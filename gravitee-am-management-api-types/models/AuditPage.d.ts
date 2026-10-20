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
import type { Audit } from './Audit';
/**
 *
 * @export
 * @interface AuditPage
 */
export interface AuditPage {
    /**
     *
     * @type {number}
     * @memberof AuditPage
     */
    currentPage?: number;
    /**
     *
     * @type {Array<Audit>}
     * @memberof AuditPage
     */
    data?: Array<Audit>;
    /**
     *
     * @type {number}
     * @memberof AuditPage
     */
    totalCount?: number;
}
/**
 * Check if a given object implements the AuditPage interface.
 */
export declare function instanceOfAuditPage(value: object): value is AuditPage;
export declare function AuditPageFromJSON(json: any): AuditPage;
export declare function AuditPageFromJSONTyped(json: any, ignoreDiscriminator: boolean): AuditPage;
export declare function AuditPageToJSON(json: any): AuditPage;
export declare function AuditPageToJSONTyped(value?: AuditPage | null, ignoreDiscriminator?: boolean): any;
