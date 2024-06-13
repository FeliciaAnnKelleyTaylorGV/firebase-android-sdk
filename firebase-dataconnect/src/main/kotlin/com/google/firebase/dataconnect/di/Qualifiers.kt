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

import java.util.concurrent.Executor
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher

/** The annotated [String] is the Firebase Project ID. */
@Qualifier internal annotation class ProjectId

/** The annotated [String] is the "host" (hostname and port) of the Data Connect server to use. */
@Qualifier internal annotation class DataConnectHost

/** The annotated [String] is the Kotlin standard library version in use. */
@Qualifier internal annotation class KotlinStdlibVersion

/** The annotated [Int] is the Android SDK version in use. */
@Qualifier internal annotation class AndroidVersion

/** The annotated [String] is the version of the Data Connect SDK. */
@Qualifier internal annotation class DataConnectSdkVersion

/** The annotated [String] is the version of the GRPC library in use. */
@Qualifier internal annotation class GrpcVersion

/**
 * The annotated [Boolean] is `true` if network traffic between the client and the Data Connect
 * server, or `false` if it must be sent unencrypted.
 */
@Qualifier internal annotation class DataConnectSslEnabled

/**
 * The annotated [Executor] or [CoroutineDispatcher] must be suitable for performing _blocking_
 * operations, such as long-running operations or I/O.
 * @see NonBlocking
 */
@Qualifier internal annotation class Blocking

/**
 * The annotated [Executor] or [CoroutineDispatcher] does _not_ need to be suitable for performing
 * blocking operations; rather, it should be expected to only perform quick tasks, quick enough for
 * running in a blocking manner on the main thread.
 * @see Blocking
 */
@Qualifier internal annotation class NonBlocking
