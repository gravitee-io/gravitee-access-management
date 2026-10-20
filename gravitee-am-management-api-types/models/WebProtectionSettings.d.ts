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
import type { CspSettings } from './CspSettings';
import type { XssProtectionSettings } from './XssProtectionSettings';
import type { XFrameSettings } from './XFrameSettings';
/**
 * HTTP security headers applied to the domain's login and consent pages.
 * @export
 * @interface WebProtectionSettings
 */
export interface WebProtectionSettings {
    /**
     *
     * @type {CspSettings}
     * @memberof WebProtectionSettings
     */
    csp?: CspSettings;
    /**
     *
     * @type {XFrameSettings}
     * @memberof WebProtectionSettings
     */
    xframe?: XFrameSettings;
    /**
     *
     * @type {XssProtectionSettings}
     * @memberof WebProtectionSettings
     */
    xss?: XssProtectionSettings;
}
/**
 * Check if a given object implements the WebProtectionSettings interface.
 */
export declare function instanceOfWebProtectionSettings(value: object): value is WebProtectionSettings;
export declare function WebProtectionSettingsFromJSON(json: any): WebProtectionSettings;
export declare function WebProtectionSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): WebProtectionSettings;
export declare function WebProtectionSettingsToJSON(json: any): WebProtectionSettings;
export declare function WebProtectionSettingsToJSONTyped(value?: WebProtectionSettings | null, ignoreDiscriminator?: boolean): any;
