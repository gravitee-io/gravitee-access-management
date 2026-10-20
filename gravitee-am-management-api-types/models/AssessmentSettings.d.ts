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
 * @interface AssessmentSettings
 */
export interface AssessmentSettings {
    /**
     *
     * @type {boolean}
     * @memberof AssessmentSettings
     */
    enabled?: boolean;
    /**
     *
     * @type {{ [key: string]: number; }}
     * @memberof AssessmentSettings
     */
    thresholds?: {
        [key: string]: number;
    };
}
/**
 * Check if a given object implements the AssessmentSettings interface.
 */
export declare function instanceOfAssessmentSettings(value: object): value is AssessmentSettings;
export declare function AssessmentSettingsFromJSON(json: any): AssessmentSettings;
export declare function AssessmentSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): AssessmentSettings;
export declare function AssessmentSettingsToJSON(json: any): AssessmentSettings;
export declare function AssessmentSettingsToJSONTyped(value?: AssessmentSettings | null, ignoreDiscriminator?: boolean): any;
