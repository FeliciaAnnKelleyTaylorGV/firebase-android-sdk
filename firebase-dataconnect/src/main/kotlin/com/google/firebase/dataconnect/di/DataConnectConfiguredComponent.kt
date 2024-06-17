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

import com.google.firebase.dataconnect.core.DataConnectGrpcClient
import com.google.firebase.dataconnect.core.DataConnectGrpcMetadata
import com.google.firebase.dataconnect.core.DataConnectGrpcRPCs
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.debug
import com.google.firebase.dataconnect.oldquerymgr.LiveQueries
import com.google.firebase.dataconnect.oldquerymgr.LiveQuery
import com.google.firebase.dataconnect.oldquerymgr.OldQueryManager
import com.google.protobuf.Struct

@Suppress("MemberVisibilityCanBePrivate")
internal class DataConnectConfiguredComponent(
  val dataConnectComponent: DataConnectComponent,
  val dataConnectHost: String,
  val dataConnectSslEnabled: Boolean,
  private val parentLoggerId: String,
) {
  val dataConnectGrpcMetadata: DataConnectGrpcMetadata = run {
    val logger = Logger("DataConnectGrpcMetadata")
    DataConnectGrpcMetadata(
        dataConnectAuth = dataConnectComponent.dataConnectAuth,
        connectorLocation = dataConnectComponent.connectorConfig.location,
        kotlinVersion = dataConnectComponent.kotlinVersion,
        androidVersion = dataConnectComponent.androidVersion,
        dataConnectSdkVersion = dataConnectComponent.dataConnectSdkVersion,
        grpcVersion = dataConnectComponent.grpcVersion,
        instanceId = logger.nameWithId,
      )
      .apply {
        logger.debug {
          "created by $parentLoggerId with " + " dataConnectAuth=${dataConnectAuth.instanceId} "
          " connectorLocation=$connectorLocation" +
            " kotlinVersion=$kotlinVersion" +
            " androidVersion=$androidVersion" +
            " dataConnectSdkVersion=$dataConnectSdkVersion" +
            " grpcVersion=$grpcVersion"
        }
      }
  }

  val dataConnectGrpcRPCs: DataConnectGrpcRPCs = run {
    val context = dataConnectComponent.context
    val host = dataConnectHost
    val sslEnabled = dataConnectSslEnabled
    val blockingCoroutineDispatcher = dataConnectComponent.blockingCoroutineDispatcher
    val dataConnectGrpcMetadata = dataConnectGrpcMetadata
    val logger = Logger("DataConnectGrpcRPCs")

    DataConnectGrpcRPCs(
        context = context,
        host = host,
        sslEnabled = sslEnabled,
        blockingCoroutineDispatcher = blockingCoroutineDispatcher,
        dataConnectGrpcMetadata = dataConnectGrpcMetadata,
        logger = logger,
      )
      .apply {
        logger.debug {
          "created by $parentLoggerId with " +
            " host=$host" +
            " sslEnabled=$sslEnabled" +
            " dataConnectGrpcMetadata=${dataConnectGrpcMetadata.instanceId}"
        }
      }
  }

  val dataConnectGrpcClient: DataConnectGrpcClient = run {
    val projectId = dataConnectComponent.projectId
    val connectorConfig = dataConnectComponent.connectorConfig
    val dataConnectGrpcRPCs = dataConnectGrpcRPCs
    val logger = Logger("DataConnectGrpcClient")

    DataConnectGrpcClient(
        projectId = projectId,
        connectorConfig = connectorConfig,
        dataConnectGrpcRPCs = dataConnectGrpcRPCs,
        logger = logger,
      )
      .apply {
        logger.debug {
          "created by $parentLoggerId with "
          " projectId=$projectId" +
            " connectorConfig=$connectorConfig" +
            " dataConnectGrpcRPCs=${dataConnectGrpcRPCs.instanceId}"
        }
      }
  }

  val liveQueries: LiveQueries = run {
    val logger = Logger("LiveQueries")
    LiveQueries(
        liveQueryFactory = LiveQueryFactoryImpl(logger.nameWithId),
        logger = logger,
      )
      .apply { logger.debug { "created by $parentLoggerId" } }
  }

  val queryManager: OldQueryManager = run {
    val liveQueries = liveQueries
    val logger = Logger("OldQueryManager")

    OldQueryManager(
        liveQueries = liveQueries,
        instanceId = logger.nameWithId,
      )
      .apply {
        logger.debug { "created by $parentLoggerId with liveQueries=${liveQueries.instanceId}" }
      }
  }

  inner class LiveQueryFactoryImpl(private val parentLoggerId: String) :
    LiveQueries.LiveQueryFactory {
    override fun newLiveQuery(
      key: LiveQuery.Key,
      operationName: String,
      variables: Struct,
      parentLogger: Logger,
    ): LiveQuery =
      LiveQueryComponent(
          dataConnectConfiguredComponent = this@DataConnectConfiguredComponent,
          key = key,
          operationName = operationName,
          variables = variables,
          parentLoggerId = parentLoggerId,
        )
        .liveQuery
  }
}
