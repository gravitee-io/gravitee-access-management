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
import type { BulkOperationResultObject } from './BulkOperationResultObject';
/**
 *
 * @export
 * @interface BulkResponse
 */
export interface BulkResponse {
    /**
     *
     * @type {boolean}
     * @memberof BulkResponse
     */
    allSuccessful?: boolean;
    /**
     *
     * @type {Array<BulkOperationResultObject>}
     * @memberof BulkResponse
     */
    results?: Array<BulkOperationResultObject>;
}
/**
 * Check if a given object implements the BulkResponse interface.
 */
export declare function instanceOfBulkResponse(value: object): value is BulkResponse;
export declare function BulkResponseFromJSON(json: any): BulkResponse;
export declare function BulkResponseFromJSONTyped(json: any, ignoreDiscriminator: boolean): BulkResponse;
export declare function BulkResponseToJSON(json: any): BulkResponse;
export declare function BulkResponseToJSONTyped(value?: BulkResponse | null, ignoreDiscriminator?: boolean): any;
