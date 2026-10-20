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
 * @interface JWK
 */
export interface JWK {
    /**
     *
     * @type {string}
     * @memberof JWK
     */
    alg?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof JWK
     */
    keyOps?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof JWK
     */
    kid?: string;
    /**
     *
     * @type {string}
     * @memberof JWK
     */
    kty?: string;
    /**
     *
     * @type {string}
     * @memberof JWK
     */
    use?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof JWK
     */
    x5c?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof JWK
     */
    x5t?: string;
    /**
     *
     * @type {string}
     * @memberof JWK
     */
    x5tS256?: string;
    /**
     *
     * @type {string}
     * @memberof JWK
     */
    x5u?: string;
}
/**
 * Check if a given object implements the JWK interface.
 */
export declare function instanceOfJWK(value: object): value is JWK;
export declare function JWKFromJSON(json: any): JWK;
export declare function JWKFromJSONTyped(json: any, ignoreDiscriminator: boolean): JWK;
export declare function JWKToJSON(json: any): JWK;
export declare function JWKToJSONTyped(value?: JWK | null, ignoreDiscriminator?: boolean): any;
