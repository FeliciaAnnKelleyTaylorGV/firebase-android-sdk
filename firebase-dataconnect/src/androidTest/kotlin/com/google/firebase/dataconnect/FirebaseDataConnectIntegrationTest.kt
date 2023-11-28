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
import com.google.firebase.dataconnect.FirebaseDataConnect.ServiceConfig
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
class FirebaseDataConnectIntegrationTest {

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()
  @get:Rule val firebaseAppFactory = TestFirebaseAppFactory()
  @get:Rule val dataConnectFactory = TestDataConnectFactory()

  @Test
  fun getInstance_without_specifying_an_app_should_use_the_default_app() {
    val instance1 = FirebaseDataConnect.getInstance(Firebase.app, SAMPLE_SERVICE_CONFIG1)
    val instance2 = FirebaseDataConnect.getInstance(Firebase.app, SAMPLE_SERVICE_CONFIG2)

    // Validate the assumption that different location and service yield distinct instances.
    assertThat(instance1).isNotSameInstanceAs(instance2)

    val instance1DefaultApp = FirebaseDataConnect.getInstance(SAMPLE_SERVICE_CONFIG1)
    val instance2DefaultApp = FirebaseDataConnect.getInstance(SAMPLE_SERVICE_CONFIG2)

    assertThat(instance1DefaultApp).isSameInstanceAs(instance1)
    assertThat(instance2DefaultApp).isSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_with_default_app_should_return_non_null() {
    val instance = FirebaseDataConnect.getInstance(Firebase.app, SAMPLE_SERVICE_CONFIG1)
    assertThat(instance).isNotNull()
  }

  @Test
  fun getInstance_with_default_app_should_return_the_same_instance_every_time() {
    val instance1 = FirebaseDataConnect.getInstance(Firebase.app, SAMPLE_SERVICE_CONFIG1)
    val instance2 = FirebaseDataConnect.getInstance(Firebase.app, SAMPLE_SERVICE_CONFIG1)
    assertThat(instance1).isSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_new_instance_after_terminate() {
    val instance1 = FirebaseDataConnect.getInstance(Firebase.app, SAMPLE_SERVICE_CONFIG1)
    instance1.close()
    val instance2 = FirebaseDataConnect.getInstance(Firebase.app, SAMPLE_SERVICE_CONFIG1)
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_distinct_instances_for_distinct_apps() {
    val nonDefaultApp1 = firebaseAppFactory.newInstance()
    val nonDefaultApp2 = firebaseAppFactory.newInstance()
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp1, SAMPLE_SERVICE_CONFIG1)
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp2, SAMPLE_SERVICE_CONFIG1)
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_distinct_instances_for_distinct_serviceIds() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val serviceConfig1 = SAMPLE_SERVICE_CONFIG1.withServiceId("foo")
    val serviceConfig2 = serviceConfig1.withServiceId("bar")
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp, serviceConfig1)
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp, serviceConfig2)
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_distinct_instances_for_distinct_locations() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val serviceConfig1 = SAMPLE_SERVICE_CONFIG1.withLocation("foo")
    val serviceConfig2 = serviceConfig1.withLocation("bar")
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp, serviceConfig1)
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp, serviceConfig2)
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_distinct_instances_for_distinct_operationSets() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val serviceConfig1 = SAMPLE_SERVICE_CONFIG1.withOperationSet("foo")
    val serviceConfig2 = serviceConfig1.withOperationSet("bar")
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp, serviceConfig1)
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp, serviceConfig2)
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_throw_if_revision_differs_from_that_of_cached_instance() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val serviceConfig1 = SAMPLE_SERVICE_CONFIG1.withRevision("foo")
    val serviceConfig2 = serviceConfig1.withRevision("bar")
    val instance1 = FirebaseDataConnect.getInstance(nonDefaultApp, serviceConfig1)

    assertThrows(IllegalArgumentException::class.java) {
      FirebaseDataConnect.getInstance(nonDefaultApp, serviceConfig2)
    }

    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp, serviceConfig1)
    assertThat(instance1).isSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_a_new_instance_after_the_instance_is_terminated() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1A = FirebaseDataConnect.getInstance(nonDefaultApp, SAMPLE_SERVICE_CONFIG1)
    val instance2A = FirebaseDataConnect.getInstance(nonDefaultApp, SAMPLE_SERVICE_CONFIG2)
    assertThat(instance1A).isNotSameInstanceAs(instance2A)

    instance1A.close()
    val instance1B = FirebaseDataConnect.getInstance(nonDefaultApp, SAMPLE_SERVICE_CONFIG1)
    assertThat(instance1A).isNotSameInstanceAs(instance1B)
    assertThat(instance1A).isNotSameInstanceAs(instance2A)

    instance2A.close()
    val instance2B = FirebaseDataConnect.getInstance(nonDefaultApp, SAMPLE_SERVICE_CONFIG2)
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
        SAMPLE_SERVICE_CONFIG1,
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName")
      )
    val instance2 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_SERVICE_CONFIG1,
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
        SAMPLE_SERVICE_CONFIG1,
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName")
      )
    val instance2 = FirebaseDataConnect.getInstance(nonDefaultApp, SAMPLE_SERVICE_CONFIG1, null)
    assertThat(instance1).isSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_throw_if_settings_compare_unequal_to_settings_of_cached_instance() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_SERVICE_CONFIG1,
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName1")
      )

    assertThrows(IllegalArgumentException::class.java) {
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_SERVICE_CONFIG1,
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName2")
      )
    }

    val instance2 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_SERVICE_CONFIG1,
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
        SAMPLE_SERVICE_CONFIG1,
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName1")
      )
    instance1.close()
    val instance2 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_SERVICE_CONFIG1,
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName2")
      )
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_allow_different_revision_after_first_instance_is_closed() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 =
      FirebaseDataConnect.getInstance(nonDefaultApp, SAMPLE_SERVICE_CONFIG1.withRevision("foo"))
    instance1.close()
    val instance2 =
      FirebaseDataConnect.getInstance(nonDefaultApp, SAMPLE_SERVICE_CONFIG1.withRevision("bar"))
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_new_instance_if_settings_and_app_are_both_different() {
    val nonDefaultApp1 = firebaseAppFactory.newInstance()
    val nonDefaultApp2 = firebaseAppFactory.newInstance()
    val instance1 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp1,
        SAMPLE_SERVICE_CONFIG1,
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName1")
      )
    val instance2 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp2,
        SAMPLE_SERVICE_CONFIG1,
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName2")
      )
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_new_instance_if_revision_and_app_are_both_different() {
    val nonDefaultApp1 = firebaseAppFactory.newInstance()
    val nonDefaultApp2 = firebaseAppFactory.newInstance()
    val instance1 =
      FirebaseDataConnect.getInstance(nonDefaultApp1, SAMPLE_SERVICE_CONFIG1.withRevision("foo"))
    val instance2 =
      FirebaseDataConnect.getInstance(nonDefaultApp2, SAMPLE_SERVICE_CONFIG1.withRevision("bar"))
    assertThat(instance1).isNotSameInstanceAs(instance2)
  }

  @Test
  fun getInstance_should_return_new_instance_if_settings_and_serviceId_are_both_different() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_SERVICE_CONFIG1.withServiceId("foo"),
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName1")
      )
    val instance2 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_SERVICE_CONFIG1.withServiceId("bar"),
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName2")
      )

    assertThat(instance1).isNotSameInstanceAs(instance2)
    assertThat(instance1.settings)
      .isEqualTo(FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName1"))
    assertThat(instance2.settings)
      .isEqualTo(FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName2"))
  }

  @Test
  fun getInstance_should_return_new_instance_if_revision_and_serviceId_are_both_different() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_SERVICE_CONFIG1.withServiceId("foo").withRevision("boo")
      )
    val instance2 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_SERVICE_CONFIG1.withServiceId("bar").withRevision("zoo")
      )

    assertThat(instance1).isNotSameInstanceAs(instance2)
    assertThat(instance1.serviceConfig.revision).isEqualTo("boo")
    assertThat(instance2.serviceConfig.revision).isEqualTo("zoo")
  }

  @Test
  fun getInstance_should_return_new_instance_if_settings_and_location_are_both_different() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_SERVICE_CONFIG1.withLocation("foo"),
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName1")
      )
    val instance2 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_SERVICE_CONFIG1.withLocation("bar"),
        FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName2")
      )

    assertThat(instance1).isNotSameInstanceAs(instance2)
    assertThat(instance1.settings)
      .isEqualTo(FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName1"))
    assertThat(instance2.settings)
      .isEqualTo(FirebaseDataConnectSettings.defaults.copy(hostName = "TestHostName2"))
  }

  @Test
  fun getInstance_should_return_new_instance_if_revision_and_location_are_both_different() {
    val nonDefaultApp = firebaseAppFactory.newInstance()
    val instance1 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_SERVICE_CONFIG1.withLocation("foo").withRevision("boo")
      )
    val instance2 =
      FirebaseDataConnect.getInstance(
        nonDefaultApp,
        SAMPLE_SERVICE_CONFIG1.withLocation("bar").withRevision("zoo")
      )

    assertThat(instance1).isNotSameInstanceAs(instance2)
    assertThat(instance1.serviceConfig.revision).isEqualTo("boo")
    assertThat(instance2.serviceConfig.revision).isEqualTo("zoo")
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

    val threads = buildList {
      val readyCountDown = AtomicInteger(numThreads)
      repeat(numThreads) { i ->
        add(
          thread {
            readyCountDown.decrementAndGet()
            while (readyCountDown.get() > 0) {
              /* spin */
            }
            val instances = buildList {
              for (app in apps) {
                add(FirebaseDataConnect.getInstance(app, SAMPLE_SERVICE_CONFIG1))
                add(FirebaseDataConnect.getInstance(app, SAMPLE_SERVICE_CONFIG2))
                add(FirebaseDataConnect.getInstance(app, SAMPLE_SERVICE_CONFIG3))
              }
            }
            createdInstancesByThreadIdLock.withLock { createdInstancesByThreadId[i] = instances }
          }
        )
      }
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
      FirebaseDataConnect.getInstance(
        app = app,
        ServiceConfig(
          serviceId = "TestServiceId",
          location = "TestLocation",
          operationSet = "TestOperationSet",
          revision = "TestRevision"
        )
      )

    val toStringResult = instance.toString()

    assertThat(toStringResult).containsWithNonAdjacentText("app=${app.name}")
    assertThat(toStringResult).containsWithNonAdjacentText("projectId=${app.options.projectId}")
    assertThat(toStringResult).containsWithNonAdjacentText("serviceId=TestServiceId")
    assertThat(toStringResult).containsWithNonAdjacentText("location=TestLocation")
    assertThat(toStringResult).containsWithNonAdjacentText("operationSet=TestOperationSet")
    assertThat(toStringResult).containsWithNonAdjacentText("revision=TestRevision")
  }
}

private val SAMPLE_SERVICE_ID1 = "SampleServiceId1"
private val SAMPLE_LOCATION1 = "SampleLocation1"
private val SAMPLE_OPERATION_SET1 = "SampleOperationSet1"
private val SAMPLE_REVISION1 = "SampleRevision1"
private val SAMPLE_SERVICE_CONFIG1 =
  ServiceConfig(
    serviceId = SAMPLE_SERVICE_ID1,
    location = SAMPLE_LOCATION1,
    operationSet = SAMPLE_OPERATION_SET1,
    revision = SAMPLE_REVISION1
  )

private val SAMPLE_SERVICE_ID2 = "SampleServiceId2"
private val SAMPLE_LOCATION2 = "SampleLocation2"
private val SAMPLE_OPERATION_SET2 = "SampleOperationSet2"
private val SAMPLE_REVISION2 = "SampleRevision2"
private val SAMPLE_SERVICE_CONFIG2 =
  ServiceConfig(
    serviceId = SAMPLE_SERVICE_ID2,
    location = SAMPLE_LOCATION2,
    operationSet = SAMPLE_OPERATION_SET2,
    revision = SAMPLE_REVISION2
  )

private val SAMPLE_SERVICE_ID3 = "SampleServiceId3"
private val SAMPLE_LOCATION3 = "SampleLocation3"
private val SAMPLE_OPERATION_SET3 = "SampleOperationSet3"
private val SAMPLE_REVISION3 = "SampleRevision3"
private val SAMPLE_SERVICE_CONFIG3 =
  ServiceConfig(
    serviceId = SAMPLE_SERVICE_ID3,
    location = SAMPLE_LOCATION3,
    operationSet = SAMPLE_OPERATION_SET3,
    revision = SAMPLE_REVISION3
  )

private fun ServiceConfig.withServiceId(newServiceId: String) =
  ServiceConfig(
    serviceId = newServiceId,
    location = location,
    operationSet = operationSet,
    revision = revision,
  )

private fun ServiceConfig.withLocation(newLocation: String) =
  ServiceConfig(
    serviceId = serviceId,
    location = newLocation,
    operationSet = operationSet,
    revision = revision,
  )

private fun ServiceConfig.withOperationSet(newOperationSet: String) =
  ServiceConfig(
    serviceId = serviceId,
    location = location,
    operationSet = newOperationSet,
    revision = revision,
  )

private fun ServiceConfig.withRevision(newRevision: String) =
  ServiceConfig(
    serviceId = serviceId,
    location = location,
    operationSet = operationSet,
    revision = newRevision,
  )
