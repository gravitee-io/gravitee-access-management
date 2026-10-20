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
import type { ReporterAttributeMapping } from './ReporterAttributeMapping';
/**
 *
 * @export
 * @interface UpdateReporter
 */
export interface UpdateReporter {
    /**
     *
     * @type {Array<string>}
     * @memberof UpdateReporter
     */
    attributeMappingEventTypes?: Array<string>;
    /**
     *
     * @type {Array<ReporterAttributeMapping>}
     * @memberof UpdateReporter
     */
    attributeMappings?: Array<ReporterAttributeMapping>;
    /**
     *
     * @type {string}
     * @memberof UpdateReporter
     */
    configuration: string;
    /**
     *
     * @type {boolean}
     * @memberof UpdateReporter
     */
    enabled?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof UpdateReporter
     */
    inherited?: boolean;
    /**
     *
     * @type {string}
     * @memberof UpdateReporter
     */
    name: string;
    /**
     *
     * @type {string}
     * @memberof UpdateReporter
     */
    type: string;
}
/**
 * Check if a given object implements the UpdateReporter interface.
 */
export declare function instanceOfUpdateReporter(value: object): value is UpdateReporter;
export declare function UpdateReporterFromJSON(json: any): UpdateReporter;
export declare function UpdateReporterFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateReporter;
export declare function UpdateReporterToJSON(json: any): UpdateReporter;
export declare function UpdateReporterToJSONTyped(value?: UpdateReporter | null, ignoreDiscriminator?: boolean): any;
