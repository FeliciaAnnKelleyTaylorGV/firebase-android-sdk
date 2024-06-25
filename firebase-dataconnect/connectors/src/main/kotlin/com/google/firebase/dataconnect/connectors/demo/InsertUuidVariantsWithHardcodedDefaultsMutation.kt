@file:Suppress(
  "KotlinRedundantDiagnosticSuppress",
  "LocalVariableName",
  "RedundantVisibilityModifier",
  "RemoveEmptyClassBody",
  "SpellCheckingInspection",
  "LocalVariableName",
  "unused",
)
@file:UseSerializers(DateSerializer::class, UUIDSerializer::class, TimestampSerializer::class)

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.MutationResult
import com.google.firebase.dataconnect.generated.GeneratedMutation
import com.google.firebase.dataconnect.serializers.DateSerializer
import com.google.firebase.dataconnect.serializers.TimestampSerializer
import com.google.firebase.dataconnect.serializers.UUIDSerializer
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.serializer

public interface InsertUuidVariantsWithHardcodedDefaultsMutation :
  GeneratedMutation<DemoConnector, InsertUuidVariantsWithHardcodedDefaultsMutation.Data, Unit> {

  @Serializable
  public data class Data(@SerialName("uUIDVariants_insert") val key: UuidVariantsKey) {}

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "InsertUUIDVariantsWithHardcodedDefaults"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Unit> = serializer()
  }
}

public fun InsertUuidVariantsWithHardcodedDefaultsMutation.ref():
  MutationRef<InsertUuidVariantsWithHardcodedDefaultsMutation.Data, Unit> = ref(Unit)

public suspend fun InsertUuidVariantsWithHardcodedDefaultsMutation.execute():
  MutationResult<InsertUuidVariantsWithHardcodedDefaultsMutation.Data, Unit> = ref().execute()

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
