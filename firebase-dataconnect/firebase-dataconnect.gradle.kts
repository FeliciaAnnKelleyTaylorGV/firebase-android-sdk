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

import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("firebase-library")
  id("kotlin-android")
  id("com.google.protobuf")
  id("org.jetbrains.dokka") version "1.9.10"
  id("org.jetbrains.kotlin.plugin.serialization") version "1.8.0"
}

firebaseLibrary {
  publishSources = true
  publishJavadoc = true
}

android {
  val targetSdkVersion: Int by rootProject

  namespace = "com.google.firebase.dataconnect"
  compileSdk = 33
  defaultConfig {
    minSdk = 21
    targetSdk = targetSdkVersion
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions { jvmTarget = "1.8" }

  testOptions.unitTests.isReturnDefaultValues = true
}

protobuf {
  protoc {
    artifact = "${libs.protoc.get()}"
  }
  plugins {
    create("java") {
      artifact = "${libs.grpc.protoc.gen.java.get()}"
    }
    create("grpc") {
      artifact = "${libs.grpc.protoc.gen.java.get()}"
    }
    create("grpckt") {
      artifact = "${libs.grpc.protoc.gen.kotlin.get()}:jdk8@jar"
    }
  }
  generateProtoTasks {
    all().forEach { task ->
      task.builtins {
        create("kotlin") {
          option("lite")
        }
      }
      task.plugins {
        create("java") {
          option("lite")
        }
        create("grpc") {
          option("lite")
        }
        create("grpckt") {
          option("lite")
        }
      }
    }
  }
}

dependencies {
  // TODO: Change `project` dependencies to normal, versioned dependencies once firebase-common is
  // released with RandomUtil changes from https://github.com/firebase/firebase-android-sdk/pull/5818.
  // This should be the M147 release that is scheduled for May 02, 2024.
  api(project(":firebase-common"))
  api(project(":firebase-common:ktx"))

  api(libs.kotlinx.coroutines.core)
  api(libs.kotlinx.serialization.core)

  implementation(project(":firebase-annotations"))
  implementation(project(":firebase-components"))
  implementation(project(":protolite-well-known-types"))
  implementation("com.google.firebase:firebase-auth-interop:20.0.0") {
    exclude(group = "com.google.firebase", module = "firebase-common")
    exclude(group = "com.google.firebase", module = "firebase-components")
    exclude(group = "com.google.android.recaptcha", module = "recaptcha")
  }

  compileOnly(libs.javax.annotation.jsr250)
  implementation(libs.grpc.android)
  implementation(libs.grpc.okhttp)
  implementation(libs.grpc.protobuf.lite)
  implementation(libs.grpc.kotlin.stub)
  implementation(libs.grpc.stub)
  implementation(libs.protobuf.java.lite)
  implementation(libs.protobuf.kotlin.lite)

  testCompileOnly(libs.protobuf.java)
  testImplementation(project(":firebase-dataconnect:testutil"))
  testImplementation(libs.mockito.core)
  testImplementation(libs.robolectric)
  testImplementation(libs.truth)
  testImplementation(libs.truth.liteproto.extension)
  testImplementation(libs.kotlin.coroutines.test)

  androidTestImplementation(project(":firebase-dataconnect:androidTestutil"))
  androidTestImplementation(project(":firebase-dataconnect:connectors"))
  androidTestImplementation(project(":firebase-dataconnect:testutil"))
  androidTestImplementation("com.google.firebase:firebase-auth:20.0.0") {
    exclude(group = "com.google.firebase", module = "firebase-common")
    exclude(group = "com.google.firebase", module = "firebase-components")
    exclude(group = "com.google.android.recaptcha", module = "recaptcha")
  }
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.kotlin.coroutines.test)
  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.truth.liteproto.extension)
  androidTestImplementation(libs.turbine)
}

tasks.withType<KotlinCompile>().all {
  kotlinOptions {
    freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
  }
}

extra["packageName"] = "com.google.firebase.dataconnect"
apply(from = "../gradle/googleServices.gradle")

tasks.withType<DokkaTask>().configureEach {
  moduleName.set("firebase-dataconnect")
}

// Enable Kotlin "Explicit API Mode". This causes the Kotlin compiler to fail if any
// classes, methods, or properties have implicit `public` visibility. This check helps
// avoid  accidentally leaking elements into the public API, requiring that any public
// element be explicitly declared as `public`.
// https://github.com/Kotlin/KEEP/blob/master/proposals/explicit-api-mode.md
// https://chao2zhang.medium.com/explicit-api-mode-for-kotlin-on-android-b8264fdd76d1
tasks.withType<KotlinCompile>().all {
  if (!name.contains("test", ignoreCase = true)) {
    if (!kotlinOptions.freeCompilerArgs.contains("-Xexplicit-api=strict")) {
      kotlinOptions.freeCompilerArgs += "-Xexplicit-api=strict"
    }
  }
}
