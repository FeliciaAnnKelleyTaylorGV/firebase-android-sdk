import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Copyright 2024 Google LLC
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

plugins {
  id("firebase-library")
  id("kotlin-android")
}

android {
  val targetSdkVersion: Int by rootProject

  namespace = "com.google.firebase.dataconnect.testutil"
  compileSdk = 33
  defaultConfig {
    minSdk = 21
    targetSdk = targetSdkVersion
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions { jvmTarget = "1.8" }
}

dependencies {
  api(project(":firebase-dataconnect"))
  api(libs.kotlinx.coroutines.core)
  api(libs.kotlinx.serialization.core)

  implementation(libs.mockito.core)
  implementation(libs.robolectric)
  implementation(libs.truth)
  implementation(project(":firebase-components"))

  implementation("com.google.firebase:firebase-auth:20.0.0") {
    exclude(group = "com.google.firebase", module = "firebase-common")
    exclude(group = "com.google.firebase", module = "firebase-components")
    exclude(group = "com.google.android.recaptcha", module = "recaptcha")
  }
}

tasks.withType<KotlinCompile>().all {
  kotlinOptions {
    freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
  }
}
