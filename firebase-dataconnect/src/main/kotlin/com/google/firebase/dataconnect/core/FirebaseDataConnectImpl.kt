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
import com.google.firebase.dataconnect.*
import com.google.firebase.dataconnect.di.Blocking
import com.google.firebase.dataconnect.di.DataConnectScope
import com.google.firebase.dataconnect.di.NonBlocking
import com.google.firebase.dataconnect.di.ProjectId
import com.google.firebase.dataconnect.oldquerymgr.OldQueryManager
import com.google.firebase.dataconnect.util.NullableReference
import com.google.firebase.dataconnect.util.SuspendingLazy
import java.util.concurrent.Executor
import javax.inject.Named
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import me.tatarka.inject.annotations.Inject

internal interface FirebaseDataConnectInternal : FirebaseDataConnect {
  val logger: Logger

  val coroutineScope: CoroutineScope
  val blockingExecutor: Executor
  val blockingDispatcher: CoroutineDispatcher
  val nonBlockingExecutor: Executor
  val nonBlockingDispatcher: CoroutineDispatcher

  val lazyGrpcClient: SuspendingLazy<DataConnectGrpcClient>
  val lazyQueryManager: SuspendingLazy<OldQueryManager>
}

@Inject
@DataConnectScope
internal class FirebaseDataConnectImpl(
  override val app: FirebaseApp,
  @ProjectId private val projectId: String,
  override val config: ConnectorConfig,
  private val dataConnectAuth: DataConnectAuth,
  @Blocking override val blockingExecutor: Executor,
  @NonBlocking override val nonBlockingExecutor: Executor,
  private val dataConnectGrpcClientFactory: DataConnectGrpcClientFactory,
  private val creator: FirebaseDataConnectFactory,
  override val settings: DataConnectSettings,
  override val coroutineScope: CoroutineScope,
  @Named("FirebaseDataConnectImpl") override val logger: Logger,
) : FirebaseDataConnectInternal {
  override val blockingDispatcher = blockingExecutor.asCoroutineDispatcher()
  override val nonBlockingDispatcher = nonBlockingExecutor.asCoroutineDispatcher()

  // Protects `closed`, `grpcClient`, `emulatorSettings`, and `queryManager`.
  private val mutex = Mutex()

  // All accesses to this variable _must_ have locked `mutex`.
  private var emulatorSettings: EmulatedServiceSettings? = null

  // All accesses to this variable _must_ have locked `mutex`.
  private var closed = false

  override val lazyGrpcClient =
    SuspendingLazy(mutex) {
      if (closed) throw IllegalStateException("FirebaseDataConnect instance has been closed")

      data class DataConnectServerInfo(
        val host: String,
        val sslEnabled: Boolean,
        val isEmulator: Boolean
      )
      val dataConnectServerInfoFromSettings =
        DataConnectServerInfo(
          host = settings.host,
          sslEnabled = settings.sslEnabled,
          isEmulator = false
        )
      val dataConnectServerInfoFromEmulatorSettings =
        emulatorSettings?.run {
          DataConnectServerInfo(host = "$host:$port", sslEnabled = false, isEmulator = true)
        }

      val dataConnectServerInfo =
        if (dataConnectServerInfoFromEmulatorSettings == null) {
          dataConnectServerInfoFromSettings
        } else {
          if (!settings.isDefaultHost()) {
            logger.warn(
              "Host has been set in DataConnectSettings and useEmulator, " +
                "emulator host will be used."
            )
          }
          dataConnectServerInfoFromEmulatorSettings
        }

      logger.debug { "Connecting to Data Connect server: $dataConnectServerInfo" }
      dataConnectServerInfo.run {
        dataConnectGrpcClientFactory.newInstance(host = host, sslEnabled = sslEnabled)
      }
    }

  override val lazyQueryManager =
    SuspendingLazy(mutex) {
      if (closed) throw IllegalStateException("FirebaseDataConnect instance has been closed")
      OldQueryManager(this)
    }

  override fun useEmulator(host: String, port: Int): Unit = runBlocking {
    logger.debug { "useEmulator(host=$host, port=$port)" }
    mutex.withLock {
      if (lazyGrpcClient.initializedValueOrNull != null) {
        throw IllegalStateException(
          "Cannot call useEmulator() after instance has already been initialized."
        )
      }
      emulatorSettings = EmulatedServiceSettings(host = host, port = port)
    }
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
          lazyGrpcClient.initializedValueOrNull?.close()
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

  private data class EmulatedServiceSettings(val host: String, val port: Int)

  interface DataConnectGrpcClientFactory {
    fun newInstance(host: String, sslEnabled: Boolean): DataConnectGrpcClient
  }
}
