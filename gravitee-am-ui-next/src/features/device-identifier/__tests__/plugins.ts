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
// The schemas and the catalog of the local stack. The Pro feature is a test value: no deployed plugin has one.
const COMMUNITY_SCHEMA = {
    type: 'object',
    id: 'urn:jsonschema:io:gravitee:am:deviceidentifier:fingerprintjs:FingerprintJsV3Community',
    properties: { useCookie: { type: 'boolean', title: 'Keep track of the deviceId in a cookie', default: false } },
};
const PRO_SCHEMA = {
    type: 'object',
    id: 'urn:jsonschema:io:gravitee:am:deviceidentifier:fingerprintjs:FingerprintJsV3Pro',
    properties: {
        browserToken: { title: 'Browser token', type: 'string', description: 'Browser subscription token' },
        region: {
            title: 'Registration region',
            type: 'string',
            description: '(optional) set the region of your FingerprintJS registration',
            placeholder: ['e.g: eu'],
        },
    },
    required: ['browserToken'],
};
const COOKIE_SCHEMA = { type: 'object', id: 'urn:jsonschema:io:gravitee:am:deviceidentifier:cookie', properties: {} };
export const SCHEMAS: Record<string, Record<string, unknown>> = {
    'fingerprintjs-v3-community-device-identifier': COMMUNITY_SCHEMA,
    'fingerprintjs-v3-pro-device-identifier': PRO_SCHEMA,
    'cookie-device-identifier': COOKIE_SCHEMA,
};
export const PLUGINS = [
    { id: 'cookie-device-identifier', name: 'Gravitee IO - Access Management - Device Identifier - Cookie' },
    {
        id: 'fingerprintjs-v3-community-device-identifier',
        name: 'Gravitee IO - Access Management - Device Identifier - FingerprintJS V3 Community',
    },
    {
        id: 'fingerprintjs-v3-pro-device-identifier',
        name: 'Gravitee IO - Access Management - Device Identifier - FingerprintJS V3 Pro',
        feature: 'am-device-identifier-premium',
    },
];
