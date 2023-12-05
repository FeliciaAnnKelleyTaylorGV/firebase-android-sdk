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
import com.google.common.truth.Truth.assertWithMessage
import com.google.firebase.dataconnect.testutil.DataConnectLogLevelRule
import com.google.firebase.dataconnect.testutil.TestDataConnectFactory
import com.google.firebase.dataconnect.testutil.schemas.AllTypesSchema
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataConnectUntypedDataIntegrationTest {

  @get:Rule val dataConnectLogLevelRule = DataConnectLogLevelRule()
  @get:Rule val dataConnectFactory = TestDataConnectFactory()

  private val allTypesSchema
    get() = dataConnectFactory.allTypesSchema

  @Test
  fun primitiveTypes() = runTest {
    allTypesSchema.createPrimitive.execute(
      AllTypesSchema.CreatePrimitiveMutation.Variables(
        AllTypesSchema.PrimitiveData(
          id = "abc123",
          idFieldNullable = "xyz",
          intField = 42,
          intFieldNullable = 43,
          floatField = 99.0,
          floatFieldNullable = 100.0,
          booleanField = false,
          booleanFieldNullable = true,
          stringField = "TestStringValue",
          stringFieldNullable = "TestStringNullableValue",
        )
      )
    )
    val query = allTypesSchema.getPrimitive.withDataDeserializer(DataConnectUntypedData)

    val result = query.execute(AllTypesSchema.GetPrimitiveQuery.Variables(id = "abc123"))

    assertWithMessage("errors").that(result.data.errors).isEmpty()
    assertWithMessage("data").that(result.data.data).isNotNull()
    assertWithMessage("data.keys").that(result.data.data?.keys).containsExactly("primitive")
    assertWithMessage("data.keys[primitive]")
      .that(result.data.data?.get("primitive") as Map<*, *>)
      .containsExactlyEntriesIn(
        mapOf(
          "id" to "abc123",
          "idFieldNullable" to "xyz",
          "intField" to 42.0,
          "intFieldNullable" to 43.0,
          "floatField" to 99.0,
          "floatFieldNullable" to 100.0,
          "booleanField" to false,
          "booleanFieldNullable" to true,
          "stringField" to "TestStringValue",
          "stringFieldNullable" to "TestStringNullableValue",
        )
      )
  }

  @Test
  fun nullPrimitiveTypes() = runTest {
    allTypesSchema.createPrimitive.execute(
      AllTypesSchema.CreatePrimitiveMutation.Variables(
        AllTypesSchema.PrimitiveData(
          id = "abc123",
          idFieldNullable = null,
          intField = 42,
          intFieldNullable = null,
          floatField = 99.0,
          floatFieldNullable = null,
          booleanField = false,
          booleanFieldNullable = null,
          stringField = "TestStringValue",
          stringFieldNullable = null,
        )
      )
    )
    val query = allTypesSchema.getPrimitive.withDataDeserializer(DataConnectUntypedData)

    val result = query.execute(AllTypesSchema.GetPrimitiveQuery.Variables(id = "abc123"))

    assertWithMessage("errors").that(result.data.errors).isEmpty()
    assertWithMessage("data").that(result.data.data).isNotNull()
    assertWithMessage("data.keys").that(result.data.data?.keys).containsExactly("primitive")
    assertWithMessage("data.keys[primitive]")
      .that(result.data.data?.get("primitive") as Map<*, *>)
      .containsExactlyEntriesIn(
        mapOf(
          "id" to "abc123",
          "idFieldNullable" to null,
          "intField" to 42.0,
          "intFieldNullable" to null,
          "floatField" to 99.0,
          "floatFieldNullable" to null,
          "booleanField" to false,
          "booleanFieldNullable" to null,
          "stringField" to "TestStringValue",
          "stringFieldNullable" to null,
        )
      )
  }

  @Test
  fun listsOfPrimitiveTypes() = runTest {
    allTypesSchema.createPrimitiveList.execute(
      AllTypesSchema.CreatePrimitiveListMutation.Variables(
        AllTypesSchema.PrimitiveListData(
          id = "abc123",
          idListNullable = listOf("aaa", "bbb"),
          idListOfNullable = listOf("ccc", "ddd"),
          intList = listOf(42, 43, 44),
          intListNullable = listOf(45, 46),
          intListOfNullable = listOf(47, 48),
          floatList = listOf(12.3, 45.6, 78.9),
          floatListNullable = listOf(98.7, 65.4),
          floatListOfNullable = listOf(100.1, 100.2),
          booleanList = listOf(true, false, true, false),
          booleanListNullable = listOf(false, true, false, true),
          booleanListOfNullable = listOf(false, false, true, true),
          stringList = listOf("xxx", "yyy", "zzz"),
          stringListNullable = listOf("qqq", "rrr"),
          stringListOfNullable = listOf("sss", "ttt"),
        )
      )
    )
    val query = allTypesSchema.getPrimitiveList.withDataDeserializer(DataConnectUntypedData)

    val result = query.execute(AllTypesSchema.GetPrimitiveListQuery.Variables(id = "abc123"))

    assertWithMessage("errors").that(result.data.errors).isEmpty()
    assertWithMessage("data").that(result.data.data).isNotNull()
    assertWithMessage("data.keys").that(result.data.data?.keys).containsExactly("primitiveList")
    assertWithMessage("data.keys[primitiveList]")
      .that(result.data.data?.get("primitiveList") as Map<*, *>)
      .containsExactlyEntriesIn(
        mapOf(
          "id" to "abc123",
          "idListNullable" to listOf("aaa", "bbb"),
          "idListOfNullable" to listOf("ccc", "ddd"),
          "intList" to listOf(42.0, 43.0, 44.0),
          "intListNullable" to listOf(45.0, 46.0),
          "intListOfNullable" to listOf(47.0, 48.0),
          "floatList" to listOf(12.3, 45.6, 78.9),
          "floatListNullable" to listOf(98.7, 65.4),
          "floatListOfNullable" to listOf(100.1, 100.2),
          "booleanList" to listOf(true, false, true, false),
          "booleanListNullable" to listOf(false, true, false, true),
          "booleanListOfNullable" to listOf(false, false, true, true),
          "stringList" to listOf("xxx", "yyy", "zzz"),
          "stringListNullable" to listOf("qqq", "rrr"),
          "stringListOfNullable" to listOf("sss", "ttt"),
        )
      )
  }

  @Test
  fun nullListsOfPrimitiveTypes() = runTest {
    allTypesSchema.createPrimitiveList.execute(
      AllTypesSchema.CreatePrimitiveListMutation.Variables(
        AllTypesSchema.PrimitiveListData(
          id = "abc123",
          idListNullable = null,
          idListOfNullable = listOf("ccc", "ddd"),
          intList = listOf(42, 43, 44),
          intListNullable = null,
          intListOfNullable = listOf(47, 48),
          floatList = listOf(12.3, 45.6, 78.9),
          floatListNullable = null,
          floatListOfNullable = listOf(100.1, 100.2),
          booleanList = listOf(true, false, true, false),
          booleanListNullable = null,
          booleanListOfNullable = listOf(false, false, true, true),
          stringList = listOf("xxx", "yyy", "zzz"),
          stringListNullable = null,
          stringListOfNullable = listOf("sss", "ttt"),
        )
      )
    )
    val query = allTypesSchema.getPrimitiveList.withDataDeserializer(DataConnectUntypedData)

    val result = query.execute(AllTypesSchema.GetPrimitiveListQuery.Variables(id = "abc123"))

    assertWithMessage("errors").that(result.data.errors).isEmpty()
    assertWithMessage("data").that(result.data.data).isNotNull()
    assertWithMessage("data.keys").that(result.data.data?.keys).containsExactly("primitiveList")
    assertWithMessage("data.keys[primitiveList]")
      .that(result.data.data?.get("primitiveList") as Map<*, *>)
      .containsExactlyEntriesIn(
        mapOf(
          "id" to "abc123",
          "idListNullable" to null,
          "idListOfNullable" to listOf("ccc", "ddd"),
          "intList" to listOf(42.0, 43.0, 44.0),
          "intListNullable" to null,
          "intListOfNullable" to listOf(47.0, 48.0),
          "floatList" to listOf(12.3, 45.6, 78.9),
          "floatListNullable" to null,
          "floatListOfNullable" to listOf(100.1, 100.2),
          "booleanList" to listOf(true, false, true, false),
          "booleanListNullable" to null,
          "booleanListOfNullable" to listOf(false, false, true, true),
          "stringList" to listOf("xxx", "yyy", "zzz"),
          "stringListNullable" to null,
          "stringListOfNullable" to listOf("sss", "ttt"),
        )
      )
  }

  @Test
  fun nestedStructs() = runTest {
    allTypesSchema.createFarmer(id = "Farmer1Id", name = "Farmer1Name", parentId = null)
    allTypesSchema.createFarmer(id = "Farmer2Id", name = "Farmer2Name", parentId = "Farmer1Id")
    allTypesSchema.createFarmer(id = "Farmer3Id", name = "Farmer3Name", parentId = "Farmer2Id")
    allTypesSchema.createFarmer(id = "Farmer4Id", name = "Farmer4Name", parentId = "Farmer3Id")
    allTypesSchema.createFarm(id = "FarmId", name = "TestFarm", farmerId = "Farmer4Id")
    allTypesSchema.createAnimal(
      id = "Animal1Id",
      farmId = "FarmId",
      name = "Animal1Name",
      species = "Animal1Species",
      age = 1
    )
    allTypesSchema.createAnimal(
      id = "Animal2Id",
      farmId = "FarmId",
      name = "Animal2Name",
      species = "Animal2Species",
      age = null
    )
    val query = allTypesSchema.getFarm.withDataDeserializer(DataConnectUntypedData)

    val result = query.execute(AllTypesSchema.GetFarmQuery.Variables(id = "FarmId"))

    assertWithMessage("errors").that(result.data.errors).isEmpty()
    assertWithMessage("data").that(result.data.data).isNotNull()
    assertWithMessage("data.keys").that(result.data.data?.keys).containsExactly("farm")
    val farm =
      result.data.data!!.get("farm").let {
        val farm = it as? Map<*, *>
        assertWithMessage("farm: $it").that(farm).isNotNull()
        farm!!
      }
    assertWithMessage("farm.keys")
      .that(farm.keys)
      .containsExactly("id", "name", "farmer", "animals")
    assertWithMessage("farm[id]").that(farm["id"]).isEqualTo("FarmId")
    assertWithMessage("farm[name]").that(farm["name"]).isEqualTo("TestFarm")
    val animals =
      farm["animals"].let {
        val animals = it as? List<*>
        assertWithMessage("animals: $it").that(animals).isNotNull()
        animals!!
      }
    assertWithMessage("farm[animals]")
      .that(animals)
      .containsExactly(
        mapOf(
          "id" to "Animal1Id",
          "name" to "Animal1Name",
          "species" to "Animal1Species",
          "age" to 1.0
        ),
        mapOf(
          "id" to "Animal2Id",
          "name" to "Animal2Name",
          "species" to "Animal2Species",
          "age" to null
        ),
      )
    val farmer =
      farm["farmer"].let {
        val farmer = it as? Map<*, *>
        assertWithMessage("farmer: $it").that(farmer).isNotNull()
        farmer!!
      }
    assertWithMessage("farmer.keys").that(farmer.keys).containsExactly("id", "name", "parent")
    assertWithMessage("farmer[id]").that(farmer["id"]).isEqualTo("Farmer4Id")
    assertWithMessage("farmer[name]").that(farmer["name"]).isEqualTo("Farmer4Name")
    val parent =
      farmer["parent"].let {
        val parent = it as? Map<*, *>
        assertWithMessage("parent: $it").that(parent).isNotNull()
        parent!!
      }
    assertWithMessage("parent.keys").that(parent.keys).containsExactly("id", "name", "parentId")
    assertWithMessage("parent[id]").that(parent["id"]).isEqualTo("Farmer3Id")
    assertWithMessage("parent[name]").that(parent["name"]).isEqualTo("Farmer3Name")
    assertWithMessage("parent[parentId]").that(parent["parentId"]).isEqualTo("Farmer2Id")
  }

  @Test
  fun nestedNullStructs() = runTest {
    allTypesSchema.createFarmer(id = "FarmerId", name = "FarmerName", parentId = null)
    allTypesSchema.createFarm(id = "FarmId", name = "TestFarm", farmerId = "FarmerId")
    val query = allTypesSchema.getFarm.withDataDeserializer(DataConnectUntypedData)

    val result = query.execute(AllTypesSchema.GetFarmQuery.Variables(id = "FarmId"))

    assertWithMessage("errors").that(result.data.errors).isEmpty()
    assertWithMessage("data").that(result.data.data).isNotNull()
    assertWithMessage("data.keys").that(result.data.data?.keys).containsExactly("farm")
    val farm =
      result.data.data!!.get("farm").let {
        val farm = it as? Map<*, *>
        assertWithMessage("farm: $it").that(farm).isNotNull()
        farm!!
      }
    assertWithMessage("farm.keys")
      .that(farm.keys)
      .containsExactly("id", "name", "farmer", "animals")
    val farmer =
      farm["farmer"].let {
        val farmer = it as? Map<*, *>
        assertWithMessage("farmer: $it").that(farmer).isNotNull()
        farmer!!
      }
    assertWithMessage("farmer.keys").that(farmer.keys).containsExactly("id", "name", "parent")
    assertWithMessage("farmer[id]").that(farmer["id"]).isEqualTo("FarmerId")
    assertWithMessage("farmer[name]").that(farmer["name"]).isEqualTo("FarmerName")
    assertWithMessage("farmer[parent]").that(farmer["parent"]).isNull()
  }

  @Test
  fun queryErrorsReturnedByServerArePutInTheErrorsListInsteadOfThrowingAnException() = runTest {
    @Serializable data class BogusVariables(val foo: String)
    val query =
      allTypesSchema.getPrimitive
        .withVariablesSerializer(serializer<BogusVariables>())
        .withDataDeserializer(DataConnectUntypedData)

    val result = query.execute(BogusVariables(foo = "bar"))

    assertWithMessage("result.data.data").that(result.data.data).isNull()
    assertWithMessage("result.data.errors").that(result.data.errors).hasSize(1)
  }

  @Test
  fun mutationErrorsReturnedByServerArePutInTheErrorsListInsteadOfThrowingAnException() = runTest {
    @Serializable data class BogusVariables(val foo: String)
    val mutation =
      allTypesSchema.createAnimal
        .withVariablesSerializer(serializer<BogusVariables>())
        .withDataDeserializer(DataConnectUntypedData)

    val result = mutation.execute(BogusVariables(foo = "bar"))

    assertWithMessage("result.data.data").that(result.data.data).isNull()
    assertWithMessage("result.data.errors").that(result.data.errors).hasSize(1)
  }
}
