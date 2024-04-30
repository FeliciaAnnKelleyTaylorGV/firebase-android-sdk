package com.google.firebase.dataconnect

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.dataconnect.testutil.DataConnectIntegrationTestBase
import com.google.firebase.dataconnect.testutil.assertThrows
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema
import com.google.firebase.dataconnect.testutil.schemas.PersonSchema.GetPersonAuthQuery
import com.google.firebase.dataconnect.testutil.schemas.randomPersonId
import com.google.firebase.dataconnect.util.SuspendingLazy
import com.google.firebase.util.nextAlphanumericString
import io.grpc.Status
import kotlin.random.Random
import kotlinx.coroutines.tasks.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthIntegrationTest : DataConnectIntegrationTestBase() {

  private val personSchema by lazy { PersonSchema(dataConnectFactory) }

  private val auth = SuspendingLazy {
    FirebaseAuth.getInstance(personSchema.dataConnect.app).apply { useEmulator("10.0.2.2", 9099) }
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

  private suspend fun signIn() {
    val authResult = auth.get().run { signInAnonymously().await() }
    assertWithMessage("authResult.user").that(authResult.user).isNotNull()
  }

  private suspend fun signOut() {
    auth.get().run { signOut() }
  }
}
