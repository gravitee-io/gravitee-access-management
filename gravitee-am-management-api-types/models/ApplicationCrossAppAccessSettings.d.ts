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
import type { ApplicationCrossAppAccessResourceServer } from './ApplicationCrossAppAccessResourceServer';
/**
 * Resource servers this application may request an ID-JAG for.
 * @export
 * @interface ApplicationCrossAppAccessSettings
 */
export interface ApplicationCrossAppAccessSettings {
    /**
     * Whether this application participates in Cross App Access.
     * @type {boolean}
     * @memberof ApplicationCrossAppAccessSettings
     */
    enabled?: boolean;
    /**
     * Resource servers this application may reach, one entry per resource server.
     * @type {Array<ApplicationCrossAppAccessResourceServer>}
     * @memberof ApplicationCrossAppAccessSettings
     */
    resourceServers?: Array<ApplicationCrossAppAccessResourceServer>;
}
/**
 * Check if a given object implements the ApplicationCrossAppAccessSettings interface.
 */
export declare function instanceOfApplicationCrossAppAccessSettings(value: object): value is ApplicationCrossAppAccessSettings;
export declare function ApplicationCrossAppAccessSettingsFromJSON(json: any): ApplicationCrossAppAccessSettings;
export declare function ApplicationCrossAppAccessSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): ApplicationCrossAppAccessSettings;
export declare function ApplicationCrossAppAccessSettingsToJSON(json: any): ApplicationCrossAppAccessSettings;
export declare function ApplicationCrossAppAccessSettingsToJSONTyped(value?: ApplicationCrossAppAccessSettings | null, ignoreDiscriminator?: boolean): any;
