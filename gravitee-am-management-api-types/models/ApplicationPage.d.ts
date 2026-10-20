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
import type { FilteredApplication } from './FilteredApplication';
/**
 *
 * @export
 * @interface ApplicationPage
 */
export interface ApplicationPage {
    /**
     *
     * @type {number}
     * @memberof ApplicationPage
     */
    currentPage?: number;
    /**
     *
     * @type {Array<FilteredApplication>}
     * @memberof ApplicationPage
     */
    data?: Array<FilteredApplication>;
    /**
     *
     * @type {number}
     * @memberof ApplicationPage
     */
    totalCount?: number;
}
/**
 * Check if a given object implements the ApplicationPage interface.
 */
export declare function instanceOfApplicationPage(value: object): value is ApplicationPage;
export declare function ApplicationPageFromJSON(json: any): ApplicationPage;
export declare function ApplicationPageFromJSONTyped(json: any, ignoreDiscriminator: boolean): ApplicationPage;
export declare function ApplicationPageToJSON(json: any): ApplicationPage;
export declare function ApplicationPageToJSONTyped(value?: ApplicationPage | null, ignoreDiscriminator?: boolean): any;
