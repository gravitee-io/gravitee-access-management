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
 * @interface ServiceResource
 */
export interface ServiceResource {
    /**
     *
     * @type {string}
     * @memberof ServiceResource
     */
    configuration?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof ServiceResource
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof ServiceResource
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof ServiceResource
     */
    name?: string;
    /**
     *
     * @type {string}
     * @memberof ServiceResource
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof ServiceResource
     */
    referenceType?: ServiceResourceReferenceTypeEnum;
    /**
     *
     * @type {string}
     * @memberof ServiceResource
     */
    type?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof ServiceResource
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const ServiceResourceReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type ServiceResourceReferenceTypeEnum = typeof ServiceResourceReferenceTypeEnum[keyof typeof ServiceResourceReferenceTypeEnum];
/**
 * Check if a given object implements the ServiceResource interface.
 */
export declare function instanceOfServiceResource(value: object): value is ServiceResource;
export declare function ServiceResourceFromJSON(json: any): ServiceResource;
export declare function ServiceResourceFromJSONTyped(json: any, ignoreDiscriminator: boolean): ServiceResource;
export declare function ServiceResourceToJSON(json: any): ServiceResource;
export declare function ServiceResourceToJSONTyped(value?: ServiceResource | null, ignoreDiscriminator?: boolean): any;
