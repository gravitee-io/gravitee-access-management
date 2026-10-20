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
import type { Domain } from './Domain';
/**
 *
 * @export
 * @interface DomainPage
 */
export interface DomainPage {
    /**
     *
     * @type {number}
     * @memberof DomainPage
     */
    currentPage?: number;
    /**
     *
     * @type {Array<Domain>}
     * @memberof DomainPage
     */
    data?: Array<Domain>;
    /**
     *
     * @type {number}
     * @memberof DomainPage
     */
    totalCount?: number;
}
/**
 * Check if a given object implements the DomainPage interface.
 */
export declare function instanceOfDomainPage(value: object): value is DomainPage;
export declare function DomainPageFromJSON(json: any): DomainPage;
export declare function DomainPageFromJSONTyped(json: any, ignoreDiscriminator: boolean): DomainPage;
export declare function DomainPageToJSON(json: any): DomainPage;
export declare function DomainPageToJSONTyped(value?: DomainPage | null, ignoreDiscriminator?: boolean): any;
