#!/bin/bash

# Copyright 2024 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

readonly CLI_ARGS=(
  ./cli
  -alsologtostderr=1
  -stderrthreshold=0
  -log_dir=logs
  dev
  -disable_sdk_generation=true
  -service_location=us-central1
  -config_dir=dataconnect
  -local_connection_string='postgresql://postgres:postgres@localhost:5432?sslmode=disable'
)

echo "[$0] Running command: ${CLI_ARGS[*]}"
exec "${CLI_ARGS[@]}"
