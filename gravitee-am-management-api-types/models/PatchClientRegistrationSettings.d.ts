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
 * @interface PatchClientRegistrationSettings
 */
export interface PatchClientRegistrationSettings {
    /**
     *
     * @type {boolean}
     * @memberof PatchClientRegistrationSettings
     */
    allowHttpSchemeRedirectUri?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchClientRegistrationSettings
     */
    allowLocalhostRedirectUri?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchClientRegistrationSettings
     */
    allowRedirectUriParamsExpressionLanguage?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchClientRegistrationSettings
     */
    allowWildCardRedirectUri?: boolean;
    /**
     *
     * @type {Array<string>}
     * @memberof PatchClientRegistrationSettings
     */
    allowedScopes?: Array<string>;
    /**
     *
     * @type {boolean}
     * @memberof PatchClientRegistrationSettings
     */
    clientTemplateEnabled?: boolean;
    /**
     *
     * @type {Array<string>}
     * @memberof PatchClientRegistrationSettings
     */
    defaultScopes?: Array<string>;
    /**
     *
     * @type {boolean}
     * @memberof PatchClientRegistrationSettings
     */
    dynamicClientRegistrationEnabled?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchClientRegistrationSettings
     */
    isAllowedScopesEnabled?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchClientRegistrationSettings
     */
    isClientTemplateEnabled?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchClientRegistrationSettings
     */
    isDynamicClientRegistrationEnabled?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchClientRegistrationSettings
     */
    isOpenDynamicClientRegistrationEnabled?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchClientRegistrationSettings
     */
    openDynamicClientRegistrationEnabled?: boolean;
}
/**
 * Check if a given object implements the PatchClientRegistrationSettings interface.
 */
export declare function instanceOfPatchClientRegistrationSettings(value: object): value is PatchClientRegistrationSettings;
export declare function PatchClientRegistrationSettingsFromJSON(json: any): PatchClientRegistrationSettings;
export declare function PatchClientRegistrationSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchClientRegistrationSettings;
export declare function PatchClientRegistrationSettingsToJSON(json: any): PatchClientRegistrationSettings;
export declare function PatchClientRegistrationSettingsToJSONTyped(value?: PatchClientRegistrationSettings | null, ignoreDiscriminator?: boolean): any;
