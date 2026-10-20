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
 * @interface UpdateFactor
 */
export interface UpdateFactor {
    /**
     *
     * @type {string}
     * @memberof UpdateFactor
     */
    configuration: string;
    /**
     *
     * @type {string}
     * @memberof UpdateFactor
     */
    name: string;
    /**
     *
     * @type {string}
     * @memberof UpdateFactor
     */
    type: string;
}
/**
 * Check if a given object implements the UpdateFactor interface.
 */
export declare function instanceOfUpdateFactor(value: object): value is UpdateFactor;
export declare function UpdateFactorFromJSON(json: any): UpdateFactor;
export declare function UpdateFactorFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateFactor;
export declare function UpdateFactorToJSON(json: any): UpdateFactor;
export declare function UpdateFactorToJSONTyped(value?: UpdateFactor | null, ignoreDiscriminator?: boolean): any;
