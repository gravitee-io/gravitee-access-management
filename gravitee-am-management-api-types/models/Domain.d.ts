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
import type { SCIMSettings } from './SCIMSettings';
import type { ManagedBy } from './ManagedBy';
import type { CertificateSettings } from './CertificateSettings';
import type { CorsSettings } from './CorsSettings';
import type { SecretExpirationSettings } from './SecretExpirationSettings';
import type { OIDCSettings } from './OIDCSettings';
import type { WebAuthnSettings } from './WebAuthnSettings';
import type { UMASettings } from './UMASettings';
import type { AccountSettings } from './AccountSettings';
import type { KeyRetrievalSettings } from './KeyRetrievalSettings';
import type { SelfServiceAccountManagementSettings } from './SelfServiceAccountManagementSettings';
import type { LoginSettings } from './LoginSettings';
import type { TokenExchangeSettings } from './TokenExchangeSettings';
import type { WebProtectionSettings } from './WebProtectionSettings';
import type { VirtualHost } from './VirtualHost';
import type { PasswordSettings } from './PasswordSettings';
import type { SAMLSettings } from './SAMLSettings';
/**
 *
 * @export
 * @interface Domain
 */
export interface Domain {
    /**
     *
     * @type {AccountSettings}
     * @memberof Domain
     */
    accountSettings?: AccountSettings;
    /**
     *
     * @type {boolean}
     * @memberof Domain
     */
    alertEnabled?: boolean;
    /**
     *
     * @type {CertificateSettings}
     * @memberof Domain
     */
    certificateSettings?: CertificateSettings;
    /**
     *
     * @type {CorsSettings}
     * @memberof Domain
     */
    corsSettings?: CorsSettings;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Domain
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof Domain
     */
    dataPlaneId?: string;
    /**
     *
     * @type {string}
     * @memberof Domain
     */
    description?: string;
    /**
     *
     * @type {boolean}
     * @memberof Domain
     */
    dynamicClientRegistrationEnabled?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof Domain
     */
    dynamicClientRegistrationTemplateEnabled?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof Domain
     */
    enabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof Domain
     */
    hrid?: string;
    /**
     *
     * @type {string}
     * @memberof Domain
     */
    id?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof Domain
     */
    identities?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof Domain
     */
    readonly key?: string;
    /**
     *
     * @type {KeyRetrievalSettings}
     * @memberof Domain
     */
    keyRetrievalSettings?: KeyRetrievalSettings;
    /**
     *
     * @type {LoginSettings}
     * @memberof Domain
     */
    loginSettings?: LoginSettings;
    /**
     *
     * @type {ManagedBy}
     * @memberof Domain
     */
    managedBy?: ManagedBy;
    /**
     *
     * @type {boolean}
     * @memberof Domain
     */
    master?: boolean;
    /**
     *
     * @type {string}
     * @memberof Domain
     */
    name?: string;
    /**
     *
     * @type {OIDCSettings}
     * @memberof Domain
     */
    oidc?: OIDCSettings;
    /**
     *
     * @type {boolean}
     * @memberof Domain
     */
    openDynamicClientRegistrationEnabled?: boolean;
    /**
     *
     * @type {PasswordSettings}
     * @memberof Domain
     */
    passwordSettings?: PasswordSettings;
    /**
     *
     * @type {string}
     * @memberof Domain
     */
    path?: string;
    /**
     *
     * @type {boolean}
     * @memberof Domain
     */
    redirectUriExpressionLanguageEnabled?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof Domain
     */
    redirectUriLocalhostAllowed?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof Domain
     */
    redirectUriStrictMatching?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof Domain
     */
    redirectUriUnsecuredHttpSchemeAllowed?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof Domain
     */
    redirectUriWildcardAllowed?: boolean;
    /**
     *
     * @type {string}
     * @memberof Domain
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof Domain
     */
    referenceType?: DomainReferenceTypeEnum;
    /**
     *
     * @type {boolean}
     * @memberof Domain
     */
    requireDpopForAll?: boolean;
    /**
     *
     * @type {SAMLSettings}
     * @memberof Domain
     */
    saml?: SAMLSettings;
    /**
     *
     * @type {SCIMSettings}
     * @memberof Domain
     */
    scim?: SCIMSettings;
    /**
     *
     * @type {SecretExpirationSettings}
     * @memberof Domain
     */
    secretExpirationSettings?: SecretExpirationSettings;
    /**
     *
     * @type {SelfServiceAccountManagementSettings}
     * @memberof Domain
     */
    selfServiceAccountManagementSettings?: SelfServiceAccountManagementSettings;
    /**
     *
     * @type {Array<string>}
     * @memberof Domain
     */
    tags?: Array<string>;
    /**
     *
     * @type {TokenExchangeSettings}
     * @memberof Domain
     */
    tokenExchangeSettings?: TokenExchangeSettings;
    /**
     *
     * @type {UMASettings}
     * @memberof Domain
     */
    uma?: UMASettings;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Domain
     */
    updatedAt?: number;
    /**
     *
     * @type {string}
     * @memberof Domain
     */
    version?: DomainVersionEnum;
    /**
     *
     * @type {boolean}
     * @memberof Domain
     */
    vhostMode?: boolean;
    /**
     *
     * @type {Array<VirtualHost>}
     * @memberof Domain
     */
    vhosts?: Array<VirtualHost>;
    /**
     *
     * @type {WebAuthnSettings}
     * @memberof Domain
     */
    webAuthnSettings?: WebAuthnSettings;
    /**
     *
     * @type {WebProtectionSettings}
     * @memberof Domain
     */
    webProtectionSettings?: WebProtectionSettings;
}
/**
 * @export
 */
export declare const DomainReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type DomainReferenceTypeEnum = typeof DomainReferenceTypeEnum[keyof typeof DomainReferenceTypeEnum];
/**
 * @export
 */
export declare const DomainVersionEnum: {
    readonly V10: "V1_0";
    readonly V20: "V2_0";
};
export type DomainVersionEnum = typeof DomainVersionEnum[keyof typeof DomainVersionEnum];
/**
 * Check if a given object implements the Domain interface.
 */
export declare function instanceOfDomain(value: object): value is Domain;
export declare function DomainFromJSON(json: any): Domain;
export declare function DomainFromJSONTyped(json: any, ignoreDiscriminator: boolean): Domain;
export declare function DomainToJSON(json: any): Domain;
export declare function DomainToJSONTyped(value?: Omit<Domain, 'key'> | null, ignoreDiscriminator?: boolean): any;
