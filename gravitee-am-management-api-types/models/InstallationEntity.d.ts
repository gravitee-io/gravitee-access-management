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
 * @interface InstallationEntity
 */
export interface InstallationEntity {
    /**
     *
     * @type {{ [key: string]: string; }}
     * @memberof InstallationEntity
     */
    additionalInformation?: {
        [key: string]: string;
    };
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof InstallationEntity
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof InstallationEntity
     */
    id?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof InstallationEntity
     */
    updatedAt?: number;
}
/**
 * Check if a given object implements the InstallationEntity interface.
 */
export declare function instanceOfInstallationEntity(value: object): value is InstallationEntity;
export declare function InstallationEntityFromJSON(json: any): InstallationEntity;
export declare function InstallationEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): InstallationEntity;
export declare function InstallationEntityToJSON(json: any): InstallationEntity;
export declare function InstallationEntityToJSONTyped(value?: InstallationEntity | null, ignoreDiscriminator?: boolean): any;
