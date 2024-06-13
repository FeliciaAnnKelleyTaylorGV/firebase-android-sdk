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
import com.google.firebase.dataconnect.testutil.randomConnectorConfig
import com.google.firebase.dataconnect.testutil.randomOperationName
import com.google.firebase.dataconnect.testutil.randomProjectId
import com.google.firebase.dataconnect.testutil.randomRequestId
import com.google.firebase.dataconnect.util.buildStructProto
import google.firebase.dataconnect.proto.ExecuteMutationRequest
import google.firebase.dataconnect.proto.ExecuteMutationResponse
import google.firebase.dataconnect.proto.ExecuteQueryRequest
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.Test

class DataConnectGrpcClientUnitTest {

  @Test
  fun `executeQuery() should send the right request`() {
    val key = "3sw2m4vkbg"
    val testValues = TestValues.fromKey(key)
    val dataConnectGrpcClient = testValues.newDataConnectGrpcClient()
    val requestId = randomRequestId(key)
    val operationName = randomOperationName(key)
    val variables = buildStructProto { put("foo", key) }

    runBlocking { dataConnectGrpcClient.executeQuery(requestId, operationName, variables) }

    testValues.executeQueryRequestIdSlot.let { slot ->
      withClue("requestId w2rr32n24c") {
        slot.isCaptured shouldBe true
        slot.captured shouldBe requestId
      }
    }
    testValues.executeQueryRequestSlot.let { slot ->
      withClue("request rh3vefnap4") {
        slot.isCaptured shouldBe true
        slot.captured.let { request ->
          request.name shouldBe
            ("projects/${testValues.projectId}" +
              "/locations/${testValues.connectorConfig.location}" +
              "/services/${testValues.connectorConfig.serviceId}" +
              "/connectors/${testValues.connectorConfig.connector}")
          request.operationName shouldBe operationName
          request.variables shouldBe variables
        }
      }
    }
  }

  @Test
  fun `executeMutation() should send the right request`() {
    val key = "hbfkfxw5z8"
    val testValues = TestValues.fromKey(key)
    val dataConnectGrpcClient = testValues.newDataConnectGrpcClient()
    val requestId = randomRequestId(key)
    val operationName = randomOperationName(key)
    val variables = buildStructProto { put("foo", key) }

    runBlocking { dataConnectGrpcClient.executeQuery(requestId, operationName, variables) }

    testValues.executeQueryRequestIdSlot.let { slot ->
      withClue("requestId kcgx4e3j3a") {
        slot.isCaptured shouldBe true
        slot.captured shouldBe requestId
      }
    }
    testValues.executeQueryRequestSlot.let { slot ->
      withClue("request rb52zfft9z") {
        slot.isCaptured shouldBe true
        slot.captured.let { request ->
          request.name shouldBe
            ("projects/${testValues.projectId}" +
              "/locations/${testValues.connectorConfig.location}" +
              "/services/${testValues.connectorConfig.serviceId}" +
              "/connectors/${testValues.connectorConfig.connector}")
          request.operationName shouldBe operationName
          request.variables shouldBe variables
        }
      }
    }
  }

  private data class TestValues(
    val dataConnectGrpcRPCs: DataConnectGrpcRPCs,
    val projectId: String,
    val connectorConfig: ConnectorConfig,
    val executeQueryRequestIdSlot: CapturingSlot<String>,
    val executeQueryRequestSlot: CapturingSlot<ExecuteQueryRequest>,
    val executeMutationRequestIdSlot: CapturingSlot<String>,
    val executeMutationRequestSlot: CapturingSlot<ExecuteMutationRequest>,
  ) {
    fun newDataConnectGrpcClient(): DataConnectGrpcClient =
      DataConnectGrpcClient(
        projectId = projectId,
        connectorConfig = connectorConfig,
        dataConnectGrpcRPCs = dataConnectGrpcRPCs,
        logger = mockk(relaxed = true)
      )
    companion object {
      fun fromKey(key: String): TestValues {
        val dataConnectGrpcRPCs: DataConnectGrpcRPCs = mockk(relaxed = true)

        val executeQueryRequestIdSlot = slot<String>()
        val executeQueryRequestSlot = slot<ExecuteQueryRequest>()
        coEvery {
          dataConnectGrpcRPCs.executeQuery(
            capture(executeQueryRequestIdSlot),
            capture(executeQueryRequestSlot)
          )
        } returns ExecuteQueryResponse.getDefaultInstance()

        val executeMutationRequestIdSlot = slot<String>()
        val executeMutationRequestSlot = slot<ExecuteMutationRequest>()
        coEvery {
          dataConnectGrpcRPCs.executeMutation(
            capture(executeMutationRequestIdSlot),
            capture(executeMutationRequestSlot)
          )
        } returns ExecuteMutationResponse.getDefaultInstance()

        return TestValues(
          dataConnectGrpcRPCs = dataConnectGrpcRPCs,
          projectId = randomProjectId(key),
          connectorConfig = randomConnectorConfig(key),
          executeQueryRequestIdSlot = executeQueryRequestIdSlot,
          executeQueryRequestSlot = executeQueryRequestSlot,
          executeMutationRequestIdSlot = executeMutationRequestIdSlot,
          executeMutationRequestSlot = executeMutationRequestSlot,
        )
      }
    }
  }
}
