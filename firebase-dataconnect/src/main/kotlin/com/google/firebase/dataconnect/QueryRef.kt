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

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

class QueryRef<VariablesType, DataType>
internal constructor(
  dataConnect: FirebaseDataConnect,
  operationName: String,
  operationSet: String,
  revision: String,
  variablesSerializer: SerializationStrategy<VariablesType>,
  dataDeserializer: DeserializationStrategy<DataType>
) :
  BaseRef<VariablesType, DataType>(
    dataConnect = dataConnect,
    operationName = operationName,
    operationSet = operationSet,
    revision = revision,
    variablesSerializer = variablesSerializer,
    dataDeserializer = dataDeserializer,
  ) {
  override suspend fun execute(
    variables: VariablesType
  ): DataConnectResult<VariablesType, DataType> = dataConnect.executeQuery(this, variables)

  fun subscribe(variables: VariablesType): QuerySubscription<VariablesType, DataType> =
    QuerySubscription(this, variables)
}
