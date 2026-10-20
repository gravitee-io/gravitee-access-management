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
 * @interface DeviceIdentifier
 */
export interface DeviceIdentifier {
    /**
     *
     * @type {string}
     * @memberof DeviceIdentifier
     */
    configuration?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof DeviceIdentifier
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof DeviceIdentifier
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof DeviceIdentifier
     */
    name?: string;
    /**
     *
     * @type {string}
     * @memberof DeviceIdentifier
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof DeviceIdentifier
     */
    referenceType?: DeviceIdentifierReferenceTypeEnum;
    /**
     *
     * @type {string}
     * @memberof DeviceIdentifier
     */
    type?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof DeviceIdentifier
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const DeviceIdentifierReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type DeviceIdentifierReferenceTypeEnum = typeof DeviceIdentifierReferenceTypeEnum[keyof typeof DeviceIdentifierReferenceTypeEnum];
/**
 * Check if a given object implements the DeviceIdentifier interface.
 */
export declare function instanceOfDeviceIdentifier(value: object): value is DeviceIdentifier;
export declare function DeviceIdentifierFromJSON(json: any): DeviceIdentifier;
export declare function DeviceIdentifierFromJSONTyped(json: any, ignoreDiscriminator: boolean): DeviceIdentifier;
export declare function DeviceIdentifierToJSON(json: any): DeviceIdentifier;
export declare function DeviceIdentifierToJSONTyped(value?: DeviceIdentifier | null, ignoreDiscriminator?: boolean): any;
