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
 * @interface DataSource
 */
export interface DataSource {
    /**
     *
     * @type {string}
     * @memberof DataSource
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof DataSource
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof DataSource
     */
    name?: string;
}
/**
 * Check if a given object implements the DataSource interface.
 */
export declare function instanceOfDataSource(value: object): value is DataSource;
export declare function DataSourceFromJSON(json: any): DataSource;
export declare function DataSourceFromJSONTyped(json: any, ignoreDiscriminator: boolean): DataSource;
export declare function DataSourceToJSON(json: any): DataSource;
export declare function DataSourceToJSONTyped(value?: DataSource | null, ignoreDiscriminator?: boolean): any;
