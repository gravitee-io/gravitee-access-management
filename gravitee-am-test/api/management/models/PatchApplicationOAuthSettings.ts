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
 * @interface PatchApplicationOAuthSettings
 */
export interface PatchApplicationOAuthSettings {
    /**
     * 
     * @type {Array<string>}
     * @memberof PatchApplicationOAuthSettings
     */
    redirectUris?: Array<string>;
    /**
     * 
     * @type {Array<string>}
     * @memberof PatchApplicationOAuthSettings
     */
    responseTypes?: Array<string>;
    /**
     * 
     * @type {Array<string>}
     * @memberof PatchApplicationOAuthSettings
     */
    grantTypes?: Array<string>;
    /**
     * 
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    applicationType?: string;
    /**
     * 
     * @type {Array<string>}
     * @memberof PatchApplicationOAuthSettings
     */
    contacts?: Array<string>;
    /**
     * 
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    clientName?: string;
    /**
     * 
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    logoUri?: string;
    /**
     * 
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    clientUri?: string;
    /**
     * 
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    policyUri?: string;
    /**
     * 
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    tosUri?: string;
    /**
     * 
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    jwksUri?: string;
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
    sectorIdentifierUri?: string;
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
    idTokenSignedResponseAlg?: string;
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
    userinfoSignedResponseAlg?: string;
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
    requestObjectSigningAlg?: string;
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
    tokenEndpointAuthMethod?: string;
    /**
     * 
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    tokenEndpointAuthSigningAlg?: string;
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
    requireAuthTime?: boolean;
    /**
     * 
     * @type {Array<string>}
     * @memberof PatchApplicationOAuthSettings
     */
    defaultACRvalues?: Array<string>;
    /**
     * 
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    initiateLoginUri?: string;
    /**
     * 
     * @type {Array<string>}
     * @memberof PatchApplicationOAuthSettings
     */
    requestUris?: Array<string>;
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
    softwareVersion?: string;
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
    registrationAccessToken?: string;
    /**
     * 
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    registrationClientUri?: string;
    /**
     * 
     * @type {Date}
     * @memberof PatchApplicationOAuthSettings
     */
    clientIdIssuedAt?: Date;
    /**
     * 
     * @type {Date}
     * @memberof PatchApplicationOAuthSettings
     */
    clientSecretExpiresAt?: Date;
    /**
     * 
     * @type {boolean}
     * @memberof PatchApplicationOAuthSettings
     */
    enhanceScopesWithUserPermissions?: boolean;
    /**
     * 
     * @type {number}
     * @memberof PatchApplicationOAuthSettings
     */
    accessTokenValiditySeconds?: number;
    /**
     * 
     * @type {number}
     * @memberof PatchApplicationOAuthSettings
     */
    refreshTokenValiditySeconds?: number;
    /**
     * 
     * @type {number}
     * @memberof PatchApplicationOAuthSettings
     */
    idTokenValiditySeconds?: number;
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
    tlsClientAuthSubjectDn?: string;
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
    tlsClientAuthSanUri?: string;
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
    tlsClientAuthSanEmail?: string;
    /**
     * 
     * @type {boolean}
     * @memberof PatchApplicationOAuthSettings
     */
    tlsClientCertificateBoundAccessTokens?: boolean;
    /**
     * 
     * @type {string}
     * @memberof PatchApplicationOAuthSettings
     */
    authorizationSignedResponseAlg?: string;
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
     * @type {boolean}
     * @memberof PatchApplicationOAuthSettings
     */
    forcePKCE?: boolean;
    /**
     * 
     * @type {Array<string>}
     * @memberof PatchApplicationOAuthSettings
     */
    postLogoutRedirectUris?: Array<string>;
    /**
     * 
     * @type {boolean}
     * @memberof PatchApplicationOAuthSettings
     */
    singleSignOut?: boolean;
    /**
     * 
     * @type {boolean}
     * @memberof PatchApplicationOAuthSettings
     */
    silentReAuthentication?: boolean;
    /**
     * 
     * @type {Array<ApplicationScopeSettings>}
     * @memberof PatchApplicationOAuthSettings
     */
    scopeSettings?: Array<ApplicationScopeSettings>;
}

export function PatchApplicationOAuthSettingsFromJSON(json: any): PatchApplicationOAuthSettings {
    return PatchApplicationOAuthSettingsFromJSONTyped(json, false);
}

export function PatchApplicationOAuthSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchApplicationOAuthSettings {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
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
        'clientIdIssuedAt': !exists(json, 'clientIdIssuedAt') ? undefined : (new Date(json['clientIdIssuedAt'])),
        'clientSecretExpiresAt': !exists(json, 'clientSecretExpiresAt') ? undefined : (new Date(json['clientSecretExpiresAt'])),
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
        'postLogoutRedirectUris': !exists(json, 'postLogoutRedirectUris') ? undefined : json['postLogoutRedirectUris'],
        'singleSignOut': !exists(json, 'singleSignOut') ? undefined : json['singleSignOut'],
        'silentReAuthentication': !exists(json, 'silentReAuthentication') ? undefined : json['silentReAuthentication'],
        'scopeSettings': !exists(json, 'scopeSettings') ? undefined : ((json['scopeSettings'] as Array<any>).map(ApplicationScopeSettingsFromJSON)),
    };
}

export function PatchApplicationOAuthSettingsToJSON(value?: PatchApplicationOAuthSettings | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
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
        'clientIdIssuedAt': value.clientIdIssuedAt === undefined ? undefined : (value.clientIdIssuedAt.toISOString()),
        'clientSecretExpiresAt': value.clientSecretExpiresAt === undefined ? undefined : (value.clientSecretExpiresAt.toISOString()),
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
        'postLogoutRedirectUris': value.postLogoutRedirectUris,
        'singleSignOut': value.singleSignOut,
        'silentReAuthentication': value.silentReAuthentication,
        'scopeSettings': value.scopeSettings === undefined ? undefined : ((value.scopeSettings as Array<any>).map(ApplicationScopeSettingsToJSON)),
    };
}

