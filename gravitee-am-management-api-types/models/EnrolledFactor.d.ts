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
import type { EnrolledFactorChannel } from './EnrolledFactorChannel';
import type { EnrolledFactorSecurity } from './EnrolledFactorSecurity';
/**
 *
 * @export
 * @interface EnrolledFactor
 */
export interface EnrolledFactor {
    /**
     *
     * @type {string}
     * @memberof EnrolledFactor
     */
    appId?: string;
    /**
     *
     * @type {EnrolledFactorChannel}
     * @memberof EnrolledFactor
     */
    channel?: EnrolledFactorChannel;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof EnrolledFactor
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof EnrolledFactor
     */
    factorId?: string;
    /**
     *
     * @type {boolean}
     * @memberof EnrolledFactor
     */
    primary?: boolean;
    /**
     *
     * @type {EnrolledFactorSecurity}
     * @memberof EnrolledFactor
     */
    security?: EnrolledFactorSecurity;
    /**
     *
     * @type {string}
     * @memberof EnrolledFactor
     */
    status?: EnrolledFactorStatusEnum;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof EnrolledFactor
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const EnrolledFactorStatusEnum: {
    readonly PendingActivation: "PENDING_ACTIVATION";
    readonly Activated: "ACTIVATED";
    readonly Revoked: "REVOKED";
    readonly Null: "NULL";
};
export type EnrolledFactorStatusEnum = typeof EnrolledFactorStatusEnum[keyof typeof EnrolledFactorStatusEnum];
/**
 * Check if a given object implements the EnrolledFactor interface.
 */
export declare function instanceOfEnrolledFactor(value: object): value is EnrolledFactor;
export declare function EnrolledFactorFromJSON(json: any): EnrolledFactor;
export declare function EnrolledFactorFromJSONTyped(json: any, ignoreDiscriminator: boolean): EnrolledFactor;
export declare function EnrolledFactorToJSON(json: any): EnrolledFactor;
export declare function EnrolledFactorToJSONTyped(value?: EnrolledFactor | null, ignoreDiscriminator?: boolean): any;
