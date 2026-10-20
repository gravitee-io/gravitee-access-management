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
 * @interface PatchChallengeSettings
 */
export interface PatchChallengeSettings {
    /**
     *
     * @type {boolean}
     * @memberof PatchChallengeSettings
     */
    active?: boolean;
    /**
     *
     * @type {string}
     * @memberof PatchChallengeSettings
     */
    challengeRule?: string;
    /**
     *
     * @type {string}
     * @memberof PatchChallengeSettings
     */
    type?: PatchChallengeSettingsTypeEnum;
}
/**
 * @export
 */
export declare const PatchChallengeSettingsTypeEnum: {
    readonly Required: "REQUIRED";
    readonly Conditional: "CONDITIONAL";
    readonly RiskBased: "RISK_BASED";
};
export type PatchChallengeSettingsTypeEnum = typeof PatchChallengeSettingsTypeEnum[keyof typeof PatchChallengeSettingsTypeEnum];
/**
 * Check if a given object implements the PatchChallengeSettings interface.
 */
export declare function instanceOfPatchChallengeSettings(value: object): value is PatchChallengeSettings;
export declare function PatchChallengeSettingsFromJSON(json: any): PatchChallengeSettings;
export declare function PatchChallengeSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchChallengeSettings;
export declare function PatchChallengeSettingsToJSON(json: any): PatchChallengeSettings;
export declare function PatchChallengeSettingsToJSONTyped(value?: PatchChallengeSettings | null, ignoreDiscriminator?: boolean): any;
