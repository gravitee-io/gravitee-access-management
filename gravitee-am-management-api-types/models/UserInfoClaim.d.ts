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
 * @interface UserInfoClaim
 */
export interface UserInfoClaim {
    /**
     *
     * @type {string}
     * @memberof UserInfoClaim
     */
    claimName?: string;
    /**
     *
     * @type {string}
     * @memberof UserInfoClaim
     */
    claimValue?: string;
}
/**
 * Check if a given object implements the UserInfoClaim interface.
 */
export declare function instanceOfUserInfoClaim(value: object): value is UserInfoClaim;
export declare function UserInfoClaimFromJSON(json: any): UserInfoClaim;
export declare function UserInfoClaimFromJSONTyped(json: any, ignoreDiscriminator: boolean): UserInfoClaim;
export declare function UserInfoClaimToJSON(json: any): UserInfoClaim;
export declare function UserInfoClaimToJSONTyped(value?: UserInfoClaim | null, ignoreDiscriminator?: boolean): any;
