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

package com.google.firebase.dataconnect.testutil

import android.annotation.SuppressLint
import com.google.common.util.concurrent.MoreExecutors
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.inject.Deferred
import com.google.firebase.util.nextAlphanumericString
import io.mockk.every
import io.mockk.mockk
import kotlin.random.Random
import org.robolectric.RuntimeEnvironment

@SuppressLint("FirebaseUseExplicitDependencies")
fun newMockFirebaseApp(
  applicationId: String = Random.nextAlphanumericString(length = 10),
  projectId: String = Random.nextAlphanumericString(length = 10)
): FirebaseApp {
  val firebaseOptions =
    FirebaseOptions.Builder().setApplicationId(applicationId).setProjectId(projectId).build()

  val firebaseApp = mockk<FirebaseApp>()
  every { firebaseApp.options } returns firebaseOptions

  abstract class DeferredAuthProvider : Deferred<InternalAuthProvider>
  val deferredAuthProvider = mockk<DeferredAuthProvider>()

  val firebaseDataConnectFactoryClass =
    Class.forName("com.google.firebase.dataconnect.core.FirebaseDataConnectFactory")

  every { firebaseApp.get(firebaseDataConnectFactoryClass) } answers
    {
      if (it.invocation.args.singleOrNull() !== firebaseDataConnectFactoryClass) {
        throw UnsupportedOperationException("arguments not supported: ${invocation.args}")
      }
      firebaseDataConnectFactoryClass.constructors
        .single()
        .newInstance(
          RuntimeEnvironment.getApplication(),
          firebaseApp,
          MoreExecutors.directExecutor(),
          MoreExecutors.directExecutor(),
          deferredAuthProvider
        )
    }

  return firebaseApp
}
