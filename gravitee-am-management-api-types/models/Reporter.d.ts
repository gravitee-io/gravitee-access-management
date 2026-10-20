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
import type { ManagedBy } from './ManagedBy';
import type { Reference } from './Reference';
/**
 *
 * @export
 * @interface Reporter
 */
export interface Reporter {
    /**
     *
     * @type {Array<string>}
     * @memberof Reporter
     */
    attributeMappingEventTypes?: Array<string>;
    /**
     *
     * @type {Array<ReporterAttributeMapping>}
     * @memberof Reporter
     */
    attributeMappings?: Array<ReporterAttributeMapping>;
    /**
     *
     * @type {string}
     * @memberof Reporter
     */
    configuration?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Reporter
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof Reporter
     */
    dataType?: string;
    /**
     *
     * @type {boolean}
     * @memberof Reporter
     */
    enabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof Reporter
     */
    id?: string;
    /**
     *
     * @type {boolean}
     * @memberof Reporter
     */
    inherited?: boolean;
    /**
     *
     * @type {string}
     * @memberof Reporter
     */
    readonly key?: string;
    /**
     *
     * @type {ManagedBy}
     * @memberof Reporter
     */
    managedBy?: ManagedBy;
    /**
     *
     * @type {string}
     * @memberof Reporter
     */
    name?: string;
    /**
     *
     * @type {Reference}
     * @memberof Reporter
     */
    reference?: Reference;
    /**
     *
     * @type {boolean}
     * @memberof Reporter
     */
    system?: boolean;
    /**
     *
     * @type {string}
     * @memberof Reporter
     */
    type?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Reporter
     */
    updatedAt?: number;
}
/**
 * Check if a given object implements the Reporter interface.
 */
export declare function instanceOfReporter(value: object): value is Reporter;
export declare function ReporterFromJSON(json: any): Reporter;
export declare function ReporterFromJSONTyped(json: any, ignoreDiscriminator: boolean): Reporter;
export declare function ReporterToJSON(json: any): Reporter;
export declare function ReporterToJSONTyped(value?: Omit<Reporter, 'key'> | null, ignoreDiscriminator?: boolean): any;
