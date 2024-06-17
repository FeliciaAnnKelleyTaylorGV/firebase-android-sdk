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

package com.google.firebase.dataconnect.di

import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.debug
import com.google.firebase.dataconnect.oldquerymgr.LiveQuery
import com.google.firebase.dataconnect.oldquerymgr.RegisteredDataDeserialzer
import com.google.protobuf.Struct
import kotlinx.serialization.DeserializationStrategy

internal class LiveQueryComponent(
  val dataConnectConfiguredComponent: DataConnectConfiguredComponent,
  val key: LiveQuery.Key,
  val operationName: String,
  val variables: Struct,
  private val parentLoggerId: String
) {
  val liveQuery: LiveQuery = run {
    val key = key
    val operationName = operationName
    val variables = variables
    val parentCoroutineScope = dataConnectConfiguredComponent.dataConnectComponent.coroutineScope
    val nonBlockingCoroutineDispatcher =
      dataConnectConfiguredComponent.dataConnectComponent.nonBlockingCoroutineDispatcher
    val grpcClient = dataConnectConfiguredComponent.dataConnectGrpcClient
    val logger = Logger("LiveQuery")
    val registeredDataDeserialzerFactory =
      RegisteredDataDeserialzerFactoryImpl(parentLoggerId = logger.nameWithId)

    LiveQuery(
        key = key,
        operationName = operationName,
        variables = variables,
        parentCoroutineScope = parentCoroutineScope,
        nonBlockingCoroutineDispatcher = nonBlockingCoroutineDispatcher,
        grpcClient = grpcClient,
        registeredDataDeserialzerFactory = registeredDataDeserialzerFactory,
        logger = logger,
      )
      .apply {
        logger.debug {
          "created by $parentLoggerId with " +
            " operationName=$operationName" +
            " variables=$variables" +
            " grpcClient=${grpcClient.instanceId}"
        }
      }
  }

  private inner class RegisteredDataDeserialzerFactoryImpl(val parentLoggerId: String) :
    LiveQuery.RegisteredDataDeserialzerFactory {
    override fun <T> newInstance(
      dataDeserializer: DeserializationStrategy<T>
    ): RegisteredDataDeserialzer<T> =
      RegisteredDataDeserialzerComponent(
          liveQueryComponent = this@LiveQueryComponent,
          dataDeserializer = dataDeserializer,
          parentLoggerId = parentLoggerId,
        )
        .registeredDataDeserialzer
  }
}
