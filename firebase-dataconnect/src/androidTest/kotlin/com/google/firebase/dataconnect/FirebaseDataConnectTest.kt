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

package com.google.firebase.dataconnect

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.app
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import com.google.firebase.dataconnect.testutil.TestFirebaseAppFactory
import com.google.firebase.dataconnect.testutil.containsWithNonAdjacentText
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirebaseDataConnectTest {

  @get:Rule val firebaseAppFactory = TestFirebaseAppFactory()
  @get:Rule val dataConnectFactory = TestDataConnectFactory()
  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()

  @Test
  fun getInstance_without_specifying_an_app_should_use_the_default_app() {
    val instance1 = FirebaseDataConnect.getInstance(Firebase.app, "TestLocation1", "TestService1")
    val instance2 = FirebaseDataConnect.getInstance(Firebase.app, "TestLocation2", "TestService2")
    // Validate the assumption that different location and service yield distinct instances.
    assertThat(instance1).isNotSameInstanceAs(instance2)

    val instance1DefaultApp = FirebaseDataConnect.getInstance("TestLocation1", "TestService1")
    val instance2DefaultApp = FirebaseDataConnect.getInstance("TestLocation2", "TestService2")

    assertThat(instance1DefaultApp).isSameInstanceAs(instance1)
    assertThat(instance2DefaultApp).isSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_with_default_app_should_return_non_null() {
    val instance = FirebaseDataConnect.getInstance(Firebase.app, "TestLocation", "TestService")
    assertThat(instance).isNotNull()
  }

  @Test
  fun getInstance_with_default_app_should_return_the_same_instance_every_time() {
    val instance1 = FirebaseDataConnect.getInstance(Firebase.app, "TestLocation", "TestService")
    val instance2 = FirebaseDataConnect.getInstance(Firebase.app, "TestLocation", "TestService")
    assertThat(instance1).isSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_new_instance_after_terminate() {
    val instance1 = FirebaseDataConnect.getInstance(Firebase.app, "TestLocation", "TestService")
    instance1.close()
    val instance2 = FirebaseDataConnect.getInstance(Firebase.app, "TestLocation", "TestService")
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_distinct_instances_for_distinct_apps() {
    val nonDefaultApp1 = firebaseAppFactory.newInstance()
    val nonDefaultApp2 = firebaseAppFactory.newInstance()
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp1, "TestLocation", "TestService")
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp2, "TestLocation", "TestService")
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_distinct_instances_for_distinct_locations() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation1", "TestService")
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation2", "TestService")
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_distinct_instances_for_distinct_services() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation", "TestService1")
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation", "TestService2")
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_a_new_instance_after_the_instance_is_terminated() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1A = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation1", "TestService1")
    val instance2A = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation2", "TestService2")
    assertThat(instance1A).isNotSameInstanceAs(instance2A)

    instance1A.close()
    val instance1B = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation1", "TestService1")
    assertThat(instance1A).isNotSameInstanceAs(instance1B)
    assertThat(instance1A).isNotSameInstanceAs(instance2A)

    instance2A.close()
    val instance2B = FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation2", "TestService2")
    assertThat(instance2A).isNotSameInstanceAs(instance2B)
    assertThat(instance2A).isNotSameInstanceAs(instance1A)
    assertThat(instance2A).isNotSameInstanceAs(instance1B)
  }

  @Test
  fun getInstance_should_return_the_cached_instance_if_settings_compare_equal() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        "TestLocation",
        "TestService",
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName")
      )
    val instance2 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        "TestLocation",
        "TestService",
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName")
      )
    assertThat(instance1).isSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_the_cached_instance_if_settings_are_null() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        "TestLocation",
        "TestService",
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName")
      )
    val instance2 =
      FirebaseDataConnect.getInstance(nonDefaultApp, "TestLocation", "TestService", null)
    assertThat(instance1).isSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_throw_if_settings_compare_unequal_to_settings_of_cached_instance() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        "TestLocation1",
        "TestService1",
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName1")
      )

    assertThrows(IllegalArgumentException::class.java) {
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        "TestLocation1",
        "TestService1",
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName2")
      )
    }

    val instance2 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        "TestLocation1",
        "TestService1",
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName1")
      )
    assertThat(instance1).isSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_allow_different_settings_after_first_instance_is_closed() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        "TestLocation",
        "TestService",
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName1")
      )
    instance1.close()
    val instance2 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        "TestLocation",
        "TestService",
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName2")
      )
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_new_instance_if_settings_and_app_are_both_different() {
    val nonDefaultApp1 = firebaseAppFactory.newInstance()
    val nonDefaultApp2 = firebaseAppFactory.newInstance()
    val instance1 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp1,
        "TestLocation",
        "TestService",
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName1")
      )
    val instance2 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp2,
        "TestLocation",
        "TestService",
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName2")
      )
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_new_instance_if_settings_and_location_are_both_different() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        "TestLocation1",
        "TestService",
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName1")
      )
    val instance2 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        "TestLocation2",
        "TestService",
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName2")
      )

    assertThat(instance1).isNotSameInstanceAs(instance2)
    assertThat(instance1.settings)
      .isEqualTo(FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName1"))
    assertThat(instance2.settings)
      .isEqualTo(FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName2"))
  }

  @Test
  fun getInstance_should_return_new_instance_if_settings_and_service_are_both_different() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        "TestLocation",
        "TestService1",
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName1")
      )
    val instance2 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        "TestLocation",
        "TestService2",
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName2")
      )

    assertThat(instance1).isNotSameInstanceAs(instance2)
    assertThat(instance1.settings)
      .isEqualTo(FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName1"))
    assertThat(instance2.settings)
      .isEqualTo(FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName2"))
  }

  @Test
  fun getInstance_should_be_thread_safe() {
    val apps =
      mutableListOf<FirebaseApp>().run {
        for (i in 0..4) {
          add(firebaseAppFactory.newInstance())
        }
        toList()
      }

    val createdInstancesByThreadIdLock = ReentrantLock()
    val createdInstancesByThreadId = mutableMapOf<Int, List<FirebaseDataConnect>>()
    val numThreads = 8

    val threads =
      mutableListOf<Thread>().run {
        val readyCountDown = AtomicInteger(numThreads)
        repeat(numThreads) { i ->
          add(
            thread {
              readyCountDown.decrementAndGet()
              while (readyCountDown.get() > 0) {
                /* spin */
              }
              val instances =
                mutableListOf<FirebaseDataConnect>().run {
                  for (app in apps) {
                    add(FirebaseDataConnect.getInstance(app, "TestLocation1", "TestService1"))
                    add(FirebaseDataConnect.getInstance(app, "TestLocation2", "TestService2"))
                    add(FirebaseDataConnect.getInstance(app, "TestLocation3", "TestService3"))
                  }
                  toList()
                }
              createdInstancesByThreadIdLock.withLock { createdInstancesByThreadId[i] = instances }
            }
          )
        }
        toList()
      }

    threads.forEach { it.join() }

    // Verify that each thread reported its result.
    assertThat(createdInstancesByThreadId.size).isEqualTo(8)

    // Choose an arbitrary list of created instances from one of the threads, and use it as the
    // "expected" value for all other threads.
    val expectedInstances = createdInstancesByThreadId.values.toList()[0]
    assertThat(expectedInstances.size).isEqualTo(15)

    createdInstancesByThreadId.entries.forEach { (threadId, createdInstances) ->
      assertWithMessage("instances created by threadId=${threadId}")
        .that(createdInstances)
        .containsExactlyElementsIn(expectedInstances)
        .inOrder()
    }
  }

  @Test
  fun toString_should_return_a_string_that_contains_the_required_information() {
    val app = firebaseAppFactory.newInstance()
    val instance =
      FirebaseDataConnect.getInstance(app = app, location = "TestLocation", service = "TestService")

    val toStringResult = instance.toString()

    assertThat(toStringResult).containsWithNonAdjacentText("app=${app.name}")
    assertThat(toStringResult).containsWithNonAdjacentText("projectId=${app.options.projectId}")
    assertThat(toStringResult).containsWithNonAdjacentText("location=TestLocation")
    assertThat(toStringResult).containsWithNonAdjacentText("service=TestService")
  }
}
