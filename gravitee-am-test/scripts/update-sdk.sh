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
    echo "usage: bash [-x] ./update-sdk.sh [HOST_URL] [SDK_OUTPUT_PATH]"
}

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

HOST_URL=$1
SDK_OUTPUT_PATH=$2

# Verify parameters
if [[ -z $HOST_URL ]]; then
  echo "\$HOST_URL must be set"
  usage
  exit 1
fi

if [[ -z $SDK_OUTPUT_PATH ]]; then
  echo "\$SDK_OUTPUT_PATH must be set"
  usage
  exit 2
fi

# Identify relative or absolute output path
SDK_OUTPUT_PATH=$(echo "$SDK_OUTPUT_PATH" | sed -r 's#/*$##g')
case $SDK_OUTPUT_PATH in
  /*) ;;
  *) SDK_OUTPUT_PATH="$SCRIPT_DIR/$SDK_OUTPUT_PATH";;
esac

echo "[INFO] output path will be: $SDK_OUTPUT_PATH"

npx @openapitools/openapi-generator-cli generate \
  -t "$SCRIPT_DIR/templates" \
  -i "$HOST_URL/openapi.json" \
  -g typescript-fetch \
  -o "$SDK_OUTPUT_PATH" \
  -puseSingleRequestParameter=true \
  -pmodelPropertyNaming=original \
  -psortModelPropertiesByRequiredFlag=false \
  -psortParamsByRequiredFlag=false \
  -plegacyDiscriminatorBehavior=false \
  --import-mappings=DateTime=Date \
  --type-mappings=DateTime=Date,object=any \
  --reserved-words-mappings=configuration=configuration \
  --skip-validate-spec

find "$SDK_OUTPUT_PATH" -name "*.ts" -exec sed -i.bak "/* The version of the OpenAPI document/d" {} \;
# must delete .bak files
find "$SDK_OUTPUT_PATH" -name "*.ts.bak" -exec rm -f {} \;
rm -f "$SDK_OUTPUT_PATH/index.ts"
rm -f "$SDK_OUTPUT_PATH/apis/index.ts"

# Format all generated TypeScript files with Prettier
echo "[INFO] Formatting generated files with Prettier..."
npx prettier --write "$SDK_OUTPUT_PATH/**/*.ts"
