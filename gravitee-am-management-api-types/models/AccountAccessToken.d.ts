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
 * @interface AccountAccessToken
 */
export interface AccountAccessToken {
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof AccountAccessToken
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof AccountAccessToken
     */
    issuerId?: string;
    /**
     *
     * @type {string}
     * @memberof AccountAccessToken
     */
    issuerUsername?: string;
    /**
     *
     * @type {string}
     * @memberof AccountAccessToken
     */
    name?: string;
    /**
     *
     * @type {string}
     * @memberof AccountAccessToken
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof AccountAccessToken
     */
    referenceType?: AccountAccessTokenReferenceTypeEnum;
    /**
     *
     * @type {string}
     * @memberof AccountAccessToken
     */
    token?: string;
    /**
     *
     * @type {string}
     * @memberof AccountAccessToken
     */
    tokenId?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof AccountAccessToken
     */
    updatedAt?: number;
    /**
     *
     * @type {string}
     * @memberof AccountAccessToken
     */
    userId?: string;
}
/**
 * @export
 */
export declare const AccountAccessTokenReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type AccountAccessTokenReferenceTypeEnum = typeof AccountAccessTokenReferenceTypeEnum[keyof typeof AccountAccessTokenReferenceTypeEnum];
/**
 * Check if a given object implements the AccountAccessToken interface.
 */
export declare function instanceOfAccountAccessToken(value: object): value is AccountAccessToken;
export declare function AccountAccessTokenFromJSON(json: any): AccountAccessToken;
export declare function AccountAccessTokenFromJSONTyped(json: any, ignoreDiscriminator: boolean): AccountAccessToken;
export declare function AccountAccessTokenToJSON(json: any): AccountAccessToken;
export declare function AccountAccessTokenToJSONTyped(value?: AccountAccessToken | null, ignoreDiscriminator?: boolean): any;
