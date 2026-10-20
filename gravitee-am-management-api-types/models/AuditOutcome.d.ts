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
 * @interface AuditOutcome
 */
export interface AuditOutcome {
    /**
     *
     * @type {string}
     * @memberof AuditOutcome
     */
    message?: string;
    /**
     *
     * @type {string}
     * @memberof AuditOutcome
     */
    status?: AuditOutcomeStatusEnum;
}
/**
 * @export
 */
export declare const AuditOutcomeStatusEnum: {
    readonly Success: "SUCCESS";
    readonly Failure: "FAILURE";
};
export type AuditOutcomeStatusEnum = typeof AuditOutcomeStatusEnum[keyof typeof AuditOutcomeStatusEnum];
/**
 * Check if a given object implements the AuditOutcome interface.
 */
export declare function instanceOfAuditOutcome(value: object): value is AuditOutcome;
export declare function AuditOutcomeFromJSON(json: any): AuditOutcome;
export declare function AuditOutcomeFromJSONTyped(json: any, ignoreDiscriminator: boolean): AuditOutcome;
export declare function AuditOutcomeToJSON(json: any): AuditOutcome;
export declare function AuditOutcomeToJSONTyped(value?: AuditOutcome | null, ignoreDiscriminator?: boolean): any;
