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
 * @interface TokenClaim
 */
export interface TokenClaim {
    /**
     *
     * @type {string}
     * @memberof TokenClaim
     */
    claimName?: string;
    /**
     *
     * @type {string}
     * @memberof TokenClaim
     */
    claimValue?: string;
    /**
     *
     * @type {string}
     * @memberof TokenClaim
     */
    tokenType?: TokenClaimTokenTypeEnum;
}
/**
 * @export
 */
export declare const TokenClaimTokenTypeEnum: {
    readonly AccessToken: "ACCESS_TOKEN";
    readonly RefreshToken: "REFRESH_TOKEN";
    readonly IdToken: "ID_TOKEN";
    readonly IdJag: "ID_JAG";
};
export type TokenClaimTokenTypeEnum = typeof TokenClaimTokenTypeEnum[keyof typeof TokenClaimTokenTypeEnum];
/**
 * Check if a given object implements the TokenClaim interface.
 */
export declare function instanceOfTokenClaim(value: object): value is TokenClaim;
export declare function TokenClaimFromJSON(json: any): TokenClaim;
export declare function TokenClaimFromJSONTyped(json: any, ignoreDiscriminator: boolean): TokenClaim;
export declare function TokenClaimToJSON(json: any): TokenClaim;
export declare function TokenClaimToJSONTyped(value?: TokenClaim | null, ignoreDiscriminator?: boolean): any;
