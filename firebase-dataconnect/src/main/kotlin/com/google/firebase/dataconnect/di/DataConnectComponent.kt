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
import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.dataconnect.core.Logger
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlinx.coroutines.CoroutineDispatcher
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.annotations.Scope

@Scope @Target(CLASS, FUNCTION, PROPERTY_GETTER) internal annotation class DataConnectScope

@Suppress("unused")
@Component
@DataConnectScope
internal abstract class DataConnectComponent(
  @get:Provides val context: Context,
  @get:Provides @get:ProjectId val projectId: String,
  @get:Provides val connectorConfig: ConnectorConfig,
  @get:Provides @get:Blocking val blockingCoroutineDispatcher: CoroutineDispatcher,
  @get:Provides @get:NonBlocking val nonBlockingCoroutineDispatcher: CoroutineDispatcher,
  val logger: Logger,
) {
  companion object
}
