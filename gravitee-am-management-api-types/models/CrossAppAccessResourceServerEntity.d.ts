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
 * A resource server an application may request an ID-JAG for.
 * @export
 * @interface CrossAppAccessResourceServerEntity
 */
export interface CrossAppAccessResourceServerEntity {
    /**
     * Label the resource server is known by.
     * @type {string}
     * @memberof CrossAppAccessResourceServerEntity
     */
    name?: string;
    /**
     * Resource identifier of the resource server, carried by the "resource" claim.
     * @type {string}
     * @memberof CrossAppAccessResourceServerEntity
     */
    resource?: string;
    /**
     * Identifier of the resource server, stable across a rename.
     * @type {string}
     * @memberof CrossAppAccessResourceServerEntity
     */
    resourceServerId?: string;
    /**
     * Identifier of the trusted domain exposing this resource server.
     * @type {string}
     * @memberof CrossAppAccessResourceServerEntity
     */
    trustDomainId?: string;
    /**
     * Name of the trusted domain exposing this resource server.
     * @type {string}
     * @memberof CrossAppAccessResourceServerEntity
     */
    trustDomainName?: string;
}
/**
 * Check if a given object implements the CrossAppAccessResourceServerEntity interface.
 */
export declare function instanceOfCrossAppAccessResourceServerEntity(value: object): value is CrossAppAccessResourceServerEntity;
export declare function CrossAppAccessResourceServerEntityFromJSON(json: any): CrossAppAccessResourceServerEntity;
export declare function CrossAppAccessResourceServerEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): CrossAppAccessResourceServerEntity;
export declare function CrossAppAccessResourceServerEntityToJSON(json: any): CrossAppAccessResourceServerEntity;
export declare function CrossAppAccessResourceServerEntityToJSONTyped(value?: CrossAppAccessResourceServerEntity | null, ignoreDiscriminator?: boolean): any;
