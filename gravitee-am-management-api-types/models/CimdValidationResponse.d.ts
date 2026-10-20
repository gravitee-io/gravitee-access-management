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
import type { Missing } from './Missing';
import type { ClientMetadata } from './ClientMetadata';
/**
 *
 * @export
 * @interface CimdValidationResponse
 */
export interface CimdValidationResponse {
    /**
     *
     * @type {boolean}
     * @memberof CimdValidationResponse
     */
    hasInlineJwks?: boolean;
    /**
     *
     * @type {ClientMetadata}
     * @memberof CimdValidationResponse
     */
    metadata?: ClientMetadata;
    /**
     *
     * @type {Missing}
     * @memberof CimdValidationResponse
     */
    missing?: Missing;
    /**
     *
     * @type {string}
     * @memberof CimdValidationResponse
     */
    url?: string;
}
/**
 * Check if a given object implements the CimdValidationResponse interface.
 */
export declare function instanceOfCimdValidationResponse(value: object): value is CimdValidationResponse;
export declare function CimdValidationResponseFromJSON(json: any): CimdValidationResponse;
export declare function CimdValidationResponseFromJSONTyped(json: any, ignoreDiscriminator: boolean): CimdValidationResponse;
export declare function CimdValidationResponseToJSON(json: any): CimdValidationResponse;
export declare function CimdValidationResponseToJSONTyped(value?: CimdValidationResponse | null, ignoreDiscriminator?: boolean): any;
