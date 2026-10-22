/*
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/** The Angular `deviceIdentifierTypes` map of the list, step 1, step 2 and the detail page. */
const TYPE_NAMES: Record<string, string> = {
    'fingerprintjs-v3-community-device-identifier': 'FingerprintJS v3 community',
    'fingerprintjs-v3-pro-device-identifier': 'FingerprintJS v3 Pro',
};

/** The Angular `displayType()`. Another plugin shows the fallback: its id, or its catalog name on step 1. */
export function deviceIdentifierTypeName(type: string, fallback = type): string {
    return TYPE_NAMES[type] ?? fallback;
}

/** True when the plugin schema has no property, as the Angular form checks before it shows "No configuration required." */
export function hasNoSettings(schema: Record<string, unknown>): boolean {
    return Object.keys((schema.properties as Record<string, unknown> | undefined) ?? {}).length === 0;
}
