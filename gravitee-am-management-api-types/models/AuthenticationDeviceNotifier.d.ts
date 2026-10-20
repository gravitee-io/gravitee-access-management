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
 * @interface AuthenticationDeviceNotifier
 */
export interface AuthenticationDeviceNotifier {
    /**
     *
     * @type {string}
     * @memberof AuthenticationDeviceNotifier
     */
    configuration?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof AuthenticationDeviceNotifier
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof AuthenticationDeviceNotifier
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof AuthenticationDeviceNotifier
     */
    name?: string;
    /**
     *
     * @type {string}
     * @memberof AuthenticationDeviceNotifier
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof AuthenticationDeviceNotifier
     */
    referenceType?: AuthenticationDeviceNotifierReferenceTypeEnum;
    /**
     *
     * @type {string}
     * @memberof AuthenticationDeviceNotifier
     */
    type?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof AuthenticationDeviceNotifier
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const AuthenticationDeviceNotifierReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type AuthenticationDeviceNotifierReferenceTypeEnum = typeof AuthenticationDeviceNotifierReferenceTypeEnum[keyof typeof AuthenticationDeviceNotifierReferenceTypeEnum];
/**
 * Check if a given object implements the AuthenticationDeviceNotifier interface.
 */
export declare function instanceOfAuthenticationDeviceNotifier(value: object): value is AuthenticationDeviceNotifier;
export declare function AuthenticationDeviceNotifierFromJSON(json: any): AuthenticationDeviceNotifier;
export declare function AuthenticationDeviceNotifierFromJSONTyped(json: any, ignoreDiscriminator: boolean): AuthenticationDeviceNotifier;
export declare function AuthenticationDeviceNotifierToJSON(json: any): AuthenticationDeviceNotifier;
export declare function AuthenticationDeviceNotifierToJSONTyped(value?: AuthenticationDeviceNotifier | null, ignoreDiscriminator?: boolean): any;
