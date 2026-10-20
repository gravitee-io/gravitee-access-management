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
 * @interface SessionSettings
 */
export interface SessionSettings {
    /**
     *
     * @type {boolean}
     * @memberof SessionSettings
     */
    persistent?: boolean;
}
/**
 * Check if a given object implements the SessionSettings interface.
 */
export declare function instanceOfSessionSettings(value: object): value is SessionSettings;
export declare function SessionSettingsFromJSON(json: any): SessionSettings;
export declare function SessionSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): SessionSettings;
export declare function SessionSettingsToJSON(json: any): SessionSettings;
export declare function SessionSettingsToJSONTyped(value?: SessionSettings | null, ignoreDiscriminator?: boolean): any;
