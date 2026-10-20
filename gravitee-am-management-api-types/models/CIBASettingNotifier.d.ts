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
 * @interface CIBASettingNotifier
 */
export interface CIBASettingNotifier {
    /**
     *
     * @type {string}
     * @memberof CIBASettingNotifier
     */
    id?: string;
}
/**
 * Check if a given object implements the CIBASettingNotifier interface.
 */
export declare function instanceOfCIBASettingNotifier(value: object): value is CIBASettingNotifier;
export declare function CIBASettingNotifierFromJSON(json: any): CIBASettingNotifier;
export declare function CIBASettingNotifierFromJSONTyped(json: any, ignoreDiscriminator: boolean): CIBASettingNotifier;
export declare function CIBASettingNotifierToJSON(json: any): CIBASettingNotifier;
export declare function CIBASettingNotifierToJSONTyped(value?: CIBASettingNotifier | null, ignoreDiscriminator?: boolean): any;
