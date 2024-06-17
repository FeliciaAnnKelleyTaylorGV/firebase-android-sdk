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

import com.google.firebase.dataconnect.DataConnectSettings
import java.util.concurrent.atomic.AtomicReference

internal class DataConnectServerInfo(dataConnectSettings: DataConnectSettings) : AutoCloseable {
  private val serverValues =
    AtomicReference<ServerValues>(
      ServerValues.Initialized(
        host = dataConnectSettings.host,
        sslEnabled = dataConnectSettings.sslEnabled,
        isEmulator = false,
      )
    )

  val host: String
    get() = freeze().values.host
  val sslEnabled: Boolean
    get() = freeze().values.sslEnabled
  val isEmulator: Boolean
    get() = freeze().values.isEmulator

  fun useEmulator(host: String, port: Int) {
    val newServerValues =
      ServerValues.Initialized(host = "$host:$port", sslEnabled = false, isEmulator = true)
    while (true) {
      when (val oldServerValues = serverValues.get()) {
        is ServerValues.Closed ->
          throw IllegalStateException("cannot configure data connect emulator after close")
        is ServerValues.Frozen ->
          throw IllegalStateException(
            "cannot configure data connect emulator after the initial use of a QueryRef or MutationRef"
          )
        is ServerValues.Initialized -> {
          if (serverValues.compareAndSet(oldServerValues, newServerValues)) {
            return
          }
        }
      }
    }
  }

  override fun close() {
    while (true) {
      val oldServerValues: ServerValues.Frozen =
        when (val oldServerValues = serverValues.get()) {
          is ServerValues.Closed -> return
          is ServerValues.Frozen -> oldServerValues
          is ServerValues.Initialized -> ServerValues.Frozen(oldServerValues)
        }
      val newServerValues = ServerValues.Closed(oldServerValues)
      if (serverValues.compareAndSet(oldServerValues, newServerValues)) {
        return
      }
    }
  }

  private fun freeze(): ServerValues.Frozen {
    while (true) {
      when (val oldServerValues = serverValues.get()) {
        is ServerValues.Frozen -> return oldServerValues
        is ServerValues.Closed -> return oldServerValues.frozenServerValues
        is ServerValues.Initialized -> {
          val newServerValues = ServerValues.Frozen(oldServerValues)
          if (serverValues.compareAndSet(oldServerValues, newServerValues)) {
            return newServerValues
          }
        }
      }
    }
  }

  sealed interface ServerValues {
    data class Initialized(val host: String, val sslEnabled: Boolean, val isEmulator: Boolean) :
      ServerValues
    data class Frozen(val values: Initialized) : ServerValues
    class Closed(val frozenServerValues: Frozen) : ServerValues
  }
}
