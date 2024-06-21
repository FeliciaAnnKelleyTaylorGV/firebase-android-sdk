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

package com.google.firebase.dataconnect

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.dataconnect.testutil.DataConnectBackend
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.InProcessDataConnectGrpcServer
import com.google.firebase.dataconnect.testutil.assertThrows
import com.google.firebase.dataconnect.testutil.newInstance
import com.google.firebase.dataconnect.testutil.operationName
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetPersonAuthQuery
import com.google.firebase.dataconnect.testutil.schemas.randomPersonId
import com.google.firebase.dataconnect.util.buildStructProto
import com.google.firebase.util.nextAlphanumericString
import google.firebase.dataconnect.proto.executeQueryResponse
import io.grpc.Status
import io.kotest.assertions.asClue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.next
import kotlin.random.Random
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthIntegrationTest : DataConnectIntegrationTestBase() {

  private val key = "e6w33rw36t"
  private val rs = RandomSource.default()

  @get:Rule val inProcessDataConnectGrpcServer = InProcessDataConnectGrpcServer()

  private val personSchema by lazy { PersonSchema(dataConnectFactory) }

  private val auth: FirebaseAuth by lazy {
    DataConnectBackend.fromInstrumentationArguments()
      .authBackend
      .getFirebaseAuth(personSchema.dataConnect.app)
  }

  @Test
  fun authenticatedRequestsAreSuccessful() = runTest {
    signIn()
    val person1Id = randomPersonId()
    val person2Id = randomPersonId()
    val person3Id = randomPersonId()

    personSchema.createPersonAuth(id = person1Id, name = "TestName1", age = 42).execute()
    personSchema.createPersonAuth(id = person2Id, name = "TestName2", age = 43).execute()
    personSchema.createPersonAuth(id = person3Id, name = "TestName3", age = 44).execute()
    val queryResult = personSchema.getPersonAuth(id = person2Id).execute()

    assertThat(queryResult.data.person).isEqualTo(GetPersonAuthQuery.Data.Person("TestName2", 43))
  }

  @Test
  fun queryFailsAfterUserSignsOut() = runTest {
    signIn()
    // Verify that we are signed in by executing a query, which should succeed.
    personSchema.getPersonAuth(id = "foo").execute()
    signOut()

    val exception =
      assertThrows(io.grpc.StatusException::class) {
        personSchema.getPersonAuth(id = "foo").execute()
      }

    assertThat(exception.status.code).isEqualTo(Status.UNAUTHENTICATED.code)
  }

  @Test
  fun mutationFailsAfterUserSignsOut() = runTest {
    signIn()
    // Verify that we are signed in by executing a mutation, which should succeed.
    personSchema.createPersonAuth(id = Random.nextAlphanumericString(20), name = "foo").execute()
    signOut()

    val exception =
      assertThrows(io.grpc.StatusException::class) {
        personSchema
          .createPersonAuth(id = Random.nextAlphanumericString(20), name = "foo")
          .execute()
      }

    assertThat(exception.status.code).isEqualTo(Status.UNAUTHENTICATED.code)
  }

  @Test
  fun queryShouldRetryOnUnauthenticated() = runTest {
    val responseData = buildStructProto { put("foo", key) }
    val executeQueryResponse = executeQueryResponse { data = responseData }
    val grpcServer =
      inProcessDataConnectGrpcServer.newInstance(
        errors = listOf(Status.UNAUTHENTICATED),
        executeQueryResponse = executeQueryResponse
      )
    val dataConnect = dataConnectFactory.newInstance(grpcServer)
    val operationName = Arb.operationName(key).next(rs)
    val queryRef =
      dataConnect.query(operationName, Unit, serializer<TestData>(), serializer<Unit>())

    val actualResponse = queryRef.execute()

    actualResponse.asClue { it.data shouldBe TestData(key) }
  }

  private suspend fun signIn() {
    val authResult = auth.run { signInAnonymously().await() }
    assertWithMessage("authResult.user").that(authResult.user).isNotNull()
  }

  private fun signOut() {
    auth.run { signOut() }
  }

  @Serializable data class TestData(val foo: String)
}
