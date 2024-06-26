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
package com.google.firebase.dataconnect.core

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.GetTokenResult
import com.google.firebase.auth.internal.IdTokenListener
import com.google.firebase.auth.internal.InternalAuthProvider
import com.google.firebase.dataconnect.DataConnectException
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.DelayedDeferred
import com.google.firebase.dataconnect.testutil.ImmediateDeferred
import com.google.firebase.dataconnect.testutil.SuspendingCountDownLatch
import com.google.firebase.dataconnect.testutil.UnavailableDeferred
import com.google.firebase.dataconnect.testutil.accessToken
import com.google.firebase.dataconnect.testutil.newBackgroundScopeThatAdvancesLikeForeground
import com.google.firebase.dataconnect.testutil.newMockLogger
import com.google.firebase.dataconnect.testutil.requestId
import com.google.firebase.dataconnect.testutil.shouldHaveLoggedAtLeastOneMessageContaining
import com.google.firebase.dataconnect.testutil.shouldHaveLoggedExactlyOneMessageContaining
import com.google.firebase.dataconnect.testutil.shouldNotHaveLoggedAnyMessagesContaining
import com.google.firebase.inject.Deferred.DeferredHandler
import com.google.firebase.internal.api.FirebaseNoSignedInUserException
import io.kotest.assertions.asClue
import io.kotest.assertions.nondeterministic.continually
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.next
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.excludeRecords
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Rule
import org.junit.Test

private typealias DeferredInternalAuthProvider =
  com.google.firebase.inject.Deferred<InternalAuthProvider>

class DataConnectAuthUnitTest {

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()

  private val key = "qqddxntcwk"
  private val rs = RandomSource.default()
  private val accessTokenGenerator = Arb.accessToken(key)
  private val accessToken: String = accessTokenGenerator.next(rs)
  private val requestId = Arb.requestId(key).next(rs)
  private val mockInternalAuthProvider: InternalAuthProvider =
    mockk(relaxed = true, name = "mockInternalAuthProvider-$key") {
      excludeRecords { this@mockk.toString() }
    }
  private val mockLogger = newMockLogger(key)

  @Test
  fun `close() should log a message`() = runTest {
    val dataConnectAuth = newDataConnectAuth()

    dataConnectAuth.close()

    mockLogger.shouldHaveLoggedExactlyOneMessageContaining("close()")
  }

  @Test
  fun `close() should cancel in-flight requests to get a token`() = runTest {
    val dataConnectAuth = newDataConnectAuth()
    coEvery { mockInternalAuthProvider.getAccessToken(any()) } coAnswers
      {
        dataConnectAuth.close()
        taskForToken(
          "wz44t6wqz7 SHOULD NOT GET HERE" +
            " because join() should have thrown CancellationException"
        )
      }

    val exception = shouldThrow<DataConnectException> { dataConnectAuth.getAccessToken(requestId) }

    exception shouldHaveMessage "getAccessToken() was cancelled, likely by close()"
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining(requestId)
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining(
      "throws GetAccessTokenCancelledException"
    )
  }

  @Test
  fun `close() should remove the IdTokenListener`() = runTest {
    val dataConnectAuth = newDataConnectAuth()
    val idTokenListenerSlot = slot<IdTokenListener>()
    verify { mockInternalAuthProvider.addIdTokenListener(capture(idTokenListenerSlot)) }
    val idTokenListener = idTokenListenerSlot.captured

    dataConnectAuth.close()

    verify { mockInternalAuthProvider.removeIdTokenListener(idTokenListener) }
  }

  @Test
  fun `close() should be callable multiple times, from multiple threads`() = runTest {
    val dataConnectAuth = newDataConnectAuth()

    val latch = SuspendingCountDownLatch(100)
    val jobs =
      List(latch.count) {
        backgroundScope.async(Dispatchers.IO) {
          latch.run {
            countDown()
            await()
          }
          dataConnectAuth.close()
        }
      }

    // Await each job to make sure that each invocation returns successfully.
    jobs.forEach { it.await() }
  }

  @Test
  fun `getAccessToken() should return null if InternalAuthProvider is not available`() = runTest {
    val dataConnectAuth = newDataConnectAuth(deferredInternalAuthProvider = UnavailableDeferred())

    val result = dataConnectAuth.getAccessToken(requestId)

    withClue("result=$result") { result.shouldBeNull() }
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining(requestId)
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining("returns null")
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining("FirebaseAuth is not (yet?) available")
  }

  @Test
  fun `getAccessToken() should throw if invoked after close()`() = runTest {
    val dataConnectAuth = newDataConnectAuth()
    dataConnectAuth.close()

    val exception = shouldThrow<DataConnectException> { dataConnectAuth.getAccessToken(requestId) }

    exception shouldHaveMessage "DataConnectAuth was closed"
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining(requestId)
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining("throws DataConnectAuthClosedException")
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining("has been closed")
  }

  @Test
  fun `getAccessToken() should return null if no user is signed in`() = runTest {
    val dataConnectAuth = newDataConnectAuth()
    coEvery { mockInternalAuthProvider.getAccessToken(any()) } returns
      Tasks.forException(FirebaseNoSignedInUserException("j8rkghbcnz"))

    val result = dataConnectAuth.getAccessToken(requestId)

    withClue("result=$result") { result.shouldBeNull() }
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining(requestId)
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining("returns null")
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining("FirebaseAuth reports no signed-in user")
  }

  @Test
  fun `getAccessToken() should return the token returned from FirebaseAuth`() = runTest {
    val dataConnectAuth = newDataConnectAuth()
    coEvery { mockInternalAuthProvider.getAccessToken(any()) } returns taskForToken(accessToken)

    val result = dataConnectAuth.getAccessToken(requestId)

    withClue("result=$result") { result shouldBe accessToken }
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining(requestId)
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining(
      "returns value obtained from FirebaseAuth: ${accessToken.toScrubbedAccessToken()}"
    )
    mockLogger.shouldNotHaveLoggedAnyMessagesContaining(accessToken)
  }

  @Test
  fun `getAccessToken() should return re-throw the exception from the task returned from FirebaseAuth`() =
    runTest {
      class TestException(message: String) : Exception(message)

      val exception = TestException("xqtbckcn6w")
      val dataConnectAuth = newDataConnectAuth()
      coEvery { mockInternalAuthProvider.getAccessToken(any()) } returns
        Tasks.forException(exception)

      val result = dataConnectAuth.runCatching { getAccessToken(requestId) }

      result.asClue { it.exceptionOrNull() shouldBeSameInstanceAs exception }
      mockLogger.shouldHaveLoggedExactlyOneMessageContaining(requestId)
      mockLogger.shouldHaveLoggedExactlyOneMessageContaining(
        "getAccessToken() failed unexpectedly",
        exception
      )
    }

  @Test
  fun `getAccessToken() should return re-throw the exception thrown by InternalAuthProvider getAccessToken()`() =
    runTest {
      class TestException(message: String) : Exception(message)

      val exception = TestException("s4c4xr9z4p")
      val dataConnectAuth = newDataConnectAuth()
      coEvery { mockInternalAuthProvider.getAccessToken(any()) } answers { throw exception }

      val result = dataConnectAuth.runCatching { getAccessToken(requestId) }

      result.asClue { it.exceptionOrNull() shouldBeSameInstanceAs exception }
      mockLogger.shouldHaveLoggedExactlyOneMessageContaining(requestId)
      mockLogger.shouldHaveLoggedExactlyOneMessageContaining(
        "getAccessToken() failed unexpectedly",
        exception
      )
    }

  @Test
  fun `getAccessToken() should force refresh the access token after calling forceRefresh()`() =
    runTest {
      val dataConnectAuth = newDataConnectAuth()
      coEvery { mockInternalAuthProvider.getAccessToken(any()) } returns taskForToken(accessToken)

      dataConnectAuth.forceRefresh()
      val result = dataConnectAuth.getAccessToken(requestId)

      withClue("result=$result") { result shouldBe accessToken }
      verify(exactly = 1) { mockInternalAuthProvider.getAccessToken(true) }
      verify(exactly = 0) { mockInternalAuthProvider.getAccessToken(false) }
      mockLogger.shouldHaveLoggedExactlyOneMessageContaining(requestId)
      mockLogger.shouldHaveLoggedExactlyOneMessageContaining("getAccessToken(forceRefresh=true)")
      mockLogger.shouldHaveLoggedExactlyOneMessageContaining(
        "returns value obtained from FirebaseAuth: ${accessToken.toScrubbedAccessToken()}"
      )
      mockLogger.shouldNotHaveLoggedAnyMessagesContaining(accessToken)
    }

  @Test
  fun `getAccessToken() should NOT force refresh the access token without calling forceRefresh()`() =
    runTest {
      val dataConnectAuth = newDataConnectAuth()
      coEvery { mockInternalAuthProvider.getAccessToken(any()) } returns taskForToken(accessToken)

      dataConnectAuth.getAccessToken(requestId)

      verify(exactly = 1) { mockInternalAuthProvider.getAccessToken(false) }
      verify(exactly = 0) { mockInternalAuthProvider.getAccessToken(true) }
      mockLogger.shouldHaveLoggedExactlyOneMessageContaining("getAccessToken(forceRefresh=false)")
    }

  @Test
  fun `getAccessToken() should NOT force refresh the access token after it is force refreshed`() =
    runTest {
      val dataConnectAuth = newDataConnectAuth()
      coEvery { mockInternalAuthProvider.getAccessToken(any()) } returns taskForToken(accessToken)

      dataConnectAuth.forceRefresh()
      dataConnectAuth.getAccessToken(requestId)
      dataConnectAuth.getAccessToken(requestId)
      dataConnectAuth.getAccessToken(requestId)

      verify(exactly = 2) { mockInternalAuthProvider.getAccessToken(false) }
      verify(exactly = 1) { mockInternalAuthProvider.getAccessToken(true) }
      mockLogger.shouldHaveLoggedAtLeastOneMessageContaining("getAccessToken(forceRefresh=false)")
      mockLogger.shouldHaveLoggedExactlyOneMessageContaining("getAccessToken(forceRefresh=true)")
    }

  @Test
  fun `getAccessToken() should ask for a token from FirebaseAuth on every invocation`() = runTest {
    val dataConnectAuth = newDataConnectAuth()
    val tokens = CopyOnWriteArrayList<String>()
    coEvery { mockInternalAuthProvider.getAccessToken(any()) } answers
      {
        taskForToken(accessTokenGenerator.next().also { tokens.add(it) })
      }

    val results = List(5) { dataConnectAuth.getAccessToken(requestId) }

    results shouldContainExactly tokens
  }

  @Test
  fun `getAccessToken() should conflate concurrent requests`() = runTest {
    val dataConnectAuth = newDataConnectAuth()
    val tokens = CopyOnWriteArrayList<String>()
    coEvery { mockInternalAuthProvider.getAccessToken(any()) } answers
      {
        taskForToken(accessTokenGenerator.next().also { tokens.add(it) })
      }

    val latch = SuspendingCountDownLatch(500)
    val jobs =
      List(latch.count) {
        backgroundScope.async(Dispatchers.IO) {
          latch.run {
            countDown()
            await()
          }
          dataConnectAuth.getAccessToken(requestId)
        }
      }

    val actualTokens = jobs.map { it.await() }
    actualTokens.forEachIndexed { index, token ->
      withClue("actualTokens[$index]") { tokens shouldContain token }
    }
    verify(atMost = 50) { mockInternalAuthProvider.getAccessToken(any()) }
  }

  @Test
  fun `getAccessToken() should re-fetch token if invalidated concurrently`() = runTest {
    val dataConnectAuth = newDataConnectAuth()
    val invocationCount = AtomicInteger(0)
    val tokens = CopyOnWriteArrayList<String>().apply { add(accessToken) }
    coEvery { mockInternalAuthProvider.getAccessToken(any()) } coAnswers
      {
        val invocationIndex = invocationCount.getAndIncrement()
        if (invocationIndex == 0 || invocationIndex == 1) {
          // Simulate a concurrent call to forceRefresh() while
          // InternalAuthProvider.getAccessToken() is in-flight.
          dataConnectAuth.forceRefresh()
        }
        val forceRefresh: Boolean = firstArg()
        val token =
          if (!forceRefresh) {
            tokens.last()
          } else {
            accessTokenGenerator.next().also { tokens.add(it) }
          }
        taskForToken(token)
      }

    val result = dataConnectAuth.getAccessToken(requestId)

    withClue("result=$result") { result shouldBe tokens.last() }
    verify(exactly = 2) { mockInternalAuthProvider.getAccessToken(true) }
    verify(exactly = 1) { mockInternalAuthProvider.getAccessToken(false) }
    mockLogger.shouldHaveLoggedAtLeastOneMessageContaining("retrying due to needs token refresh")
    mockLogger.shouldHaveLoggedAtLeastOneMessageContaining("getAccessToken(forceRefresh=true)")
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining("getAccessToken(forceRefresh=false)")
  }

  @Test
  @OptIn(ExperimentalCoroutinesApi::class)
  fun `getAccessToken() should ignore results with lower sequence number`() = runTest {
    val dataConnectAuth = newDataConnectAuth()
    val invocationCount = AtomicInteger(0)
    val tokens = CopyOnWriteArrayList<String>()
    val getAccessTokenJob2 =
      async(start = CoroutineStart.LAZY) {
        val accessToken = dataConnectAuth.getAccessToken(requestId)
        accessToken
      }
    coEvery { mockInternalAuthProvider.getAccessToken(any()) } coAnswers
      {
        if (invocationCount.getAndIncrement() == 0) {
          // Simulate a concurrent call to forceRefresh() while
          // InternalAuthProvider.getAccessToken() is in-flight.
          getAccessTokenJob2.start()
          advanceUntilIdle()
        }
        val rv = taskForToken(accessTokenGenerator.next().also { tokens.add(it) })
        rv
      }

    val result1 = dataConnectAuth.getAccessToken(requestId)
    withClue("getAccessTokenJob2.isActive") { getAccessTokenJob2.isActive shouldBe true }
    val result2 = getAccessTokenJob2.await()

    withClue("result1=$result1") { result1 shouldBe tokens[0] }
    withClue("result2=$result2") { result2 shouldBe tokens[1] }
    verify(exactly = 2) { mockInternalAuthProvider.getAccessToken(false) }
    verify(exactly = 0) { mockInternalAuthProvider.getAccessToken(true) }
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining("got an old result; retrying")
  }

  @Test
  fun `DataConnectAuth initializes even if whenAvailable() throws`() = runTest {
    class TestException : Exception("z44jcswqxq")

    val testException = TestException()
    val deferredInternalAuthProvider: DeferredInternalAuthProvider = mockk {
      every { whenAvailable(any()) } throws testException
    }
    val dataConnectAuth =
      newDataConnectAuth(deferredInternalAuthProvider = deferredInternalAuthProvider)

    val result = dataConnectAuth.getAccessToken(requestId)
    dataConnectAuth.close()

    withClue("result=$result") { result.shouldBeNull() }
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining("$testException")
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining("k6rwgqg9gh")
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining("deferredAuthProvider.whenAvailable")
    mockLogger.shouldHaveLoggedExactlyOneMessageContaining("FirebaseAuth is not (yet?) available")
  }

  @Test
  fun `addIdTokenListener() should NOT be called if whenAvailable() calls back after close()`() =
    runTest {
      val deferredInternalAuthProvider: DeferredInternalAuthProvider = mockk(relaxed = true)
      val dataConnectAuth =
        newDataConnectAuth(deferredInternalAuthProvider = deferredInternalAuthProvider)
      dataConnectAuth.close()
      val deferredInternalAuthProviderHandlerSlot = slot<DeferredHandler<InternalAuthProvider>>()
      verify {
        deferredInternalAuthProvider.whenAvailable(capture(deferredInternalAuthProviderHandlerSlot))
      }

      deferredInternalAuthProviderHandlerSlot.captured.handle { mockInternalAuthProvider }

      continually(duration = 500.milliseconds) {
        confirmVerified(deferredInternalAuthProvider)
        yield()
      }
    }

  @Test
  fun `removeIdTokenListener() should be called if close() is called concurrently during addIdTokenListener()`() =
    runTest {
      val deferredInternalAuthProvider = DelayedDeferred(mockInternalAuthProvider)
      val dataConnectAuth =
        newDataConnectAuth(deferredInternalAuthProvider = deferredInternalAuthProvider)
      every { mockInternalAuthProvider.addIdTokenListener(any()) } answers
        {
          dataConnectAuth.close()
        }
      deferredInternalAuthProvider.makeAvailable()
      val idTokenListenerSlot = slot<IdTokenListener>()
      eventually(`check every 100 milliseconds for 2 seconds`) {
        verify { mockInternalAuthProvider.addIdTokenListener(capture(idTokenListenerSlot)) }
      }
      val idTokenListener = idTokenListenerSlot.captured

      eventually(`check every 100 milliseconds for 2 seconds`) {
        verify { mockInternalAuthProvider.removeIdTokenListener(idTokenListener) }
      }
      mockLogger.shouldHaveLoggedExactlyOneMessageContaining(
        "unregistering IdTokenListener that was just added"
      )
    }

  @Test
  fun `addIdTokenListener() throwing IllegalStateException due to FirebaseApp deleted should be ignored`() =
    runTest {
      every { mockInternalAuthProvider.addIdTokenListener(any()) } throws
        firebaseAppDeletedException
      coEvery { mockInternalAuthProvider.getAccessToken(any()) } returns taskForToken(accessToken)
      val dataConnectAuth = newDataConnectAuth()

      eventually(`check every 100 milliseconds for 2 seconds`) {
        mockLogger.shouldHaveLoggedExactlyOneMessageContaining(
          "ignoring exception: $firebaseAppDeletedException"
        )
      }
      val result = dataConnectAuth.getAccessToken(requestId)
      withClue("result=$result") { result shouldBe accessToken }
    }

  @Test
  fun `removeIdTokenListener() throwing IllegalStateException due to FirebaseApp deleted should be ignored`() =
    runTest {
      every { mockInternalAuthProvider.removeIdTokenListener(any()) } throws
        firebaseAppDeletedException
      val dataConnectAuth = newDataConnectAuth()

      dataConnectAuth.close()

      eventually(`check every 100 milliseconds for 2 seconds`) {
        mockLogger.shouldHaveLoggedExactlyOneMessageContaining(
          "ignoring exception: $firebaseAppDeletedException"
        )
      }
    }

  private fun TestScope.newDataConnectAuth(
    deferredInternalAuthProvider: DeferredInternalAuthProvider =
      ImmediateDeferred(mockInternalAuthProvider),
    logger: Logger = mockLogger,
  ): DataConnectAuth {
    val parentCoroutineScope = newBackgroundScopeThatAdvancesLikeForeground()
    val dataConnectAuth =
      DataConnectAuth(
        deferredAuthProvider = deferredInternalAuthProvider,
        parentCoroutineScope = parentCoroutineScope,
        blockingDispatcher =
          StandardTestDispatcher(testScheduler, name = "4jg7adscn6_DataConnectAuth_TestDispatcher"),
        logger = logger
      )

    @OptIn(ExperimentalCoroutinesApi::class) advanceUntilIdle()

    return dataConnectAuth
  }

  private companion object {
    val `check every 100 milliseconds for 2 seconds` = eventuallyConfig {
      duration = 2.seconds
      interval = 100.milliseconds
    }

    val firebaseAppDeletedException
      get() = java.lang.IllegalStateException("FirebaseApp was deleted")

    fun taskForToken(token: String?): Task<GetTokenResult> =
      Tasks.forResult(mockk(relaxed = true) { every { getToken() } returns token })
  }
}
