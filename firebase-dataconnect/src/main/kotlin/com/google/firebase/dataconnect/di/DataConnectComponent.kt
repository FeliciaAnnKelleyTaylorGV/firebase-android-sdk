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

import android.content.Context
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.dataconnect.BuildConfig
import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.dataconnect.DataConnectSettings
import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.core.DataConnectAuth
import com.google.firebase.dataconnect.core.DataConnectServerInfo
import com.google.firebase.dataconnect.core.FirebaseDataConnectFactory
import com.google.firebase.dataconnect.core.FirebaseDataConnectImpl
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.debug
import com.google.firebase.dataconnect.core.warn
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher

internal class DataConnectComponent(
  val creator: FirebaseDataConnectFactory,
  val context: Context,
  val firebaseApp: FirebaseApp,
  val projectId: String,
  val dataConnectSettings: DataConnectSettings,
  val connectorConfig: ConnectorConfig,
  val blockingExecutor: Executor,
  val nonBlockingExecutor: Executor,
  val deferredAuthProvider: com.google.firebase.inject.Deferred<InternalAuthProvider>,
) {
  val parentLogger: Logger =
    Logger("FirebaseDataConnect").apply {
      "created with firebaseApp=${firebaseApp.name}" +
        " projectId=$projectId" +
        " config=$connectorConfig" +
        " settings=$dataConnectSettings"
    }
  val parentLoggerId: String = parentLogger.nameWithId

  val blockingCoroutineDispatcher: CoroutineDispatcher = blockingExecutor.asCoroutineDispatcher()
  val nonBlockingCoroutineDispatcher: CoroutineDispatcher =
    nonBlockingExecutor.asCoroutineDispatcher()

  val kotlinVersion: String = "${KotlinVersion.CURRENT}"
  val androidVersion: Int = Build.VERSION.SDK_INT
  val dataConnectSdkVersion: String = BuildConfig.VERSION_NAME
  val grpcVersion: String = "" // no way to get the grpc version at runtime

  val coroutineScope: CoroutineScope =
    CoroutineScope(
      SupervisorJob() +
        nonBlockingCoroutineDispatcher +
        CoroutineName(parentLoggerId) +
        CoroutineExceptionHandler { _, throwable ->
          parentLogger.warn(throwable) { "uncaught exception from a coroutine" }
        }
    )

  val dataConnectAuth: DataConnectAuth =
    DataConnectAuth(
      deferredAuthProvider = deferredAuthProvider,
      blockingExecutor = blockingExecutor,
      logger = Logger("DataConnectAuth").apply { debug { "Created by $parentLoggerId" } }
    )

  val dataConnectServerInfo: DataConnectServerInfo = DataConnectServerInfo(dataConnectSettings)

  val dataConnect: FirebaseDataConnect =
    FirebaseDataConnectImpl(
      app = firebaseApp,
      projectId = projectId,
      config = connectorConfig,
      dataConnectAuth = dataConnectAuth,
      dataConnectServerInfo = dataConnectServerInfo,
      creator = creator,
      settings = dataConnectSettings,
      coroutineScope = coroutineScope,
      logger = parentLogger,
    )
}
