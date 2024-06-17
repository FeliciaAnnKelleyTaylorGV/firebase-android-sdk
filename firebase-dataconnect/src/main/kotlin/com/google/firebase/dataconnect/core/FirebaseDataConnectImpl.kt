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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
    // Remove the reference to this `FirebaseDataConnect` instance from the
    // `FirebaseDataConnectFactory` that created it, so that the next time that `getInstance()` is
    // called with the same arguments that a new instance of `FirebaseDataConnect` will be created.
    creator.remove(this)

    coroutineScope.cancel()

    dataConnectServerInfo.close()

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
          dataConnectAuth.close()
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
