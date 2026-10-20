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
import type { ApplicationCrossAppAccessSettings } from './ApplicationCrossAppAccessSettings';
import type { TokenClaim } from './TokenClaim';
import type { JWKSet } from './JWKSet';
import type { ApplicationScopeSettings } from './ApplicationScopeSettings';
import type { UserInfoClaim } from './UserInfoClaim';
import type { TokenExchangeOAuthSettings } from './TokenExchangeOAuthSettings';
/**
 *
 * @export
 * @interface PatchApplicationOAuthSettings
 */
export interface PatchApplicationOAuthSettings {
    /**
     *
     * @type {number}
     * @memberof PatchApplicationOAuthSettings
     */
    accessTokenValiditySeconds?: number;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    applicationType?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    authorizationEncryptedResponseAlg?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    authorizationEncryptedResponseEnc?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    authorizationSignedResponseAlg?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof PatchApplicationOAuthSettings
     */
    clientIdIssuedAt?: number;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    clientName?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof PatchApplicationOAuthSettings
     */
    clientSecretExpiresAt?: number;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    clientUri?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof PatchApplicationOAuthSettings
     */
    contacts?: Array<string>;
    /**
     *
     * @type {ApplicationCrossAppAccessSettings}
     * @memberof PatchApplicationOAuthSettings
     */
    crossAppAccessSettings?: ApplicationCrossAppAccessSettings;
    /**
     *
     * @type {Array<string>}
     * @memberof PatchApplicationOAuthSettings
     */
    defaultACRvalues?: Array<string>;
    /**
     *
     * @type {number}
     * @memberof PatchApplicationOAuthSettings
     */
    defaultMaxAge?: number;
    /**
     *
     * @type {boolean}
     * @memberof PatchApplicationOAuthSettings
     */
    disableRefreshTokenRotation?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchApplicationOAuthSettings
     */
    dpopBoundAccessTokens?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchApplicationOAuthSettings
     */
    enhanceScopesWithUserPermissions?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchApplicationOAuthSettings
     */
    forcePKCE?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchApplicationOAuthSettings
     */
    forceS256CodeChallengeMethod?: boolean;
    /**
     *
     * @type {Array<string>}
     * @memberof PatchApplicationOAuthSettings
     */
    grantTypes?: Array<string>;
    /**
     *
     * @type {number}
     * @memberof PatchApplicationOAuthSettings
     */
    idJagValiditySeconds?: number;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    idTokenEncryptedResponseAlg?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    idTokenEncryptedResponseEnc?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    idTokenSignedResponseAlg?: string;
    /**
     *
     * @type {number}
     * @memberof PatchApplicationOAuthSettings
     */
    idTokenValiditySeconds?: number;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    initiateLoginUri?: string;
    /**
     *
     * @type {JWKSet}
     * @memberof PatchApplicationOAuthSettings
     */
    jwks?: JWKSet;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    jwksUri?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    logoUri?: string;
    /**
     *
     * @type {boolean}
     * @memberof PatchApplicationOAuthSettings
     */
    optInScopeSelection?: boolean;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    policyUri?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof PatchApplicationOAuthSettings
     */
    postLogoutRedirectUris?: Array<string>;
    /**
     *
     * @type {Array<string>}
     * @memberof PatchApplicationOAuthSettings
     */
    redirectUris?: Array<string>;
    /**
     *
     * @type {number}
     * @memberof PatchApplicationOAuthSettings
     */
    refreshTokenValiditySeconds?: number;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    registrationAccessToken?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    registrationClientUri?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    requestObjectEncryptionAlg?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    requestObjectEncryptionEnc?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    requestObjectSigningAlg?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof PatchApplicationOAuthSettings
     */
    requestUris?: Array<string>;
    /**
     *
     * @type {boolean}
     * @memberof PatchApplicationOAuthSettings
     */
    requireAuthTime?: boolean;
    /**
     *
     * @type {Array<string>}
     * @memberof PatchApplicationOAuthSettings
     */
    responseTypes?: Array<string>;
    /**
     *
     * @type {Array<ApplicationScopeSettings>}
     * @memberof PatchApplicationOAuthSettings
     */
    scopeSettings?: Array<ApplicationScopeSettings>;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    sectorIdentifierUri?: string;
    /**
     *
     * @type {boolean}
     * @memberof PatchApplicationOAuthSettings
     */
    silentReAuthentication?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchApplicationOAuthSettings
     */
    singleSignOut?: boolean;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    softwareId?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    softwareStatement?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    softwareVersion?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    subjectType?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    tlsClientAuthSanDns?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    tlsClientAuthSanEmail?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    tlsClientAuthSanIp?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    tlsClientAuthSanUri?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    tlsClientAuthSubjectDn?: string;
    /**
     *
     * @type {boolean}
     * @memberof PatchApplicationOAuthSettings
     */
    tlsClientCertificateBoundAccessTokens?: boolean;
    /**
     *
     * @type {Array<TokenClaim>}
     * @memberof PatchApplicationOAuthSettings
     */
    tokenCustomClaims?: Array<TokenClaim>;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    tokenEndpointAuthMethod?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    tokenEndpointAuthSigningAlg?: string;
    /**
     *
     * @type {TokenExchangeOAuthSettings}
     * @memberof PatchApplicationOAuthSettings
     */
    tokenExchangeOAuthSettings?: TokenExchangeOAuthSettings;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    tosUri?: string;
    /**
     *
     * @type {Array<UserInfoClaim>}
     * @memberof PatchApplicationOAuthSettings
     */
    userinfoCustomClaims?: Array<UserInfoClaim>;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    userinfoEncryptedResponseAlg?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    userinfoEncryptedResponseEnc?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    userinfoSignedResponseAlg?: string;
}
/**
 * Check if a given object implements the PatchApplicationOAuthSettings interface.
 */
export declare function instanceOfPatchApplicationOAuthSettings(value: object): value is PatchApplicationOAuthSettings;
export declare function PatchApplicationOAuthSettingsFromJSON(json: any): PatchApplicationOAuthSettings;
export declare function PatchApplicationOAuthSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchApplicationOAuthSettings;
export declare function PatchApplicationOAuthSettingsToJSON(json: any): PatchApplicationOAuthSettings;
export declare function PatchApplicationOAuthSettingsToJSONTyped(value?: PatchApplicationOAuthSettings | null, ignoreDiscriminator?: boolean): any;
