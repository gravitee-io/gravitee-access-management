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
import type { CrossAppAccessResourceServer } from './CrossAppAccessResourceServer';
/**
 * Outbound configuration for minting ID-JAGs towards this authority. The ID-JAG is presented to the authorization server named by the trusted domain's domainIdentifier, which the "aud" claim carries.
 * @export
 * @interface CrossAppAccessSettings
 */
export interface CrossAppAccessSettings {
    /**
     * Expression producing the optional "aud_sub" claim, with the user profile available as context.attributes['user']. The claim is omitted when the expression yields nothing, and issuance is refused when the expression fails.
     * @type {string}
     * @memberof CrossAppAccessSettings
     */
    audSubMapping?: string;
    /**
     * Whether AM may mint an ID-JAG towards this authority.
     * @type {boolean}
     * @memberof CrossAppAccessSettings
     */
    enabled?: boolean;
    /**
     * Resource servers this authority exposes behind that authorization server.
     * @type {Array<CrossAppAccessResourceServer>}
     * @memberof CrossAppAccessSettings
     */
    resourceServers?: Array<CrossAppAccessResourceServer>;
    /**
     * One-to-one mapping from domain scope to the name this authority knows it by. A requested domain scope with no mapping is refused.
     * @type {{ [key: string]: string; }}
     * @memberof CrossAppAccessSettings
     */
    scopeMappings?: {
        [key: string]: string;
    };
}
/**
 * Check if a given object implements the CrossAppAccessSettings interface.
 */
export declare function instanceOfCrossAppAccessSettings(value: object): value is CrossAppAccessSettings;
export declare function CrossAppAccessSettingsFromJSON(json: any): CrossAppAccessSettings;
export declare function CrossAppAccessSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): CrossAppAccessSettings;
export declare function CrossAppAccessSettingsToJSON(json: any): CrossAppAccessSettings;
export declare function CrossAppAccessSettingsToJSONTyped(value?: CrossAppAccessSettings | null, ignoreDiscriminator?: boolean): any;
