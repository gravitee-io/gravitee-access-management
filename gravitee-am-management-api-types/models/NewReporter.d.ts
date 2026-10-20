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
 * @interface NewReporter
 */
export interface NewReporter {
    /**
     *
     * @type {Array<string>}
     * @memberof NewReporter
     */
    attributeMappingEventTypes?: Array<string>;
    /**
     *
     * @type {Array<ReporterAttributeMapping>}
     * @memberof NewReporter
     */
    attributeMappings?: Array<ReporterAttributeMapping>;
    /**
     *
     * @type {string}
     * @memberof NewReporter
     */
    configuration: string;
    /**
     *
     * @type {boolean}
     * @memberof NewReporter
     */
    enabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof NewReporter
     */
    id?: string;
    /**
     *
     * @type {boolean}
     * @memberof NewReporter
     */
    inherited?: boolean;
    /**
     *
     * @type {string}
     * @memberof NewReporter
     */
    name: string;
    /**
     *
     * @type {string}
     * @memberof NewReporter
     */
    type: string;
}
/**
 * Check if a given object implements the NewReporter interface.
 */
export declare function instanceOfNewReporter(value: object): value is NewReporter;
export declare function NewReporterFromJSON(json: any): NewReporter;
export declare function NewReporterFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewReporter;
export declare function NewReporterToJSON(json: any): NewReporter;
export declare function NewReporterToJSONTyped(value?: NewReporter | null, ignoreDiscriminator?: boolean): any;
