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
 * @interface ApplicationOAuthSettings
 */
export interface ApplicationOAuthSettings {
    /**
     *
     * @type {number}
     * @memberof ApplicationOAuthSettings
     */
    accessTokenValiditySeconds?: number;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    applicationType?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    authorizationEncryptedResponseAlg?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    authorizationEncryptedResponseEnc?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    authorizationSignedResponseAlg?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    backchannelAuthRequestSignAlg?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    backchannelClientNotificationEndpoint?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    backchannelTokenDeliveryMode?: string;
    /**
     *
     * @type {boolean}
     * @memberof ApplicationOAuthSettings
     */
    backchannelUserCodeParameter?: boolean;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    clientId?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof ApplicationOAuthSettings
     */
    clientIdIssuedAt?: number;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    clientName?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    clientSecret?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof ApplicationOAuthSettings
     */
    clientSecretExpiresAt?: number;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    clientType?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    clientUri?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof ApplicationOAuthSettings
     */
    contacts?: Array<string>;
    /**
     *
     * @type {ApplicationCrossAppAccessSettings}
     * @memberof ApplicationOAuthSettings
     */
    crossAppAccessSettings?: ApplicationCrossAppAccessSettings;
    /**
     *
     * @type {Array<string>}
     * @memberof ApplicationOAuthSettings
     */
    defaultACRvalues?: Array<string>;
    /**
     *
     * @type {number}
     * @memberof ApplicationOAuthSettings
     */
    defaultMaxAge?: number;
    /**
     *
     * @type {Array<string>}
     * @memberof ApplicationOAuthSettings
     */
    defaultScopes?: Array<string>;
    /**
     *
     * @type {boolean}
     * @memberof ApplicationOAuthSettings
     */
    disableRefreshTokenRotation?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof ApplicationOAuthSettings
     */
    dpopBoundAccessTokens?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof ApplicationOAuthSettings
     */
    enhanceScopesWithUserPermissions?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof ApplicationOAuthSettings
     */
    forcePKCE?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof ApplicationOAuthSettings
     */
    forceS256CodeChallengeMethod?: boolean;
    /**
     *
     * @type {Array<string>}
     * @memberof ApplicationOAuthSettings
     */
    grantTypes?: Array<string>;
    /**
     * Lifetime in seconds of an ID-JAG minted for this application.
     * @type {number}
     * @memberof ApplicationOAuthSettings
     */
    idJagValiditySeconds?: number;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    idTokenEncryptedResponseAlg?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    idTokenEncryptedResponseEnc?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    idTokenSignedResponseAlg?: string;
    /**
     *
     * @type {number}
     * @memberof ApplicationOAuthSettings
     */
    idTokenValiditySeconds?: number;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    initiateLoginUri?: string;
    /**
     *
     * @type {JWKSet}
     * @memberof ApplicationOAuthSettings
     */
    jwks?: JWKSet;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    jwksUri?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    logoUri?: string;
    /**
     *
     * @type {boolean}
     * @memberof ApplicationOAuthSettings
     */
    optInScopeSelection?: boolean;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    policyUri?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof ApplicationOAuthSettings
     */
    postLogoutRedirectUris?: Array<string>;
    /**
     *
     * @type {Array<string>}
     * @memberof ApplicationOAuthSettings
     */
    redirectUris?: Array<string>;
    /**
     *
     * @type {number}
     * @memberof ApplicationOAuthSettings
     */
    refreshTokenValiditySeconds?: number;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    registrationAccessToken?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    registrationClientUri?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    requestObjectEncryptionAlg?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    requestObjectEncryptionEnc?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    requestObjectSigningAlg?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof ApplicationOAuthSettings
     */
    requestUris?: Array<string>;
    /**
     *
     * @type {boolean}
     * @memberof ApplicationOAuthSettings
     */
    requireAuthTime?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof ApplicationOAuthSettings
     */
    requireParRequest?: boolean;
    /**
     *
     * @type {Array<string>}
     * @memberof ApplicationOAuthSettings
     */
    responseTypes?: Array<string>;
    /**
     *
     * @type {{ [key: string]: number; }}
     * @memberof ApplicationOAuthSettings
     */
    scopeApprovals?: {
        [key: string]: number;
    };
    /**
     *
     * @type {Array<ApplicationScopeSettings>}
     * @memberof ApplicationOAuthSettings
     */
    scopeSettings?: Array<ApplicationScopeSettings>;
    /**
     *
     * @type {Array<string>}
     * @memberof ApplicationOAuthSettings
     */
    scopes?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    sectorIdentifierUri?: string;
    /**
     *
     * @type {boolean}
     * @memberof ApplicationOAuthSettings
     */
    silentReAuthentication?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof ApplicationOAuthSettings
     */
    singleSignOut?: boolean;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    softwareId?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    softwareStatement?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    softwareVersion?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    subjectType?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    tlsClientAuthSanDns?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    tlsClientAuthSanEmail?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    tlsClientAuthSanIp?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    tlsClientAuthSanUri?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    tlsClientAuthSubjectDn?: string;
    /**
     *
     * @type {boolean}
     * @memberof ApplicationOAuthSettings
     */
    tlsClientCertificateBoundAccessTokens?: boolean;
    /**
     *
     * @type {Array<TokenClaim>}
     * @memberof ApplicationOAuthSettings
     */
    tokenCustomClaims?: Array<TokenClaim>;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    tokenEndpointAuthMethod?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    tokenEndpointAuthSigningAlg?: string;
    /**
     *
     * @type {TokenExchangeOAuthSettings}
     * @memberof ApplicationOAuthSettings
     */
    tokenExchangeOAuthSettings?: TokenExchangeOAuthSettings;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    tosUri?: string;
    /**
     *
     * @type {Array<UserInfoClaim>}
     * @memberof ApplicationOAuthSettings
     */
    userinfoCustomClaims?: Array<UserInfoClaim>;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    userinfoEncryptedResponseAlg?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    userinfoEncryptedResponseEnc?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    userinfoSignedResponseAlg?: string;
}
/**
 * Check if a given object implements the ApplicationOAuthSettings interface.
 */
export declare function instanceOfApplicationOAuthSettings(value: object): value is ApplicationOAuthSettings;
export declare function ApplicationOAuthSettingsFromJSON(json: any): ApplicationOAuthSettings;
export declare function ApplicationOAuthSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): ApplicationOAuthSettings;
export declare function ApplicationOAuthSettingsToJSON(json: any): ApplicationOAuthSettings;
export declare function ApplicationOAuthSettingsToJSONTyped(value?: ApplicationOAuthSettings | null, ignoreDiscriminator?: boolean): any;
