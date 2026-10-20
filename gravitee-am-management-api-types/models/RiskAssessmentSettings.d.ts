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
import type { AssessmentSettings } from './AssessmentSettings';
/**
 *
 * @export
 * @interface RiskAssessmentSettings
 */
export interface RiskAssessmentSettings {
    /**
     *
     * @type {AssessmentSettings}
     * @memberof RiskAssessmentSettings
     */
    deviceAssessment?: AssessmentSettings;
    /**
     *
     * @type {boolean}
     * @memberof RiskAssessmentSettings
     */
    enabled?: boolean;
    /**
     *
     * @type {AssessmentSettings}
     * @memberof RiskAssessmentSettings
     */
    geoVelocityAssessment?: AssessmentSettings;
    /**
     *
     * @type {AssessmentSettings}
     * @memberof RiskAssessmentSettings
     */
    ipReputationAssessment?: AssessmentSettings;
}
/**
 * Check if a given object implements the RiskAssessmentSettings interface.
 */
export declare function instanceOfRiskAssessmentSettings(value: object): value is RiskAssessmentSettings;
export declare function RiskAssessmentSettingsFromJSON(json: any): RiskAssessmentSettings;
export declare function RiskAssessmentSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): RiskAssessmentSettings;
export declare function RiskAssessmentSettingsToJSON(json: any): RiskAssessmentSettings;
export declare function RiskAssessmentSettingsToJSONTyped(value?: RiskAssessmentSettings | null, ignoreDiscriminator?: boolean): any;
