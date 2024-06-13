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

import com.google.firebase.dataconnect.ConnectorConfig
import com.google.firebase.dataconnect.testutil.accessToken
import com.google.firebase.dataconnect.testutil.connectorConfig
import com.google.firebase.dataconnect.testutil.requestId
import io.grpc.Metadata
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.next
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DataConnectGrpcMetadataUnitTest {

  @Test
  fun `should set x-goog-request-params`() = runTest {
    val key = "67ns7bkvx8"
    val testValues = DataConnectGrpcMetadataTestValues.fromKey(key)
    val dataConnectGrpcMetadata = testValues.newDataConnectGrpcMetadata()
    val requestId = Arb.requestId(key).next()

    val metadata = dataConnectGrpcMetadata.get(requestId)

    metadata.keys() shouldContain "x-goog-request-params"
    metadata.get(
      Metadata.Key.of("x-goog-request-params", Metadata.ASCII_STRING_MARSHALLER)
    ) shouldBe "location=${testValues.connectorConfig.location}&frontend=data"
  }

  private data class DataConnectGrpcMetadataTestValues(
    val dataConnectAuth: DataConnectAuth,
    val requestIdSlot: CapturingSlot<String>,
    val connectorConfig: ConnectorConfig,
  ) {

    fun newDataConnectGrpcMetadata(): DataConnectGrpcMetadata =
      DataConnectGrpcMetadata(dataConnectAuth, connectorConfig)

    companion object {
      fun fromKey(
        key: String,
        rs: RandomSource = RandomSource.default()
      ): DataConnectGrpcMetadataTestValues {
        val dataConnectAuth: DataConnectAuth = mockk(relaxed = true)

        val accessTokenArb = Arb.accessToken(key)
        val requestIdSlot = slot<String>()
        coEvery { dataConnectAuth.getAccessToken(capture(requestIdSlot)) } answers
          {
            accessTokenArb.next(rs)
          }

        return DataConnectGrpcMetadataTestValues(
          dataConnectAuth = dataConnectAuth,
          requestIdSlot = requestIdSlot,
          connectorConfig = Arb.connectorConfig(key).next(rs),
        )
      }
    }
  }
}
