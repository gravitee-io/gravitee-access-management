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
 * OAuth-specific token-exchange behavior, such as how scopes are handled, with optional inheritance from domain defaults.
 * @export
 * @interface TokenExchangeOAuthSettings
 */
export interface TokenExchangeOAuthSettings {
    /**
     * Whether these settings are inherited from the domain defaults rather than defined here.
     * @type {boolean}
     * @memberof TokenExchangeOAuthSettings
     */
    inherited?: boolean;
    /**
     * How scopes are handled when issuing the exchanged token. DOWNSCOPING restricts the issued token to a subset of the original scopes.
     * @type {string}
     * @memberof TokenExchangeOAuthSettings
     */
    scopeHandling?: TokenExchangeOAuthSettingsScopeHandlingEnum;
}
/**
 * @export
 */
export declare const TokenExchangeOAuthSettingsScopeHandlingEnum: {
    readonly Downscoping: "DOWNSCOPING";
    readonly Permissive: "PERMISSIVE";
};
export type TokenExchangeOAuthSettingsScopeHandlingEnum = typeof TokenExchangeOAuthSettingsScopeHandlingEnum[keyof typeof TokenExchangeOAuthSettingsScopeHandlingEnum];
/**
 * Check if a given object implements the TokenExchangeOAuthSettings interface.
 */
export declare function instanceOfTokenExchangeOAuthSettings(value: object): value is TokenExchangeOAuthSettings;
export declare function TokenExchangeOAuthSettingsFromJSON(json: any): TokenExchangeOAuthSettings;
export declare function TokenExchangeOAuthSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): TokenExchangeOAuthSettings;
export declare function TokenExchangeOAuthSettingsToJSON(json: any): TokenExchangeOAuthSettings;
export declare function TokenExchangeOAuthSettingsToJSONTyped(value?: TokenExchangeOAuthSettings | null, ignoreDiscriminator?: boolean): any;
