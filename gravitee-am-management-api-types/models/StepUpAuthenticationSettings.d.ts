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
 * @interface StepUpAuthenticationSettings
 */
export interface StepUpAuthenticationSettings {
    /**
     *
     * @type {boolean}
     * @memberof StepUpAuthenticationSettings
     */
    active?: boolean;
    /**
     *
     * @type {string}
     * @memberof StepUpAuthenticationSettings
     */
    stepUpAuthenticationRule?: string;
}
/**
 * Check if a given object implements the StepUpAuthenticationSettings interface.
 */
export declare function instanceOfStepUpAuthenticationSettings(value: object): value is StepUpAuthenticationSettings;
export declare function StepUpAuthenticationSettingsFromJSON(json: any): StepUpAuthenticationSettings;
export declare function StepUpAuthenticationSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): StepUpAuthenticationSettings;
export declare function StepUpAuthenticationSettingsToJSON(json: any): StepUpAuthenticationSettings;
export declare function StepUpAuthenticationSettingsToJSONTyped(value?: StepUpAuthenticationSettings | null, ignoreDiscriminator?: boolean): any;
