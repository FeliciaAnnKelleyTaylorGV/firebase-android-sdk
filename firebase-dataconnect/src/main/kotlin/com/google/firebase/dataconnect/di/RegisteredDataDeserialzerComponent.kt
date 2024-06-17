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
import kotlinx.serialization.DeserializationStrategy

internal class RegisteredDataDeserialzerComponent<T>(
  val liveQueryComponent: LiveQueryComponent,
  val dataDeserializer: DeserializationStrategy<T>,
  private val parentLoggerId: String,
) {
  val registeredDataDeserialzer: RegisteredDataDeserialzer<T> = run {
    val dataDeserializer = dataDeserializer
    val blockingCoroutineDispatcher =
      liveQueryComponent.dataConnectConfiguredComponent.dataConnectComponent
        .blockingCoroutineDispatcher
    val logger = Logger("RegisteredDataDeserialzer")

    RegisteredDataDeserialzer(
        dataDeserializer = dataDeserializer,
        blockingCoroutineDispatcher = blockingCoroutineDispatcher,
        logger = logger,
      )
      .apply {
        logger.debug { "created by $parentLoggerId with dataDeserializer=$dataDeserializer" }
      }
  }
}
