/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.core

import com.google.firebase.FirebaseApp
import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.dataconnect.DataConnectSettings
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.util.NullableReference
import com.google.firebase.dataconnect.util.SuspendingLazy
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

internal class FirebaseDataConnectImpl(
  override val app: FirebaseApp,
  private val projectId: String,
  override val config: ConnectorConfig,
  private val dataConnectAuth: DataConnectAuth,
  private val dataConnectServerInfo: DataConnectServerInfo,
  private val grpcClient: SuspendingLazy<DataConnectGrpcClient>,
  private val creator: FirebaseDataConnectFactory,
  override val settings: DataConnectSettings,
  private val coroutineScope: CoroutineScope,
  private val logger: Logger,
) : FirebaseDataConnect {
  // Protects `closed`, `grpcClient`, `emulatorSettings`, and `queryManager`.
  private val mutex = Mutex()

  // All accesses to this variable _must_ have locked `mutex`.
  private var closed = false

  override fun useEmulator(host: String, port: Int): Unit = runBlocking {
    logger.debug { "useEmulator(host=$host, port=$port)" }
    dataConnectServerInfo.useEmulator(host, port)
  }

  override fun <Data, Variables> query(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
  ) =
    QueryRefImpl(
      dataConnect = this,
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
    )

  override fun <Data, Variables> mutation(
    operationName: String,
    variables: Variables,
    dataDeserializer: DeserializationStrategy<Data>,
    variablesSerializer: SerializationStrategy<Variables>,
  ) =
    MutationRefImpl(
      dataConnect = this,
      operationName = operationName,
      variables = variables,
      dataDeserializer = dataDeserializer,
      variablesSerializer = variablesSerializer,
    )

  private val closeJob = MutableStateFlow(NullableReference<Deferred<Unit>>(null))

  override fun close() {
    logger.debug { "close() called" }
    @Suppress("DeferredResultUnused") runBlocking { nonBlockingClose() }
  }

  override suspend fun suspendingClose() {
    logger.debug { "suspendingClose() called" }
    nonBlockingClose().await()
  }

  private suspend fun nonBlockingClose(): Deferred<Unit> {
    coroutineScope.cancel()

    // Remove the reference to this `FirebaseDataConnect` instance from the
    // `FirebaseDataConnectFactory` that created it, so that the next time that `getInstance()` is
    // called with the same arguments that a new instance of `FirebaseDataConnect` will be created.
    creator.remove(this)

    mutex.withLock { closed = true }

    // Close Auth synchronously to avoid race conditions with auth callbacks. Since close()
    // is re-entrant, this is safe even if it's already been closed.
    dataConnectAuth.close()

    // Start the job to asynchronously close the gRPC client.
    while (true) {
      val oldCloseJob = closeJob.value

      oldCloseJob.ref?.let {
        if (!it.isCancelled) {
          return it
        }
      }

      @OptIn(DelicateCoroutinesApi::class)
      val newCloseJob =
        GlobalScope.async<Unit>(start = CoroutineStart.LAZY) {
          grpcClient.initializedValueOrNull?.close()
        }

      newCloseJob.invokeOnCompletion { exception ->
        if (exception === null) {
          logger.debug { "close() completed successfully" }
        } else {
          logger.warn(exception) { "close() failed" }
        }
      }

      if (closeJob.compareAndSet(oldCloseJob, NullableReference(newCloseJob))) {
        newCloseJob.start()
        return newCloseJob
      }
    }
  }

  // The generated SDK relies on equals() and hashCode() using object identity.
  // Although you get this for free by just calling the methods of the superclass, be explicit
  // to ensure that nobody changes these implementations in the future.
  override fun equals(other: Any?): Boolean = other === this
  override fun hashCode(): Int = System.identityHashCode(this)

  override fun toString(): String =
    "FirebaseDataConnect(app=${app.name}, projectId=$projectId, config=$config, settings=$settings)"
}

internal class DataConnectServerInfo(dataConnectSettings: DataConnectSettings) : AutoCloseable {
  private val emulatorInfo =
    AtomicReference<EmulatorInfo>(
      EmulatorInfo.Initialized(
        host = dataConnectSettings.host,
        sslEnabled = dataConnectSettings.sslEnabled,
        isEmulator = false,
      )
    )

  val host: String
    get() = freeze().values.host
  val sslEnabled: Boolean
    get() = freeze().values.sslEnabled
  val isEmulator: Boolean
    get() = freeze().values.isEmulator

  fun useEmulator(host: String, port: Int) {
    val newEmulatorInfo =
      EmulatorInfo.Initialized(host = "$host:$port", sslEnabled = false, isEmulator = true)
    while (true) {
      when (val oldEmulatorInfo = emulatorInfo.get()) {
        is EmulatorInfo.Closed ->
          throw IllegalStateException("cannot configure data connect emulator after close")
        is EmulatorInfo.Frozen ->
          throw IllegalStateException(
            "cannot configure data connect emulator after the initial use of a QueryRef or MutationRef"
          )
        is EmulatorInfo.Initialized -> {
          if (emulatorInfo.compareAndSet(oldEmulatorInfo, newEmulatorInfo)) {
            return
          }
        }
      }
    }
  }

  override fun close() {
    while (true) {
      val oldEmulatorInfo: EmulatorInfo.Frozen =
        when (val oldEmulatorInfo = emulatorInfo.get()) {
          is EmulatorInfo.Closed -> return
          is EmulatorInfo.Frozen -> oldEmulatorInfo
          is EmulatorInfo.Initialized -> EmulatorInfo.Frozen(oldEmulatorInfo)
        }
      val newEmulatorInfo = EmulatorInfo.Closed(oldEmulatorInfo)
      if (emulatorInfo.compareAndSet(oldEmulatorInfo, newEmulatorInfo)) {
        return
      }
    }
  }

  private fun freeze(): EmulatorInfo.Frozen {
    while (true) {
      when (val oldEmulatorInfo = emulatorInfo.get()) {
        is EmulatorInfo.Frozen -> return oldEmulatorInfo
        is EmulatorInfo.Closed -> return oldEmulatorInfo.frozenEmulatorInfo
        is EmulatorInfo.Initialized -> {
          val newEmulatorInfo = EmulatorInfo.Frozen(oldEmulatorInfo)
          if (emulatorInfo.compareAndSet(oldEmulatorInfo, newEmulatorInfo)) {
            return newEmulatorInfo
          }
        }
      }
    }
  }

  private sealed interface EmulatorInfo {
    data class Initialized(val host: String, val sslEnabled: Boolean, val isEmulator: Boolean) :
      EmulatorInfo
    data class Frozen(val values: Initialized) : EmulatorInfo
    class Closed(val frozenEmulatorInfo: Frozen) : EmulatorInfo
  }
}
