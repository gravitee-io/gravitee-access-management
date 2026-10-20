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
import type { SessionSettings } from './SessionSettings';
/**
 *
 * @export
 * @interface CookieSettings
 */
export interface CookieSettings {
    /**
     *
     * @type {boolean}
     * @memberof CookieSettings
     */
    inherited?: boolean;
    /**
     *
     * @type {SessionSettings}
     * @memberof CookieSettings
     */
    session?: SessionSettings;
}
/**
 * Check if a given object implements the CookieSettings interface.
 */
export declare function instanceOfCookieSettings(value: object): value is CookieSettings;
export declare function CookieSettingsFromJSON(json: any): CookieSettings;
export declare function CookieSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): CookieSettings;
export declare function CookieSettingsToJSON(json: any): CookieSettings;
export declare function CookieSettingsToJSONTyped(value?: CookieSettings | null, ignoreDiscriminator?: boolean): any;
