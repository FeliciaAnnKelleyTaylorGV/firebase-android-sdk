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
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.util.concurrent.MoreExecutors
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseAppLifecycleListener
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.inject.Deferred
import io.mockk.Call
import io.mockk.every
import io.mockk.mockk
import java.util.WeakHashMap
import java.util.concurrent.Executor

/**
 * A JUnit test rule that creates mock instances of [FirebaseApp] for use during unit testing, and
 * closes them upon test completion.
 */
class MockFirebaseAppFactory(
  private val applicationIdKey: String? = null,
  private val projectIdKey: String? = null
) : FactoryTestRule<FirebaseApp, Nothing>() {

  override fun createInstance(params: Nothing?) =
    newMockFirebaseApp(
      applicationId = randomApplicationId(applicationIdKey ?: "m6dgx5tvfb"),
      projectId = randomProjectId(projectIdKey ?: "m2c63g5gs3")
    )

  override fun destroyInstance(instance: FirebaseApp) {
    instance.delete()
  }

  data class Params(
    val applicationId: String? = null,
    val applicationIdKey: String? = null,
    val projectId: String? = null,
    val projectIdKey: String? = null
  )

  private companion object {

    fun newMockFirebaseApp(applicationId: String, projectId: String): FirebaseApp =
      newMockFirebaseApp(
        context = ApplicationProvider.getApplicationContext(),
        firebaseOptions =
          FirebaseOptions.Builder().setApplicationId(applicationId).setProjectId(projectId).build(),
        executor = MoreExecutors.directExecutor()
      )

    @SuppressLint("FirebaseUseExplicitDependencies")
    fun newMockFirebaseApp(
      context: Context,
      firebaseOptions: FirebaseOptions,
      executor: Executor
    ): FirebaseApp {
      val firebaseDataConnectFactoryClass =
        Class.forName("com.google.firebase.dataconnect.core.FirebaseDataConnectFactory")

      return mockk<FirebaseApp> {
        every { options } returns firebaseOptions

        every { get(firebaseDataConnectFactoryClass) } answers
          {
            firebaseDataConnectFactoryClass.constructors
              .single()
              .newInstance(
                context,
                this@mockk,
                executor,
                executor,
                mockk<Deferred<InternalAuthProvider>>(relaxed = true)
              )
          }

        every { addLifecycleEventListener(any<FirebaseAppLifecycleListener>()) } answers
          { call ->
            call.invocation.self
            val listener = call.invocation.args[0] as FirebaseAppLifecycleListener
            val lifecycleEventListeners = call.lifecycleEventListeners
            synchronized(lifecycleEventListeners) { lifecycleEventListeners.add(listener) }
          }

        every { removeLifecycleEventListener(any<FirebaseAppLifecycleListener>()) } answers
          { call ->
            val listener = call.invocation.args[0] as FirebaseAppLifecycleListener
            val lifecycleEventListeners = call.lifecycleEventListeners
            synchronized(lifecycleEventListeners) {
              val index = lifecycleEventListeners.indexOfLast { it === listener }
              if (index >= 0) {
                lifecycleEventListeners.removeAt(index)
              }
            }
          }

        every { delete() } answers
          {
            val lifecycleEventListeners =
              call.lifecycleEventListeners.let { synchronized(it) { it.toList() } }
            lifecycleEventListeners.forEach { it.onDeleted("AppName_s7e89h8tx8", firebaseOptions) }
          }
      }
    }

    val lifecycleEventListenersByApp = WeakHashMap<Any, MutableList<FirebaseAppLifecycleListener>>()

    val Call.lifecycleEventListeners: MutableList<FirebaseAppLifecycleListener>
      get() =
        synchronized(lifecycleEventListenersByApp) {
          lifecycleEventListenersByApp.getOrPut(invocation.self) { mutableListOf() }
        }
  }
}
