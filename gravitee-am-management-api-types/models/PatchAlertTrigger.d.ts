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
 * @interface PatchAlertTrigger
 */
export interface PatchAlertTrigger {
    /**
     *
     * @type {Array<string>}
     * @memberof PatchAlertTrigger
     */
    alertNotifiers?: Array<string>;
    /**
     *
     * @type {boolean}
     * @memberof PatchAlertTrigger
     */
    enabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof PatchAlertTrigger
     */
    type: PatchAlertTriggerTypeEnum;
}
/**
 * @export
 */
export declare const PatchAlertTriggerTypeEnum: {
    readonly TooManyLoginFailures: "TOO_MANY_LOGIN_FAILURES";
    readonly RiskAssessment: "RISK_ASSESSMENT";
};
export type PatchAlertTriggerTypeEnum = typeof PatchAlertTriggerTypeEnum[keyof typeof PatchAlertTriggerTypeEnum];
/**
 * Check if a given object implements the PatchAlertTrigger interface.
 */
export declare function instanceOfPatchAlertTrigger(value: object): value is PatchAlertTrigger;
export declare function PatchAlertTriggerFromJSON(json: any): PatchAlertTrigger;
export declare function PatchAlertTriggerFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchAlertTrigger;
export declare function PatchAlertTriggerToJSON(json: any): PatchAlertTrigger;
export declare function PatchAlertTriggerToJSONTyped(value?: PatchAlertTrigger | null, ignoreDiscriminator?: boolean): any;
