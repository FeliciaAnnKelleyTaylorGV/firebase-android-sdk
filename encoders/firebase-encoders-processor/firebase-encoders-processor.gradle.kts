// Copyright 2022 Google LLC
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
  id("java-library")
}

val jvm = org.gradle.internal.jvm.Jvm.current()

dependencies {
  implementation(project(":encoders:firebase-encoders"))
  implementation("androidx.annotation:annotation:1.1.0")
  implementation("com.google.auto.service:auto-service-annotations:1.0-rc6")
  implementation("com.squareup:javapoet:1.13.0")
  compileOnly("javax.annotation:javax.annotation-api:1.3.2")
  implementation("com.google.guava:guava:28.1-jre")
  implementation("com.google.auto:auto-common:1.1.2")
  implementation("com.google.auto.value:auto-value-annotations:1.6.6")

  annotationProcessor("com.google.auto.value:auto-value:1.6.5")

  annotationProcessor("com.google.auto.service:auto-service:1.0-rc6")

  testImplementation("com.google.testing.compile:compile-testing:0.18")
  if (jvm.getToolsJar() != null) testImplementation(files(jvm.getToolsJar()))
  testImplementation("com.google.truth:truth:1.0.1")

}

// this is needed to bump guava to required version, otherwise tests fail.
configurations["testImplementation"].resolutionStrategy {
  force("com.google.guava:guava:28.1-jre")
}

tasks.test {
  testLogging.showStandardStreams = true
}

googleJavaFormat {
  exclude("src/test/resources/**")
}