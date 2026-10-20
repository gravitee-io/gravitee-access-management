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
 * @interface NewFactor
 */
export interface NewFactor {
    /**
     *
     * @type {string}
     * @memberof NewFactor
     */
    configuration: string;
    /**
     *
     * @type {string}
     * @memberof NewFactor
     */
    factorType: string;
    /**
     *
     * @type {string}
     * @memberof NewFactor
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof NewFactor
     */
    name: string;
    /**
     *
     * @type {string}
     * @memberof NewFactor
     */
    type: string;
}
/**
 * Check if a given object implements the NewFactor interface.
 */
export declare function instanceOfNewFactor(value: object): value is NewFactor;
export declare function NewFactorFromJSON(json: any): NewFactor;
export declare function NewFactorFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewFactor;
export declare function NewFactorToJSON(json: any): NewFactor;
export declare function NewFactorToJSONTyped(value?: NewFactor | null, ignoreDiscriminator?: boolean): any;
