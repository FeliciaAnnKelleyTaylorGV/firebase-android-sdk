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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseAppTestUtils
import com.google.firebase.FirebaseOptions
import com.google.firebase.initialize

class FirebaseAppUnitTestingRule(
  private val appNameKey: String,
  private val applicationIdKey: String,
  private val projectIdKey: String,
) : FactoryTestRule<FirebaseApp, Nothing>() {

  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  override fun createInstance(params: Nothing?) = createInstance(randomAppName(appNameKey))

  private fun createInstance(appName: String): FirebaseApp {
    val firebaseOptions = newFirebaseOptions()
    val app = Firebase.initialize(context, firebaseOptions, appName)
    FirebaseAppTestUtils.initializeAllComponents(app)
    return app
  }

  private fun initializeDefaultApp(): FirebaseApp = createInstance(FirebaseApp.DEFAULT_APP_NAME)

  override fun destroyInstance(instance: FirebaseApp) {
    instance.delete()
  }

  override fun before() {
    super.before()
    FirebaseAppTestUtils.clearInstancesForTest()
    initializeDefaultApp()
  }

  override fun after() {
    FirebaseAppTestUtils.clearInstancesForTest()
    super.after()
  }

  private fun newFirebaseOptions() =
    FirebaseOptions.Builder()
      .setApplicationId(randomApplicationId(applicationIdKey))
      .setProjectId(randomProjectId(projectIdKey))
      .build()
}
