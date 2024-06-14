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

package com.google.firebase.dataconnect.oldquerymgr

import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.core.FirebaseDataConnectInternal
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.debug
import com.google.firebase.dataconnect.util.SequencedReference

internal class OldQueryManager(dataConnect: FirebaseDataConnectInternal) {
  private val logger =
    Logger("OldQueryManager").apply { debug { "Created by ${dataConnect.logger.nameWithId}" } }

  private val liveQueries = LiveQueries(dataConnect, parentLogger = logger)

  suspend fun <Data, Variables> execute(
    query: QueryRef<Data, Variables>
  ): SequencedReference<Result<Data>> =
    liveQueries.withLiveQuery(query) { it.execute(query.dataDeserializer) }

  suspend fun <Data, Variables> subscribe(
    query: QueryRef<Data, Variables>,
    executeQuery: Boolean,
    callback: suspend (SequencedReference<Result<Data>>) -> Unit,
  ): Nothing =
    liveQueries.withLiveQuery(query) { liveQuery ->
      liveQuery.subscribe(query.dataDeserializer, executeQuery = executeQuery, callback = callback)
    }
}
