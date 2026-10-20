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
 * Cross-Origin Resource Sharing configuration controlling which web origins may call the domain's endpoints from a browser.
 * @export
 * @interface CorsSettings
 */
export interface CorsSettings {
    /**
     * Whether the browser may send credentials (cookies, authorization headers) with cross-origin requests.
     * @type {boolean}
     * @memberof CorsSettings
     */
    allowCredentials?: boolean;
    /**
     * Request headers permitted on cross-origin requests.
     * @type {Array<string>}
     * @memberof CorsSettings
     */
    allowedHeaders?: Array<string>;
    /**
     * HTTP methods permitted on cross-origin requests.
     * @type {Array<string>}
     * @memberof CorsSettings
     */
    allowedMethods?: Array<string>;
    /**
     * Origins permitted to make cross-origin requests. Use "*" to allow any origin.
     * @type {Array<string>}
     * @memberof CorsSettings
     */
    allowedOrigins?: Array<string>;
    /**
     * Whether CORS handling is enabled for the domain when not inherited.
     * @type {boolean}
     * @memberof CorsSettings
     */
    enabled?: boolean;
    /**
     * Whether CORS settings are inherited from the gateway defaults (gravitee.yml). When null, legacy behaviour applies: enabled=true overrides and enabled=false inherits.
     * @type {boolean}
     * @memberof CorsSettings
     */
    inherited?: boolean;
    /**
     * How long, in seconds, a browser may cache the result of a preflight request.
     * @type {number}
     * @memberof CorsSettings
     */
    maxAge?: number;
}
/**
 * Check if a given object implements the CorsSettings interface.
 */
export declare function instanceOfCorsSettings(value: object): value is CorsSettings;
export declare function CorsSettingsFromJSON(json: any): CorsSettings;
export declare function CorsSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): CorsSettings;
export declare function CorsSettingsToJSON(json: any): CorsSettings;
export declare function CorsSettingsToJSONTyped(value?: CorsSettings | null, ignoreDiscriminator?: boolean): any;
