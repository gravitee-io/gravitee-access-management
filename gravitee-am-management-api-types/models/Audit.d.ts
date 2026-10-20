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
import type { AuditEntity } from './AuditEntity';
import type { Reference } from './Reference';
import type { AuditOutcome } from './AuditOutcome';
import type { AuditAccessPoint } from './AuditAccessPoint';
/**
 *
 * @export
 * @interface Audit
 */
export interface Audit {
    /**
     *
     * @type {AuditAccessPoint}
     * @memberof Audit
     */
    accessPoint?: AuditAccessPoint;
    /**
     *
     * @type {AuditEntity}
     * @memberof Audit
     */
    actor?: AuditEntity;
    /**
     *
     * @type {string}
     * @memberof Audit
     */
    id?: string;
    /**
     *
     * @type {AuditOutcome}
     * @memberof Audit
     */
    outcome?: AuditOutcome;
    /**
     *
     * @type {Reference}
     * @memberof Audit
     */
    reference?: Reference;
    /**
     *
     * @type {string}
     * @memberof Audit
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof Audit
     */
    referenceType?: AuditReferenceTypeEnum;
    /**
     *
     * @type {AuditEntity}
     * @memberof Audit
     */
    target?: AuditEntity;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Audit
     */
    timestamp?: number;
    /**
     *
     * @type {string}
     * @memberof Audit
     */
    transactionId?: string;
    /**
     *
     * @type {string}
     * @memberof Audit
     */
    type?: string;
}
/**
 * @export
 */
export declare const AuditReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type AuditReferenceTypeEnum = typeof AuditReferenceTypeEnum[keyof typeof AuditReferenceTypeEnum];
/**
 * Check if a given object implements the Audit interface.
 */
export declare function instanceOfAudit(value: object): value is Audit;
export declare function AuditFromJSON(json: any): Audit;
export declare function AuditFromJSONTyped(json: any, ignoreDiscriminator: boolean): Audit;
export declare function AuditToJSON(json: any): Audit;
export declare function AuditToJSONTyped(value?: Audit | null, ignoreDiscriminator?: boolean): any;
