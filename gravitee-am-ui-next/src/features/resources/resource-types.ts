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
/** The Angular `displayType` names of the built-in resource plugins. */
const TYPE_NAMES: Record<string, string> = {
    'twilio-verify-am-resource': 'Twilio Verify',
    'smtp-am-resource': 'SMTP',
    'infobip-am-resource': 'Infobip 2FA',
    'http-factor-am-resource': 'HTTP Factor',
};

/** The display name of a resource type, else the fallback: the type id in the list, the plugin name in the wizard. */
export function resourceTypeName(type: string, fallback = type): string {
    return TYPE_NAMES[type] ?? fallback;
}

export function isHttpFactorResource(type: string): boolean {
    return type.includes('http-factor-am-resource');
}
