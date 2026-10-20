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
 * Exports one additional attribute, read from the audit context by expression, under a chosen field name.
 * @export
 * @interface ReporterAttributeMapping
 */
export interface ReporterAttributeMapping {
    /**
     * The name the evaluated value takes on the exported payload.
     * @type {string}
     * @memberof ReporterAttributeMapping
     */
    exportedName?: string;
    /**
     * Expression evaluated against the audit context.
     * @type {string}
     * @memberof ReporterAttributeMapping
     */
    expression?: string;
}
/**
 * Check if a given object implements the ReporterAttributeMapping interface.
 */
export declare function instanceOfReporterAttributeMapping(value: object): value is ReporterAttributeMapping;
export declare function ReporterAttributeMappingFromJSON(json: any): ReporterAttributeMapping;
export declare function ReporterAttributeMappingFromJSONTyped(json: any, ignoreDiscriminator: boolean): ReporterAttributeMapping;
export declare function ReporterAttributeMappingToJSON(json: any): ReporterAttributeMapping;
export declare function ReporterAttributeMappingToJSONTyped(value?: ReporterAttributeMapping | null, ignoreDiscriminator?: boolean): any;
