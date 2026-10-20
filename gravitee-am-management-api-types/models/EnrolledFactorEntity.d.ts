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
 * @interface EnrolledFactorEntity
 */
export interface EnrolledFactorEntity {
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof EnrolledFactorEntity
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof EnrolledFactorEntity
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof EnrolledFactorEntity
     */
    name?: string;
    /**
     *
     * @type {string}
     * @memberof EnrolledFactorEntity
     */
    target?: string;
    /**
     *
     * @type {string}
     * @memberof EnrolledFactorEntity
     */
    type?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof EnrolledFactorEntity
     */
    updatedAt?: number;
}
/**
 * Check if a given object implements the EnrolledFactorEntity interface.
 */
export declare function instanceOfEnrolledFactorEntity(value: object): value is EnrolledFactorEntity;
export declare function EnrolledFactorEntityFromJSON(json: any): EnrolledFactorEntity;
export declare function EnrolledFactorEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): EnrolledFactorEntity;
export declare function EnrolledFactorEntityToJSON(json: any): EnrolledFactorEntity;
export declare function EnrolledFactorEntityToJSONTyped(value?: EnrolledFactorEntity | null, ignoreDiscriminator?: boolean): any;
