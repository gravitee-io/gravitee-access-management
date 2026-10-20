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
 * @interface ApplicationScopeSettings
 */
export interface ApplicationScopeSettings {
    /**
     *
     * @type {boolean}
     * @memberof ApplicationScopeSettings
     */
    defaultScope?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof ApplicationScopeSettings
     */
    requiredScope?: boolean;
    /**
     *
     * @type {string}
     * @memberof ApplicationScopeSettings
     */
    scope?: string;
    /**
     *
     * @type {number}
     * @memberof ApplicationScopeSettings
     */
    scopeApproval?: number;
}
/**
 * Check if a given object implements the ApplicationScopeSettings interface.
 */
export declare function instanceOfApplicationScopeSettings(value: object): value is ApplicationScopeSettings;
export declare function ApplicationScopeSettingsFromJSON(json: any): ApplicationScopeSettings;
export declare function ApplicationScopeSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): ApplicationScopeSettings;
export declare function ApplicationScopeSettingsToJSON(json: any): ApplicationScopeSettings;
export declare function ApplicationScopeSettingsToJSONTyped(value?: ApplicationScopeSettings | null, ignoreDiscriminator?: boolean): any;
