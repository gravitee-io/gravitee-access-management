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
 * @interface AuthorizationEngine
 */
export interface AuthorizationEngine {
    /**
     *
     * @type {string}
     * @memberof AuthorizationEngine
     */
    configuration?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof AuthorizationEngine
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof AuthorizationEngine
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof AuthorizationEngine
     */
    name?: string;
    /**
     *
     * @type {string}
     * @memberof AuthorizationEngine
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof AuthorizationEngine
     */
    referenceType?: AuthorizationEngineReferenceTypeEnum;
    /**
     *
     * @type {string}
     * @memberof AuthorizationEngine
     */
    type?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof AuthorizationEngine
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const AuthorizationEngineReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type AuthorizationEngineReferenceTypeEnum = typeof AuthorizationEngineReferenceTypeEnum[keyof typeof AuthorizationEngineReferenceTypeEnum];
/**
 * Check if a given object implements the AuthorizationEngine interface.
 */
export declare function instanceOfAuthorizationEngine(value: object): value is AuthorizationEngine;
export declare function AuthorizationEngineFromJSON(json: any): AuthorizationEngine;
export declare function AuthorizationEngineFromJSONTyped(json: any, ignoreDiscriminator: boolean): AuthorizationEngine;
export declare function AuthorizationEngineToJSON(json: any): AuthorizationEngine;
export declare function AuthorizationEngineToJSONTyped(value?: AuthorizationEngine | null, ignoreDiscriminator?: boolean): any;
