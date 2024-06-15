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
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.debug
import com.google.firebase.dataconnect.oldquerymgr.LiveQueries
import com.google.firebase.dataconnect.oldquerymgr.LiveQuery
import com.google.firebase.dataconnect.oldquerymgr.OldQueryManager
import com.google.protobuf.Struct
import javax.inject.Named
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.annotations.Scope

@Scope
@Target(CLASS, FUNCTION, PROPERTY_GETTER, PROPERTY)
internal annotation class DataConnectConfiguredScope

@Suppress("unused")
@Component
@DataConnectConfiguredScope
internal abstract class DataConnectConfiguredComponent(
  @Component val dataConnectComponent: DataConnectComponent,
  @get:Provides @get:DataConnectHost val dataConnectHost: String,
  @get:Provides @get:DataConnectSslEnabled val dataConnectSslEnabled: Boolean,
  private val parentLogger: Logger
) {
  @DataConnectConfiguredScope abstract val dataConnectGrpcClient: DataConnectGrpcClient
  @DataConnectConfiguredScope abstract val queryManager: OldQueryManager

  @Provides
  @Named("DataConnectGrpcClient")
  fun loggerDataConnectGrpcClient(): Logger =
    Logger("DataConnectGrpcClient").apply { debug { "Created by ${parentLogger.nameWithId}" } }

  @Provides
  @Named("DataConnectGrpcRPCs")
  fun loggerDataConnectGrpcRPCs(): Logger =
    Logger("DataConnectGrpcRPCs").apply { debug { "Created by ${parentLogger.nameWithId}" } }

  @Provides
  @Named("LiveQueries")
  fun loggerLiveQueries(): Logger =
    Logger("LiveQueries").apply { debug { "Created by ${parentLogger.nameWithId}" } }

  @Provides
  fun liveQueryFactory(): LiveQueries.LiveQueryFactory =
    object : LiveQueries.LiveQueryFactory {
      override fun newLiveQuery(
        key: LiveQuery.Key,
        operationName: String,
        variables: Struct,
        parentLogger: Logger,
      ): LiveQuery =
        LiveQueryComponent.create(
            this@DataConnectConfiguredComponent,
            key,
            operationName,
            variables,
            parentLogger
          )
          .liveQuery
    }

  companion object
}
