/* tslint:disable */
/* eslint-disable */
/**
 * Gravitee.io - Access Management API
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */

import { exists, mapValues } from '../runtime';
import {
    ApplicationScopeSettings,
    ApplicationScopeSettingsFromJSON,
    ApplicationScopeSettingsFromJSONTyped,
    ApplicationScopeSettingsToJSON,
} from './ApplicationScopeSettings';
import {
    JWKSet,
    JWKSetFromJSON,
    JWKSetFromJSONTyped,
    JWKSetToJSON,
} from './JWKSet';
import {
    TokenClaim,
    TokenClaimFromJSON,
    TokenClaimFromJSONTyped,
    TokenClaimToJSON,
} from './TokenClaim';

/**
 * 
 * @export
 * @interface ApplicationOAuthSettings
 */
export interface ApplicationOAuthSettings {
    /**
     * 
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    clientId?: string;
    /**
     * 
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    clientSecret?: string;
    /**
     * 
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    clientType?: string;
    /**
     * 
     * @type {Array<string>}
     * @memberof ApplicationOAuthSettings
     */
    redirectUris?: Array<string>;
    /**
     * 
     * @type {Array<string>}
     * @memberof ApplicationOAuthSettings
     */
    responseTypes?: Array<string>;
    /**
     * 
     * @type {Array<string>}
     * @memberof ApplicationOAuthSettings
     */
    grantTypes?: Array<string>;
    /**
     * 
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    applicationType?: string;
    /**
     * 
     * @type {Array<string>}
     * @memberof ApplicationOAuthSettings
     */
    contacts?: Array<string>;
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
    logoUri?: string;
    /**
     * 
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    clientUri?: string;
    /**
     * 
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    policyUri?: string;
    /**
     * 
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    tosUri?: string;
    /**
     * 
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    jwksUri?: string;
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
    sectorIdentifierUri?: string;
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
    idTokenSignedResponseAlg?: string;
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
    userinfoSignedResponseAlg?: string;
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
    requestObjectSigningAlg?: string;
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
    tokenEndpointAuthMethod?: string;
    /**
     * 
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    tokenEndpointAuthSigningAlg?: string;
    /**
     * 
     * @type {number}
     * @memberof ApplicationOAuthSettings
     */
    defaultMaxAge?: number;
    /**
     * 
     * @type {boolean}
     * @memberof ApplicationOAuthSettings
     */
    requireAuthTime?: boolean;
    /**
     * 
     * @type {Array<string>}
     * @memberof ApplicationOAuthSettings
     */
    defaultACRvalues?: Array<string>;
    /**
     * 
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    initiateLoginUri?: string;
    /**
     * 
     * @type {Array<string>}
     * @memberof ApplicationOAuthSettings
     */
    requestUris?: Array<string>;
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
    softwareVersion?: string;
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
    registrationAccessToken?: string;
    /**
     * 
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    registrationClientUri?: string;
    /**
     * 
     * @type {number}
     * @memberof ApplicationOAuthSettings
     */
    clientIdIssuedAt?: number;
    /**
     * 
     * @type {number}
     * @memberof ApplicationOAuthSettings
     */
    clientSecretExpiresAt?: number;
    /**
     * 
     * @type {Array<string>}
     * @memberof ApplicationOAuthSettings
     */
    scopes?: Array<string>;
    /**
     * 
     * @type {Array<string>}
     * @memberof ApplicationOAuthSettings
     */
    defaultScopes?: Array<string>;
    /**
     * 
     * @type {{ [key: string]: number; }}
     * @memberof ApplicationOAuthSettings
     */
    scopeApprovals?: { [key: string]: number; };
    /**
     * 
     * @type {Array<ApplicationScopeSettings>}
     * @memberof ApplicationOAuthSettings
     */
    scopeSettings?: Array<ApplicationScopeSettings>;
    /**
     * 
     * @type {boolean}
     * @memberof ApplicationOAuthSettings
     */
    enhanceScopesWithUserPermissions?: boolean;
    /**
     * 
     * @type {number}
     * @memberof ApplicationOAuthSettings
     */
    accessTokenValiditySeconds?: number;
    /**
     * 
     * @type {number}
     * @memberof ApplicationOAuthSettings
     */
    refreshTokenValiditySeconds?: number;
    /**
     * 
     * @type {number}
     * @memberof ApplicationOAuthSettings
     */
    idTokenValiditySeconds?: number;
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
    tlsClientAuthSubjectDn?: string;
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
    tlsClientAuthSanUri?: string;
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
    tlsClientAuthSanEmail?: string;
    /**
     * 
     * @type {boolean}
     * @memberof ApplicationOAuthSettings
     */
    tlsClientCertificateBoundAccessTokens?: boolean;
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
    authorizationEncryptedResponseAlg?: string;
    /**
     * 
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    authorizationEncryptedResponseEnc?: string;
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
    postLogoutRedirectUris?: Array<string>;
    /**
     * 
     * @type {boolean}
     * @memberof ApplicationOAuthSettings
     */
    singleSignOut?: boolean;
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
    requireParRequest?: boolean;
    /**
     * 
     * @type {string}
     * @memberof ApplicationOAuthSettings
     */
    backchannelTokenDeliveryMode?: string;
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
    backchannelAuthRequestSignAlg?: string;
    /**
     * 
     * @type {boolean}
     * @memberof ApplicationOAuthSettings
     */
    backchannelUserCodeParameter?: boolean;
    /**
     * 
     * @type {boolean}
     * @memberof ApplicationOAuthSettings
     */
    disableRefreshTokenRotation?: boolean;
}

export function ApplicationOAuthSettingsFromJSON(json: any): ApplicationOAuthSettings {
    return ApplicationOAuthSettingsFromJSONTyped(json, false);
}

export function ApplicationOAuthSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): ApplicationOAuthSettings {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'clientId': !exists(json, 'clientId') ? undefined : json['clientId'],
        'clientSecret': !exists(json, 'clientSecret') ? undefined : json['clientSecret'],
        'clientType': !exists(json, 'clientType') ? undefined : json['clientType'],
        'redirectUris': !exists(json, 'redirectUris') ? undefined : json['redirectUris'],
        'responseTypes': !exists(json, 'responseTypes') ? undefined : json['responseTypes'],
        'grantTypes': !exists(json, 'grantTypes') ? undefined : json['grantTypes'],
        'applicationType': !exists(json, 'applicationType') ? undefined : json['applicationType'],
        'contacts': !exists(json, 'contacts') ? undefined : json['contacts'],
        'clientName': !exists(json, 'clientName') ? undefined : json['clientName'],
        'logoUri': !exists(json, 'logoUri') ? undefined : json['logoUri'],
        'clientUri': !exists(json, 'clientUri') ? undefined : json['clientUri'],
        'policyUri': !exists(json, 'policyUri') ? undefined : json['policyUri'],
        'tosUri': !exists(json, 'tosUri') ? undefined : json['tosUri'],
        'jwksUri': !exists(json, 'jwksUri') ? undefined : json['jwksUri'],
        'jwks': !exists(json, 'jwks') ? undefined : JWKSetFromJSON(json['jwks']),
        'sectorIdentifierUri': !exists(json, 'sectorIdentifierUri') ? undefined : json['sectorIdentifierUri'],
        'subjectType': !exists(json, 'subjectType') ? undefined : json['subjectType'],
        'idTokenSignedResponseAlg': !exists(json, 'idTokenSignedResponseAlg') ? undefined : json['idTokenSignedResponseAlg'],
        'idTokenEncryptedResponseAlg': !exists(json, 'idTokenEncryptedResponseAlg') ? undefined : json['idTokenEncryptedResponseAlg'],
        'idTokenEncryptedResponseEnc': !exists(json, 'idTokenEncryptedResponseEnc') ? undefined : json['idTokenEncryptedResponseEnc'],
        'userinfoSignedResponseAlg': !exists(json, 'userinfoSignedResponseAlg') ? undefined : json['userinfoSignedResponseAlg'],
        'userinfoEncryptedResponseAlg': !exists(json, 'userinfoEncryptedResponseAlg') ? undefined : json['userinfoEncryptedResponseAlg'],
        'userinfoEncryptedResponseEnc': !exists(json, 'userinfoEncryptedResponseEnc') ? undefined : json['userinfoEncryptedResponseEnc'],
        'requestObjectSigningAlg': !exists(json, 'requestObjectSigningAlg') ? undefined : json['requestObjectSigningAlg'],
        'requestObjectEncryptionAlg': !exists(json, 'requestObjectEncryptionAlg') ? undefined : json['requestObjectEncryptionAlg'],
        'requestObjectEncryptionEnc': !exists(json, 'requestObjectEncryptionEnc') ? undefined : json['requestObjectEncryptionEnc'],
        'tokenEndpointAuthMethod': !exists(json, 'tokenEndpointAuthMethod') ? undefined : json['tokenEndpointAuthMethod'],
        'tokenEndpointAuthSigningAlg': !exists(json, 'tokenEndpointAuthSigningAlg') ? undefined : json['tokenEndpointAuthSigningAlg'],
        'defaultMaxAge': !exists(json, 'defaultMaxAge') ? undefined : json['defaultMaxAge'],
        'requireAuthTime': !exists(json, 'requireAuthTime') ? undefined : json['requireAuthTime'],
        'defaultACRvalues': !exists(json, 'defaultACRvalues') ? undefined : json['defaultACRvalues'],
        'initiateLoginUri': !exists(json, 'initiateLoginUri') ? undefined : json['initiateLoginUri'],
        'requestUris': !exists(json, 'requestUris') ? undefined : json['requestUris'],
        'softwareId': !exists(json, 'softwareId') ? undefined : json['softwareId'],
        'softwareVersion': !exists(json, 'softwareVersion') ? undefined : json['softwareVersion'],
        'softwareStatement': !exists(json, 'softwareStatement') ? undefined : json['softwareStatement'],
        'registrationAccessToken': !exists(json, 'registrationAccessToken') ? undefined : json['registrationAccessToken'],
        'registrationClientUri': !exists(json, 'registrationClientUri') ? undefined : json['registrationClientUri'],
        'clientIdIssuedAt': !exists(json, 'clientIdIssuedAt') ? undefined : json['clientIdIssuedAt'],
        'clientSecretExpiresAt': !exists(json, 'clientSecretExpiresAt') ? undefined : json['clientSecretExpiresAt'],
        'scopes': !exists(json, 'scopes') ? undefined : json['scopes'],
        'defaultScopes': !exists(json, 'defaultScopes') ? undefined : json['defaultScopes'],
        'scopeApprovals': !exists(json, 'scopeApprovals') ? undefined : json['scopeApprovals'],
        'scopeSettings': !exists(json, 'scopeSettings') ? undefined : ((json['scopeSettings'] as Array<any>).map(ApplicationScopeSettingsFromJSON)),
        'enhanceScopesWithUserPermissions': !exists(json, 'enhanceScopesWithUserPermissions') ? undefined : json['enhanceScopesWithUserPermissions'],
        'accessTokenValiditySeconds': !exists(json, 'accessTokenValiditySeconds') ? undefined : json['accessTokenValiditySeconds'],
        'refreshTokenValiditySeconds': !exists(json, 'refreshTokenValiditySeconds') ? undefined : json['refreshTokenValiditySeconds'],
        'idTokenValiditySeconds': !exists(json, 'idTokenValiditySeconds') ? undefined : json['idTokenValiditySeconds'],
        'tokenCustomClaims': !exists(json, 'tokenCustomClaims') ? undefined : ((json['tokenCustomClaims'] as Array<any>).map(TokenClaimFromJSON)),
        'tlsClientAuthSubjectDn': !exists(json, 'tlsClientAuthSubjectDn') ? undefined : json['tlsClientAuthSubjectDn'],
        'tlsClientAuthSanDns': !exists(json, 'tlsClientAuthSanDns') ? undefined : json['tlsClientAuthSanDns'],
        'tlsClientAuthSanUri': !exists(json, 'tlsClientAuthSanUri') ? undefined : json['tlsClientAuthSanUri'],
        'tlsClientAuthSanIp': !exists(json, 'tlsClientAuthSanIp') ? undefined : json['tlsClientAuthSanIp'],
        'tlsClientAuthSanEmail': !exists(json, 'tlsClientAuthSanEmail') ? undefined : json['tlsClientAuthSanEmail'],
        'tlsClientCertificateBoundAccessTokens': !exists(json, 'tlsClientCertificateBoundAccessTokens') ? undefined : json['tlsClientCertificateBoundAccessTokens'],
        'authorizationSignedResponseAlg': !exists(json, 'authorizationSignedResponseAlg') ? undefined : json['authorizationSignedResponseAlg'],
        'authorizationEncryptedResponseAlg': !exists(json, 'authorizationEncryptedResponseAlg') ? undefined : json['authorizationEncryptedResponseAlg'],
        'authorizationEncryptedResponseEnc': !exists(json, 'authorizationEncryptedResponseEnc') ? undefined : json['authorizationEncryptedResponseEnc'],
        'forcePKCE': !exists(json, 'forcePKCE') ? undefined : json['forcePKCE'],
        'forceS256CodeChallengeMethod': !exists(json, 'forceS256CodeChallengeMethod') ? undefined : json['forceS256CodeChallengeMethod'],
        'postLogoutRedirectUris': !exists(json, 'postLogoutRedirectUris') ? undefined : json['postLogoutRedirectUris'],
        'singleSignOut': !exists(json, 'singleSignOut') ? undefined : json['singleSignOut'],
        'silentReAuthentication': !exists(json, 'silentReAuthentication') ? undefined : json['silentReAuthentication'],
        'requireParRequest': !exists(json, 'requireParRequest') ? undefined : json['requireParRequest'],
        'backchannelTokenDeliveryMode': !exists(json, 'backchannelTokenDeliveryMode') ? undefined : json['backchannelTokenDeliveryMode'],
        'backchannelClientNotificationEndpoint': !exists(json, 'backchannelClientNotificationEndpoint') ? undefined : json['backchannelClientNotificationEndpoint'],
        'backchannelAuthRequestSignAlg': !exists(json, 'backchannelAuthRequestSignAlg') ? undefined : json['backchannelAuthRequestSignAlg'],
        'backchannelUserCodeParameter': !exists(json, 'backchannelUserCodeParameter') ? undefined : json['backchannelUserCodeParameter'],
        'disableRefreshTokenRotation': !exists(json, 'disableRefreshTokenRotation') ? undefined : json['disableRefreshTokenRotation'],
    };
}

export function ApplicationOAuthSettingsToJSON(value?: ApplicationOAuthSettings | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
        'clientId': value.clientId,
        'clientSecret': value.clientSecret,
        'clientType': value.clientType,
        'redirectUris': value.redirectUris,
        'responseTypes': value.responseTypes,
        'grantTypes': value.grantTypes,
        'applicationType': value.applicationType,
        'contacts': value.contacts,
        'clientName': value.clientName,
        'logoUri': value.logoUri,
        'clientUri': value.clientUri,
        'policyUri': value.policyUri,
        'tosUri': value.tosUri,
        'jwksUri': value.jwksUri,
        'jwks': JWKSetToJSON(value.jwks),
        'sectorIdentifierUri': value.sectorIdentifierUri,
        'subjectType': value.subjectType,
        'idTokenSignedResponseAlg': value.idTokenSignedResponseAlg,
        'idTokenEncryptedResponseAlg': value.idTokenEncryptedResponseAlg,
        'idTokenEncryptedResponseEnc': value.idTokenEncryptedResponseEnc,
        'userinfoSignedResponseAlg': value.userinfoSignedResponseAlg,
        'userinfoEncryptedResponseAlg': value.userinfoEncryptedResponseAlg,
        'userinfoEncryptedResponseEnc': value.userinfoEncryptedResponseEnc,
        'requestObjectSigningAlg': value.requestObjectSigningAlg,
        'requestObjectEncryptionAlg': value.requestObjectEncryptionAlg,
        'requestObjectEncryptionEnc': value.requestObjectEncryptionEnc,
        'tokenEndpointAuthMethod': value.tokenEndpointAuthMethod,
        'tokenEndpointAuthSigningAlg': value.tokenEndpointAuthSigningAlg,
        'defaultMaxAge': value.defaultMaxAge,
        'requireAuthTime': value.requireAuthTime,
        'defaultACRvalues': value.defaultACRvalues,
        'initiateLoginUri': value.initiateLoginUri,
        'requestUris': value.requestUris,
        'softwareId': value.softwareId,
        'softwareVersion': value.softwareVersion,
        'softwareStatement': value.softwareStatement,
        'registrationAccessToken': value.registrationAccessToken,
        'registrationClientUri': value.registrationClientUri,
        'clientIdIssuedAt': value.clientIdIssuedAt,
        'clientSecretExpiresAt': value.clientSecretExpiresAt,
        'scopes': value.scopes,
        'defaultScopes': value.defaultScopes,
        'scopeApprovals': value.scopeApprovals,
        'scopeSettings': value.scopeSettings === undefined ? undefined : ((value.scopeSettings as Array<any>).map(ApplicationScopeSettingsToJSON)),
        'enhanceScopesWithUserPermissions': value.enhanceScopesWithUserPermissions,
        'accessTokenValiditySeconds': value.accessTokenValiditySeconds,
        'refreshTokenValiditySeconds': value.refreshTokenValiditySeconds,
        'idTokenValiditySeconds': value.idTokenValiditySeconds,
        'tokenCustomClaims': value.tokenCustomClaims === undefined ? undefined : ((value.tokenCustomClaims as Array<any>).map(TokenClaimToJSON)),
        'tlsClientAuthSubjectDn': value.tlsClientAuthSubjectDn,
        'tlsClientAuthSanDns': value.tlsClientAuthSanDns,
        'tlsClientAuthSanUri': value.tlsClientAuthSanUri,
        'tlsClientAuthSanIp': value.tlsClientAuthSanIp,
        'tlsClientAuthSanEmail': value.tlsClientAuthSanEmail,
        'tlsClientCertificateBoundAccessTokens': value.tlsClientCertificateBoundAccessTokens,
        'authorizationSignedResponseAlg': value.authorizationSignedResponseAlg,
        'authorizationEncryptedResponseAlg': value.authorizationEncryptedResponseAlg,
        'authorizationEncryptedResponseEnc': value.authorizationEncryptedResponseEnc,
        'forcePKCE': value.forcePKCE,
        'forceS256CodeChallengeMethod': value.forceS256CodeChallengeMethod,
        'postLogoutRedirectUris': value.postLogoutRedirectUris,
        'singleSignOut': value.singleSignOut,
        'silentReAuthentication': value.silentReAuthentication,
        'requireParRequest': value.requireParRequest,
        'backchannelTokenDeliveryMode': value.backchannelTokenDeliveryMode,
        'backchannelClientNotificationEndpoint': value.backchannelClientNotificationEndpoint,
        'backchannelAuthRequestSignAlg': value.backchannelAuthRequestSignAlg,
        'backchannelUserCodeParameter': value.backchannelUserCodeParameter,
        'disableRefreshTokenRotation': value.disableRefreshTokenRotation,
    };
}

