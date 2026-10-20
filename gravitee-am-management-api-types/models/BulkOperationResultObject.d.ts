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
 * @interface BulkOperationResultObject
 */
export interface BulkOperationResultObject {
    /**
     *
     * @type {any}
     * @memberof BulkOperationResultObject
     */
    body?: any;
    /**
     *
     * @type {any}
     * @memberof BulkOperationResultObject
     */
    errorDetails?: any;
    /**
     *
     * @type {number}
     * @memberof BulkOperationResultObject
     */
    httpStatus?: number;
    /**
     *
     * @type {number}
     * @memberof BulkOperationResultObject
     */
    index?: number;
    /**
     *
     * @type {boolean}
     * @memberof BulkOperationResultObject
     */
    success?: boolean;
}
/**
 * Check if a given object implements the BulkOperationResultObject interface.
 */
export declare function instanceOfBulkOperationResultObject(value: object): value is BulkOperationResultObject;
export declare function BulkOperationResultObjectFromJSON(json: any): BulkOperationResultObject;
export declare function BulkOperationResultObjectFromJSONTyped(json: any, ignoreDiscriminator: boolean): BulkOperationResultObject;
export declare function BulkOperationResultObjectToJSON(json: any): BulkOperationResultObject;
export declare function BulkOperationResultObjectToJSONTyped(value?: BulkOperationResultObject | null, ignoreDiscriminator?: boolean): any;
