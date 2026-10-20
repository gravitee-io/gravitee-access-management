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
 * Controls whether the domain's pages may be embedded in frames on other origins.
 * @export
 * @interface XFrameSettings
 */
export interface XFrameSettings {
    /**
     * X-Frame-Options action. Supported values: DENY, SAMEORIGIN. Leave empty to omit the header.
     * @type {string}
     * @memberof XFrameSettings
     */
    action?: string;
    /**
     * Whether X-Frame-Options is enabled for the domain when not inherited.
     * @type {boolean}
     * @memberof XFrameSettings
     */
    enabled?: boolean;
    /**
     * Whether X-Frame-Options settings are inherited from the gateway defaults (gravitee.yml). When null, legacy behaviour applies: enabled=true overrides and enabled=false inherits.
     * @type {boolean}
     * @memberof XFrameSettings
     */
    inherited?: boolean;
}
/**
 * Check if a given object implements the XFrameSettings interface.
 */
export declare function instanceOfXFrameSettings(value: object): value is XFrameSettings;
export declare function XFrameSettingsFromJSON(json: any): XFrameSettings;
export declare function XFrameSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): XFrameSettings;
export declare function XFrameSettingsToJSON(json: any): XFrameSettings;
export declare function XFrameSettingsToJSONTyped(value?: XFrameSettings | null, ignoreDiscriminator?: boolean): any;
