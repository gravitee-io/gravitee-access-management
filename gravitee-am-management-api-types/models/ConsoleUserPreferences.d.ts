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
 * @interface ConsoleUserPreferences
 */
export interface ConsoleUserPreferences {
    /**
     *
     * @type {string}
     * @memberof ConsoleUserPreferences
     */
    defaultDomainId?: string;
    /**
     *
     * @type {string}
     * @memberof ConsoleUserPreferences
     */
    defaultEnvironmentId?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof ConsoleUserPreferences
     */
    pinnedDomainIds?: Array<string>;
}
/**
 * Check if a given object implements the ConsoleUserPreferences interface.
 */
export declare function instanceOfConsoleUserPreferences(value: object): value is ConsoleUserPreferences;
export declare function ConsoleUserPreferencesFromJSON(json: any): ConsoleUserPreferences;
export declare function ConsoleUserPreferencesFromJSONTyped(json: any, ignoreDiscriminator: boolean): ConsoleUserPreferences;
export declare function ConsoleUserPreferencesToJSON(json: any): ConsoleUserPreferences;
export declare function ConsoleUserPreferencesToJSONTyped(value?: ConsoleUserPreferences | null, ignoreDiscriminator?: boolean): any;
