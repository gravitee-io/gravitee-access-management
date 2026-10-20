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
/**
 * Controls whether client secrets in the domain expire and after how long.
 * @export
 * @interface SecretExpirationSettings
 */
export interface SecretExpirationSettings {
    /**
     * Whether client-secret expiration is enabled.
     * @type {boolean}
     * @memberof SecretExpirationSettings
     */
    enabled?: boolean;
    /**
     * Lifetime, in seconds, of a client secret before it expires.
     * @type {number}
     * @memberof SecretExpirationSettings
     */
    expiryTimeSeconds?: number;
}
/**
 * Check if a given object implements the SecretExpirationSettings interface.
 */
export declare function instanceOfSecretExpirationSettings(value: object): value is SecretExpirationSettings;
export declare function SecretExpirationSettingsFromJSON(json: any): SecretExpirationSettings;
export declare function SecretExpirationSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): SecretExpirationSettings;
export declare function SecretExpirationSettingsToJSON(json: any): SecretExpirationSettings;
export declare function SecretExpirationSettingsToJSONTyped(value?: SecretExpirationSettings | null, ignoreDiscriminator?: boolean): any;
