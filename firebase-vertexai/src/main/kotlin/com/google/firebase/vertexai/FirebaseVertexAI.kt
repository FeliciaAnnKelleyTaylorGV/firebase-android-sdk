/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.vertexai

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider
import com.google.firebase.inject.Provider
import com.google.firebase.vertexai.type.Content
import com.google.firebase.vertexai.type.GenerationConfig
import com.google.firebase.vertexai.type.InvalidLocationException
import com.google.firebase.vertexai.type.RequestOptions
import com.google.firebase.vertexai.type.SafetySetting
import com.google.firebase.vertexai.type.Tool
import com.google.firebase.vertexai.type.ToolConfig

/** Entry point for all Firebase Vertex AI functionality. */
class FirebaseVertexAI internal constructor(
  private val firebaseApp: FirebaseApp,
  private val appCheckProvider: Provider<InteropAppCheckTokenProvider>
) {

  /**
   * Instantiates a new [GenerativeModel] given the provided parameters.
   *
   * @param modelName name of the model in the backend
   * @param generationConfig configuration parameters to use for content generation
   * @param safetySettings the safety bounds to use during alongside prompts during content
   * generation
   * @param requestOptions configuration options to utilize during backend communication
   * @param tools the list of tools to make available to the model
   * @param toolConfig the configuration that defines how the model handles the tools provided
   * @param systemInstruction contains a [Content] that directs the model to behave a certain way
   * @param location location identifier, defaults to `us-central1`; see available
   * [Vertex AI regions](https://cloud.google.com/vertex-ai/generative-ai/docs/learn/locations#available-regions)
   */
  @JvmOverloads
  fun generativeModel(
    modelName: String,
    generationConfig: GenerationConfig? = null,
    safetySettings: List<SafetySetting>? = null,
    requestOptions: RequestOptions = RequestOptions(),
    tools: List<Tool>? = null,
    toolConfig: ToolConfig? = null,
    systemInstruction: Content? = null,
    location: String = "us-central1"
  ): GenerativeModel {
    if (location.trim().isEmpty() || location.contains("/")) {
      throw InvalidLocationException(location)
    }
    return GenerativeModel(
      "projects/${firebaseApp.options.projectId}/locations/${location}/publishers/google/models/${modelName}",
      firebaseApp.options.apiKey,
      generationConfig,
      safetySettings,
      tools,
      toolConfig,
      systemInstruction,
      requestOptions,
      appCheckProvider.get()
    )
  }

  companion object {
    /** The [FirebaseVertexAI] instance for the default [FirebaseApp] */
    @JvmStatic
    val instance: FirebaseVertexAI
      get() = Firebase.app[FirebaseVertexAI::class.java]

    /** Returns the [FirebaseVertexAI] instance for the provided [FirebaseApp]*/
    @JvmStatic
    fun getInstance(app: FirebaseApp): FirebaseVertexAI = app[FirebaseVertexAI::class.java]
  }
}

/** Returns the [FirebaseVertexAI] instance of the default [FirebaseApp]. */
val Firebase.vertexAI: FirebaseVertexAI
  get() = FirebaseVertexAI.instance

/** Returns the [FirebaseVertexAI] instance of a given [FirebaseApp]. */
fun Firebase.vertexAI(app: FirebaseApp): FirebaseVertexAI = FirebaseVertexAI.getInstance(app)
