// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.firebase.dataconnect.connectors

import com.google.firebase.dataconnect.DataConnectMutationResult
import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.mutation
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

public class CreatePost internal constructor(public val connector: PostsConnector) {

  public fun ref(variables: Variables): MutationRef<Unit, Variables> =
    connector.dataConnect.mutation(
      operationName = "createPost",
      variables = variables,
      responseDeserializer = responseDeserializer,
      variablesSerializer = variablesSerializer,
    )

  public fun ref(id: String, content: String): MutationRef<Unit, Variables> =
    ref(Variables(data = Variables.PostData(id = id, content = content)))

  @Serializable
  public data class Variables(val data: PostData) {
    @Serializable public data class PostData(val id: String, val content: String)
  }

  private companion object {
    val responseDeserializer = serializer<Unit>()
    val variablesSerializer = serializer<Variables>()
  }
}

public suspend fun PostsConnector.createPost(
  id: String,
  content: String
): DataConnectMutationResult<Unit, CreatePost.Variables> =
  createPost.ref(id = id, content = content).execute()
