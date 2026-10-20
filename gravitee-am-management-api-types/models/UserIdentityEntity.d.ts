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
 * @interface UserIdentityEntity
 */
export interface UserIdentityEntity {
    /**
     *
     * @type {{ [key: string]: any; }}
     * @memberof UserIdentityEntity
     */
    additionalInformation?: {
        [key: string]: any;
    };
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof UserIdentityEntity
     */
    linkedAt?: number;
    /**
     *
     * @type {string}
     * @memberof UserIdentityEntity
     */
    providerId?: string;
    /**
     *
     * @type {string}
     * @memberof UserIdentityEntity
     */
    providerName?: string;
    /**
     *
     * @type {string}
     * @memberof UserIdentityEntity
     */
    userId?: string;
    /**
     *
     * @type {string}
     * @memberof UserIdentityEntity
     */
    username?: string;
}
/**
 * Check if a given object implements the UserIdentityEntity interface.
 */
export declare function instanceOfUserIdentityEntity(value: object): value is UserIdentityEntity;
export declare function UserIdentityEntityFromJSON(json: any): UserIdentityEntity;
export declare function UserIdentityEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): UserIdentityEntity;
export declare function UserIdentityEntityToJSON(json: any): UserIdentityEntity;
export declare function UserIdentityEntityToJSONTyped(value?: UserIdentityEntity | null, ignoreDiscriminator?: boolean): any;
