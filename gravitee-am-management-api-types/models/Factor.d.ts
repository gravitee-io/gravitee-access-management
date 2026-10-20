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
 * @interface Factor
 */
export interface Factor {
    /**
     *
     * @type {string}
     * @memberof Factor
     */
    configuration?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Factor
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof Factor
     */
    domain?: string;
    /**
     *
     * @type {string}
     * @memberof Factor
     */
    factorType?: FactorFactorTypeEnum;
    /**
     *
     * @type {string}
     * @memberof Factor
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof Factor
     */
    name?: string;
    /**
     *
     * @type {string}
     * @memberof Factor
     */
    type?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Factor
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const FactorFactorTypeEnum: {
    readonly Otp: "OTP";
    readonly Sms: "SMS";
    readonly Email: "EMAIL";
    readonly Call: "CALL";
    readonly Http: "HTTP";
    readonly RecoveryCode: "RECOVERY_CODE";
    readonly Fido2: "FIDO2";
    readonly Mock: "MOCK";
};
export type FactorFactorTypeEnum = typeof FactorFactorTypeEnum[keyof typeof FactorFactorTypeEnum];
/**
 * Check if a given object implements the Factor interface.
 */
export declare function instanceOfFactor(value: object): value is Factor;
export declare function FactorFromJSON(json: any): Factor;
export declare function FactorFromJSONTyped(json: any, ignoreDiscriminator: boolean): Factor;
export declare function FactorToJSON(json: any): Factor;
export declare function FactorToJSONTyped(value?: Factor | null, ignoreDiscriminator?: boolean): any;
