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
 * @interface AlertTrigger
 */
export interface AlertTrigger {
    /**
     *
     * @type {Array<string>}
     * @memberof AlertTrigger
     */
    alertNotifiers?: Array<string>;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof AlertTrigger
     */
    createdAt?: number;
    /**
     *
     * @type {boolean}
     * @memberof AlertTrigger
     */
    enabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof AlertTrigger
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof AlertTrigger
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof AlertTrigger
     */
    referenceType?: AlertTriggerReferenceTypeEnum;
    /**
     *
     * @type {string}
     * @memberof AlertTrigger
     */
    type?: AlertTriggerTypeEnum;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof AlertTrigger
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const AlertTriggerReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type AlertTriggerReferenceTypeEnum = typeof AlertTriggerReferenceTypeEnum[keyof typeof AlertTriggerReferenceTypeEnum];
/**
 * @export
 */
export declare const AlertTriggerTypeEnum: {
    readonly TooManyLoginFailures: "TOO_MANY_LOGIN_FAILURES";
    readonly RiskAssessment: "RISK_ASSESSMENT";
};
export type AlertTriggerTypeEnum = typeof AlertTriggerTypeEnum[keyof typeof AlertTriggerTypeEnum];
/**
 * Check if a given object implements the AlertTrigger interface.
 */
export declare function instanceOfAlertTrigger(value: object): value is AlertTrigger;
export declare function AlertTriggerFromJSON(json: any): AlertTrigger;
export declare function AlertTriggerFromJSONTyped(json: any, ignoreDiscriminator: boolean): AlertTrigger;
export declare function AlertTriggerToJSON(json: any): AlertTrigger;
export declare function AlertTriggerToJSONTyped(value?: AlertTrigger | null, ignoreDiscriminator?: boolean): any;
