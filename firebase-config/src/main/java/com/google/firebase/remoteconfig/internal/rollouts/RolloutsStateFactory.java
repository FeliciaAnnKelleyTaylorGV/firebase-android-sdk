/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.remoteconfig.internal.rollouts;

import androidx.annotation.NonNull;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigClientException;
import com.google.firebase.remoteconfig.internal.ConfigGetParameterHandler;
import com.google.firebase.remoteconfig.interop.rollouts.RolloutAssignment;
import com.google.firebase.remoteconfig.interop.rollouts.RolloutsState;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class RolloutsStateFactory {
  Executor executor;
  ConfigGetParameterHandler getParameterHandler;

  private static final String ROLLOUT_ID_KEY = "rollout_id";
  private static final String VARIANT_ID_KEY = "variant_id";
  private static final String AFFECTED_PARAMETER_KEYS_KEY = "affected_parameter_keys";
  private static final String TEMPLATE_VERSION_KEY = "template_version";

  RolloutsStateFactory(ConfigGetParameterHandler getParameterHandler, Executor executor) {
    this.executor = executor;
    this.getParameterHandler = getParameterHandler;
  }

  @NonNull
  RolloutsState getActiveRolloutsState(@NonNull JSONArray rolloutsMetadata)
      throws FirebaseRemoteConfigClientException {

    Set<RolloutAssignment> rolloutAssignments = new HashSet<>();
    for (int i = 0; i < rolloutsMetadata.length(); i++) {
      try {
        JSONObject rollout = rolloutsMetadata.getJSONObject(i);
        JSONArray affectedParameterKeys = rollout.getJSONArray(AFFECTED_PARAMETER_KEYS_KEY);

        for (int j = 0; j < affectedParameterKeys.length(); j++) {
          String parameterKey = affectedParameterKeys.getString(j);
          String parameterValue = getParameterHandler.getString(parameterKey);

          rolloutAssignments.add(
              RolloutAssignment.builder()
                  .setRolloutId(rollout.getString(ROLLOUT_ID_KEY))
                  .setVariantId(rollout.getString(VARIANT_ID_KEY))
                  .setParameterKey(parameterKey)
                  .setParameterValue(parameterValue)
                  .setTemplateVersion(rollout.getLong(TEMPLATE_VERSION_KEY))
                  .build());
        }
      } catch (JSONException e) {
        throw new FirebaseRemoteConfigClientException(
            "Exception parsing rollouts metadata to create RolloutsState.", e);
      }
    }

    return RolloutsState.create(rolloutAssignments);
  }

  @NonNull
  public static RolloutsStateFactory create(
      @NonNull ConfigGetParameterHandler configGetParameterHandler, @NonNull Executor executor) {
    return new RolloutsStateFactory(configGetParameterHandler, executor);
  }
}
