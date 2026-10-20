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
 * ID-JAG issuance behavior of token exchange.
 * @export
 * @interface IdJagSettings
 */
export interface IdJagSettings {
    /**
     * Lax validation: also accept an access token issued to the requesting client as the subject token. By default only an ID token is accepted.
     * @type {boolean}
     * @memberof IdJagSettings
     */
    laxValidation?: boolean;
}
/**
 * Check if a given object implements the IdJagSettings interface.
 */
export declare function instanceOfIdJagSettings(value: object): value is IdJagSettings;
export declare function IdJagSettingsFromJSON(json: any): IdJagSettings;
export declare function IdJagSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): IdJagSettings;
export declare function IdJagSettingsToJSON(json: any): IdJagSettings;
export declare function IdJagSettingsToJSONTyped(value?: IdJagSettings | null, ignoreDiscriminator?: boolean): any;
