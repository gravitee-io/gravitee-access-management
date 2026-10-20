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
import type { ProtectedResourcePrimaryData } from './ProtectedResourcePrimaryData';
/**
 *
 * @export
 * @interface ProtectedResourcePage
 */
export interface ProtectedResourcePage {
    /**
     *
     * @type {number}
     * @memberof ProtectedResourcePage
     */
    currentPage?: number;
    /**
     *
     * @type {Array<ProtectedResourcePrimaryData>}
     * @memberof ProtectedResourcePage
     */
    data?: Array<ProtectedResourcePrimaryData>;
    /**
     *
     * @type {number}
     * @memberof ProtectedResourcePage
     */
    totalCount?: number;
}
/**
 * Check if a given object implements the ProtectedResourcePage interface.
 */
export declare function instanceOfProtectedResourcePage(value: object): value is ProtectedResourcePage;
export declare function ProtectedResourcePageFromJSON(json: any): ProtectedResourcePage;
export declare function ProtectedResourcePageFromJSONTyped(json: any, ignoreDiscriminator: boolean): ProtectedResourcePage;
export declare function ProtectedResourcePageToJSON(json: any): ProtectedResourcePage;
export declare function ProtectedResourcePageToJSONTyped(value?: ProtectedResourcePage | null, ignoreDiscriminator?: boolean): any;
