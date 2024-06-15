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
import com.google.firebase.dataconnect.oldquerymgr.RegisteredDataDeserialzer
import javax.inject.Named
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlinx.serialization.DeserializationStrategy
import me.tatarka.inject.annotations.Component
import me.tatarka.inject.annotations.Provides
import me.tatarka.inject.annotations.Scope

@Scope
@Target(CLASS, FUNCTION, PROPERTY_GETTER, PROPERTY)
internal annotation class RegisteredDataDeserialzerScope

@Suppress("unused")
@Component
@RegisteredDataDeserialzerScope
internal abstract class RegisteredDataDeserialzerComponent(
  @Component val liveQueryComponent: LiveQueryComponent,
  @get:Provides val dataDeserializer: DeserializationStrategy<*>,
  private val parentLogger: Logger
) {
  @RegisteredDataDeserialzerScope
  abstract val registeredDataDeserialzer: RegisteredDataDeserialzer<*>

  @Provides
  @Named("RegisteredDataDeserialzer")
  fun loggerRegisteredDataDeserialzer(): Logger =
    Logger("RegisteredDataDeserialzer").apply {
      debug { "Created by ${parentLogger.nameWithId}" }
      debug { "dataDeserializer=$dataDeserializer" }
    }

  companion object
}
