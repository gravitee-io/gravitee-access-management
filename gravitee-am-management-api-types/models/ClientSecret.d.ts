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
 *
 * @export
 * @interface ClientSecret
 */
export interface ClientSecret {
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof ClientSecret
     */
    createdAt?: number;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof ClientSecret
     */
    expiresAt?: number;
    /**
     *
     * @type {string}
     * @memberof ClientSecret
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof ClientSecret
     */
    name?: string;
    /**
     *
     * @type {string}
     * @memberof ClientSecret
     */
    secret?: string;
    /**
     *
     * @type {string}
     * @memberof ClientSecret
     */
    settingsId?: string;
}
/**
 * Check if a given object implements the ClientSecret interface.
 */
export declare function instanceOfClientSecret(value: object): value is ClientSecret;
export declare function ClientSecretFromJSON(json: any): ClientSecret;
export declare function ClientSecretFromJSONTyped(json: any, ignoreDiscriminator: boolean): ClientSecret;
export declare function ClientSecretToJSON(json: any): ClientSecret;
export declare function ClientSecretToJSONTyped(value?: ClientSecret | null, ignoreDiscriminator?: boolean): any;
