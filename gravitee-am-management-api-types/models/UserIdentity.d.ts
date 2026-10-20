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
 * @interface UserIdentity
 */
export interface UserIdentity {
    /**
     *
     * @type {{ [key: string]: any; }}
     * @memberof UserIdentity
     */
    additionalInformation?: {
        [key: string]: any;
    };
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof UserIdentity
     */
    linkedAt?: number;
    /**
     *
     * @type {string}
     * @memberof UserIdentity
     */
    providerId?: string;
    /**
     *
     * @type {string}
     * @memberof UserIdentity
     */
    userId?: string;
    /**
     *
     * @type {string}
     * @memberof UserIdentity
     */
    username?: string;
}
/**
 * Check if a given object implements the UserIdentity interface.
 */
export declare function instanceOfUserIdentity(value: object): value is UserIdentity;
export declare function UserIdentityFromJSON(json: any): UserIdentity;
export declare function UserIdentityFromJSONTyped(json: any, ignoreDiscriminator: boolean): UserIdentity;
export declare function UserIdentityToJSON(json: any): UserIdentity;
export declare function UserIdentityToJSONTyped(value?: UserIdentity | null, ignoreDiscriminator?: boolean): any;
