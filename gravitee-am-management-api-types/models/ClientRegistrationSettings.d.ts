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
 * OpenID Connect Dynamic Client Registration configuration for the domain.
 * @export
 * @interface ClientRegistrationSettings
 */
export interface ClientRegistrationSettings {
    /**
     * Whether the unsecured http scheme is permitted in redirect URIs.
     * @type {boolean}
     * @memberof ClientRegistrationSettings
     */
    allowHttpSchemeRedirectUri?: boolean;
    /**
     * Whether localhost is permitted as a redirect URI host.
     * @type {boolean}
     * @memberof ClientRegistrationSettings
     */
    allowLocalhostRedirectUri?: boolean;
    /**
     * Whether expression language is permitted in redirect URI parameters.
     * @type {boolean}
     * @memberof ClientRegistrationSettings
     */
    allowRedirectUriParamsExpressionLanguage?: boolean;
    /**
     * Whether wildcards are permitted in redirect URIs.
     * @type {boolean}
     * @memberof ClientRegistrationSettings
     */
    allowWildCardRedirectUri?: boolean;
    /**
     * Scopes permitted on client registration requests when the allowed list is enabled.
     * @type {Array<string>}
     * @memberof ClientRegistrationSettings
     */
    allowedScopes?: Array<string>;
    /**
     *
     * @type {boolean}
     * @memberof ClientRegistrationSettings
     */
    allowedScopesEnabled?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof ClientRegistrationSettings
     */
    clientTemplateEnabled?: boolean;
    /**
     * Default scopes added to every client registration request.
     * @type {Array<string>}
     * @memberof ClientRegistrationSettings
     */
    defaultScopes?: Array<string>;
    /**
     *
     * @type {boolean}
     * @memberof ClientRegistrationSettings
     */
    dynamicClientRegistrationEnabled?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof ClientRegistrationSettings
     */
    openDynamicClientRegistrationEnabled?: boolean;
}
/**
 * Check if a given object implements the ClientRegistrationSettings interface.
 */
export declare function instanceOfClientRegistrationSettings(value: object): value is ClientRegistrationSettings;
export declare function ClientRegistrationSettingsFromJSON(json: any): ClientRegistrationSettings;
export declare function ClientRegistrationSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): ClientRegistrationSettings;
export declare function ClientRegistrationSettingsToJSON(json: any): ClientRegistrationSettings;
export declare function ClientRegistrationSettingsToJSONTyped(value?: ClientRegistrationSettings | null, ignoreDiscriminator?: boolean): any;
