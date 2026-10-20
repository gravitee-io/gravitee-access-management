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
 * @interface ErrorEntity
 */
export interface ErrorEntity {
    /**
     *
     * @type {number}
     * @memberof ErrorEntity
     */
    http_status?: number;
    /**
     *
     * @type {string}
     * @memberof ErrorEntity
     */
    message?: string;
}
/**
 * Check if a given object implements the ErrorEntity interface.
 */
export declare function instanceOfErrorEntity(value: object): value is ErrorEntity;
export declare function ErrorEntityFromJSON(json: any): ErrorEntity;
export declare function ErrorEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): ErrorEntity;
export declare function ErrorEntityToJSON(json: any): ErrorEntity;
export declare function ErrorEntityToJSONTyped(value?: ErrorEntity | null, ignoreDiscriminator?: boolean): any;
