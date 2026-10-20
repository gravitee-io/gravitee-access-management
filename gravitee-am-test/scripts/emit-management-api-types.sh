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

# Writes .d.ts files of the generated Management API models to gravitee-am-management-api-types/.
# gravitee-am-ui-next imports them as types. It cannot import the .ts models, because their
# FromJSON/ToJSON code fails its noUnusedLocals check, and skipLibCheck skips only .d.ts files.

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
MANAGEMENT_SDK_PATH="$SCRIPT_DIR/../api/management"
TYPES_PATH="$SCRIPT_DIR/../../gravitee-am-management-api-types"

rm -rf "${TYPES_PATH:?}"/*

# The generated models have type errors of their own, so tsc exits non-zero but still writes every file
"$SCRIPT_DIR/../node_modules/.bin/tsc" \
  --declaration \
  --emitDeclarationOnly \
  --skipLibCheck \
  --target es2020 \
  --lib es2020,dom \
  --module esnext \
  --moduleResolution bundler \
  --rootDir "$MANAGEMENT_SDK_PATH" \
  --outDir "$TYPES_PATH" \
  "$MANAGEMENT_SDK_PATH/models/index.ts" > /dev/null

if ! ls "$TYPES_PATH"/models/*.d.ts >/dev/null 2>&1; then
  echo "[ERROR] No type declarations were written to $TYPES_PATH"
  exit 1
fi

# tsc drops the licence header, so copy it back from the generated runtime.
# The console reads raw JSON without FromJSON, so a uniqueItems array stays an array, not a Set.
HEADER=$(sed -n '1,/^ \*\//p' "$MANAGEMENT_SDK_PATH/runtime.ts")
find "$TYPES_PATH" -name "*.d.ts" | while read -r FILE; do
  { echo "$HEADER"; sed 's/\([^A-Za-z]\)Set</\1Array</g' "$FILE"; } > "$FILE.tmp" && mv "$FILE.tmp" "$FILE"
done

echo "[INFO] Wrote the model types to $TYPES_PATH"
