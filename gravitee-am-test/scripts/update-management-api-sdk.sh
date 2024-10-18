#!/bin/bash
#
# Copyright (C) 2015 The Gravitee team (http://gravitee.io)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

function usage () {
    echo "usage: bash [-x] ./update-management-api-sdk.sh [MANAGEMENT_API]"
}

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

MANAGEMENT_API=$1

# Verify parameters
if [[ -z $MANAGEMENT_API ]]; then
  echo "\MANAGEMENT_API must be set"
  usage
  exit 1
fi

# We create the management SDK path
MANAGEMENT_SDK_PATH=$(echo "MANAGEMENT_SDK_PATH" | sed -r 's#/*$##g')
MANAGEMENT_SDK_PATH="$SCRIPT_DIR/../api/management"

# Delete everything in MANAGEMENT_SDK_PATH
rm -r "${MANAGEMENT_SDK_PATH:?}/*"

bash "$SCRIPT_DIR"/update-sdk.sh "$MANAGEMENT_API" "$MANAGEMENT_SDK_PATH"
UPDATE_SDK_STATUS=$?

if [[ $UPDATE_SDK_STATUS -ne 0 ]]; then
  echo "Fail creating the SDK for AM management-api"
  usage
  exit $UPDATE_SDK_STATUS
fi

exit 0
