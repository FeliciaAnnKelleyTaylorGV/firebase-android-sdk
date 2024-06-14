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
import com.google.firebase.dataconnect.core.FirebaseDataConnectFactory
import com.google.firebase.dataconnect.core.FirebaseDataConnectImpl
import com.google.firebase.dataconnect.core.FirebaseDataConnectImpl.DataConnectGrpcClientFactory
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.debug
import java.util.concurrent.Executor
import javax.inject.Named
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.annotations.Scope

@Scope @Target(CLASS, FUNCTION, PROPERTY_GETTER) internal annotation class DataConnectScope

@Suppress("unused")
@Component
@DataConnectScope
internal abstract class DataConnectComponent(
  @get:Provides val creator: FirebaseDataConnectFactory,
  @get:Provides val context: Context,
  @get:Provides val firebaseApp: FirebaseApp,
  @get:Provides @get:ProjectId val projectId: String,
  @get:Provides val dataConnectSettings: DataConnectSettings,
  @get:Provides val connectorConfig: ConnectorConfig,
  @get:Provides @get:Blocking val blockingCoroutineDispatcher: CoroutineDispatcher,
  @get:Provides @get:NonBlocking val nonBlockingCoroutineDispatcher: CoroutineDispatcher,
  @get:Provides val deferredAuthProvider: com.google.firebase.inject.Deferred<InternalAuthProvider>,
) {
  @Provides fun dataConnect(impl: FirebaseDataConnectImpl): FirebaseDataConnect = impl
  abstract fun dataConnect(): FirebaseDataConnect

  @Provides
  @Named("FirebaseDataConnectImpl")
  fun loggerFirebaseDataConnectImpl(): Logger =
    Logger("FirebaseDataConnectImpl").apply {
      debug {
        "Created for firebaseApp=${firebaseApp.name} projectId=$projectId " +
          "dataConnectSettings=$dataConnectSettings connectorConfig=$connectorConfig"
      }
    }

  @Provides
  @Named("DataConnectAuth")
  fun loggerDataConnectAuth(@Named("FirebaseDataConnectImpl") parentLogger: Logger): Logger =
    Logger("DataConnectAuth").apply { debug { "Created by ${parentLogger.nameWithId}" } }

  @Provides @Blocking fun blockingExecutor(): Executor = blockingCoroutineDispatcher.asExecutor()
  @Provides
  @NonBlocking
  fun nonBlockingExecutor(): Executor = nonBlockingCoroutineDispatcher.asExecutor()

  @Provides @KotlinStdlibVersion fun kotlinVersion(): String = "${KotlinVersion.CURRENT}"
  @Provides @AndroidVersion fun androidVersion(): Int = Build.VERSION.SDK_INT
  @Provides @DataConnectSdkVersion fun dataConnectSdkVersion(): String = BuildConfig.VERSION_NAME
  @Provides @GrpcVersion fun grpcVersion(): String = "" // no way to get the grpc version at runtime

  @Provides
  fun dataConnectGrpcClientFactory(
    @Named("FirebaseDataConnectImpl") parentLogger: Logger
  ): DataConnectGrpcClientFactory =
    object : DataConnectGrpcClientFactory {
      override fun newInstance(host: String, sslEnabled: Boolean) =
        DataConnectConfiguredComponent.create(
            this@DataConnectComponent,
            host,
            sslEnabled,
            parentLogger = parentLogger
          )
          .dataConnectGrpcClient
    }

  companion object
}
