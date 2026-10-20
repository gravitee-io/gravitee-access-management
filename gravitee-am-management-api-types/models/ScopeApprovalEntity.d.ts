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
import type { ScopeEntity } from './ScopeEntity';
import type { ApplicationEntity } from './ApplicationEntity';
import type { UserId } from './UserId';
/**
 *
 * @export
 * @interface ScopeApprovalEntity
 */
export interface ScopeApprovalEntity {
    /**
     *
     * @type {ApplicationEntity}
     * @memberof ScopeApprovalEntity
     */
    clientEntity?: ApplicationEntity;
    /**
     *
     * @type {string}
     * @memberof ScopeApprovalEntity
     */
    clientId?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof ScopeApprovalEntity
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof ScopeApprovalEntity
     */
    domain?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof ScopeApprovalEntity
     */
    expiresAt?: number;
    /**
     *
     * @type {string}
     * @memberof ScopeApprovalEntity
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof ScopeApprovalEntity
     */
    scope?: string;
    /**
     *
     * @type {ScopeEntity}
     * @memberof ScopeApprovalEntity
     */
    scopeEntity?: ScopeEntity;
    /**
     *
     * @type {string}
     * @memberof ScopeApprovalEntity
     */
    status?: ScopeApprovalEntityStatusEnum;
    /**
     *
     * @type {string}
     * @memberof ScopeApprovalEntity
     */
    transactionId?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof ScopeApprovalEntity
     */
    updatedAt?: number;
    /**
     *
     * @type {UserId}
     * @memberof ScopeApprovalEntity
     */
    userId?: UserId;
}
/**
 * @export
 */
export declare const ScopeApprovalEntityStatusEnum: {
    readonly Approved: "APPROVED";
    readonly Denied: "DENIED";
};
export type ScopeApprovalEntityStatusEnum = typeof ScopeApprovalEntityStatusEnum[keyof typeof ScopeApprovalEntityStatusEnum];
/**
 * Check if a given object implements the ScopeApprovalEntity interface.
 */
export declare function instanceOfScopeApprovalEntity(value: object): value is ScopeApprovalEntity;
export declare function ScopeApprovalEntityFromJSON(json: any): ScopeApprovalEntity;
export declare function ScopeApprovalEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): ScopeApprovalEntity;
export declare function ScopeApprovalEntityToJSON(json: any): ScopeApprovalEntity;
export declare function ScopeApprovalEntityToJSONTyped(value?: ScopeApprovalEntity | null, ignoreDiscriminator?: boolean): any;
