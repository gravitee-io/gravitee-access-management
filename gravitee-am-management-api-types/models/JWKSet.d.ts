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
import type { JWK } from './JWK';
/**
 *
 * @export
 * @interface JWKSet
 */
export interface JWKSet {
    /**
     *
     * @type {Array<JWK>}
     * @memberof JWKSet
     */
    keys?: Array<JWK>;
}
/**
 * Check if a given object implements the JWKSet interface.
 */
export declare function instanceOfJWKSet(value: object): value is JWKSet;
export declare function JWKSetFromJSON(json: any): JWKSet;
export declare function JWKSetFromJSONTyped(json: any, ignoreDiscriminator: boolean): JWKSet;
export declare function JWKSetToJSON(json: any): JWKSet;
export declare function JWKSetToJSONTyped(value?: JWKSet | null, ignoreDiscriminator?: boolean): any;
