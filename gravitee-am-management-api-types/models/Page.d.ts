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
 * @interface Page
 */
export interface Page {
    /**
     *
     * @type {number}
     * @memberof Page
     */
    currentPage?: number;
    /**
     *
     * @type {Array<any>}
     * @memberof Page
     */
    data?: Array<any>;
    /**
     *
     * @type {number}
     * @memberof Page
     */
    totalCount?: number;
}
/**
 * Check if a given object implements the Page interface.
 */
export declare function instanceOfPage(value: object): value is Page;
export declare function PageFromJSON(json: any): Page;
export declare function PageFromJSONTyped(json: any, ignoreDiscriminator: boolean): Page;
export declare function PageToJSON(json: any): Page;
export declare function PageToJSONTyped(value?: Page | null, ignoreDiscriminator?: boolean): any;
