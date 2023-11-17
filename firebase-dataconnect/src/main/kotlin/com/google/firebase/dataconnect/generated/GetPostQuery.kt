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
package com.google.firebase.dataconnect.generated

import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.QuerySubscription
import com.google.firebase.dataconnect.query
import kotlinx.serialization.Serializable

class GetPostQuery private constructor() {

  @Serializable
  data class Variables(val id: String) {

    val builder
      get() = Builder(id = id)

    fun build(block: Builder.() -> Unit): Variables = builder.apply(block).build()

    @DslMarker annotation class VariablesDsl

    @VariablesDsl
    class Builder(var id: String) {
      fun build() = Variables(id = id)
    }
  }

  @Serializable
  data class Result(val post: Post?) {
    @Serializable
    data class Post(val content: String, val comments: List<Comment>) {
      @Serializable data class Comment(val id: String, val content: String)
    }
  }

  companion object {

    fun query(dataConnect: FirebaseDataConnect) =
      dataConnect.query<Variables, Result>(
        operationName = "getPost",
        operationSet = "crud",
        revision = "1234567890abcdef",
      )
  }
}

typealias GetPostQuerySubscription = QuerySubscription<GetPostQuery.Variables, GetPostQuery.Result>

val FirebaseDataConnect.Queries.getPost
  get() = GetPostQuery.query(dataConnect)

suspend fun QueryRef<GetPostQuery.Variables, GetPostQuery.Result>.execute(id: String) =
  execute(variablesFor(id = id))

fun QueryRef<GetPostQuery.Variables, GetPostQuery.Result>.subscribe(id: String) =
  subscribe(variablesFor(id = id))

fun QuerySubscription<GetPostQuery.Variables, GetPostQuery.Result>.update(
  block: GetPostQuery.Variables.Builder.() -> Unit
) = update(variables.build(block))

private fun variablesFor(id: String) = GetPostQuery.Variables(id = id)
