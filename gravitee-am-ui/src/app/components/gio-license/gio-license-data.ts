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
export enum AmFeature {
  AM_MFA_SMS = 'am-mfa-sms',
  AM_MFA_CALL = 'am-mfa-call',
  AM_MFA_FIDO2 = 'am-mfa-fido2',
  AM_MFA_RESOURCE_HTTP_FACTOR = 'am-mfa-resource-http-factor',
  AM_MFA_HTTP = 'am-mfa-http',
  AM_MFA_RECOVERY_CODE = 'am-mfa-recovery-code',
  AM_RESOURCE_HTTP_FACTOR = 'am-resource-http-factor',
  AM_MFA_OTP_SENDER = 'am-mfa-otp-sender',
  AM_RESOURCE_TWILIO = 'am-resource-twilio',
  AM_IDP_SALESFORCE = 'am-idp-salesforce',
  AM_IDP_SAML = 'am-idp-saml',
  AM_IDP_LDAP = 'am-idp-ldap',
  AM_IDP_KERBEROS = 'am-idp-kerberos',
  AM_IDP_AZURE_AD = 'am-idp-azure_ad',
  AM_IDP_FRANCE_CONNECT = 'am-idp-kerberos',
  AM_IDP_CAS = 'am-idp-cas',
  AM_IDP_GATEWAY_HANDLER_SAML = 'am-idp-gateway-handler-saml',
  AM_IDP_HTTP_FLOW = 'am-idp-http-flow',
  AM_GRAVITEE_RISK_ASSESSMENT = 'gravitee-risk-assessment',
  AM_IDP_SAML2 = 'am-idp-saml2',
}

export const FeatureInfoData: Record<AmFeature, FeatureInfo> = {
  [AmFeature.AM_MFA_SMS]: {
    image: 'assets/gio-ee-unlock-dialog/am-mfa-sms.svg',
    description:
      'The SMS factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  [AmFeature.AM_MFA_CALL]: {
    image: 'assets/gio-ee-unlock-dialog/am-mfa-call.svg',
    description:
      'The Call factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  [AmFeature.AM_MFA_FIDO2]: {
    image: 'assets/gio-ee-unlock-dialog/am-mfa-fido2.svg',
    description:
      'The FIDO2 factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  [AmFeature.AM_MFA_RESOURCE_HTTP_FACTOR]: {
    image: 'assets/gio-ee-unlock-dialog/am-mfa-http.svg',
    description:
      'The HTTP factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  [AmFeature.AM_MFA_HTTP]: {
    image: 'assets/gio-ee-unlock-dialog/am-mfa-http.svg',
    description:
      'The HTTP factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  [AmFeature.AM_MFA_RECOVERY_CODE]: {
    image: 'assets/gio-license/am-mfa-recovery-code.svg',
    description:
      'The Recovery code factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  [AmFeature.AM_RESOURCE_HTTP_FACTOR]: {
    image: 'assets/gio-license/am-mfa-http.svg',
    description:
      'The HTTP factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  [AmFeature.AM_MFA_OTP_SENDER]: {
    image: 'assets/gio-license/gravitee-ee-upgrade.svg',
    description:
      'The OTO Sender is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  [AmFeature.AM_RESOURCE_TWILIO]: {
    image: 'assets/gio-license/gravitee-ee-upgrade.svg',
    description:
      'The Twilio resource is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  // IDPs
  [AmFeature.AM_IDP_SALESFORCE]: {
    image: 'assets/gio-license/am-idp-salesforce.svg',
    description:
      'The Salesforce identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  [AmFeature.AM_IDP_SAML]: {
    image: 'assets/gio-license/am-idp-saml.svg',
    description:
      'The SAML 2.0 identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  [AmFeature.AM_IDP_LDAP]: {
    image: 'assets/gio-license/am-idp-ldap.svg',
    description:
      'The LDAP identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  [AmFeature.AM_IDP_KERBEROS]: {
    image: 'assets/gio-license/am-idp-kerberos.svg',
    description:
      'The Kerberos identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  [AmFeature.AM_IDP_AZURE_AD]: {
    image: 'assets/gio-license/am-idp-azure-ad.svg',
    description:
      'The Azure AD identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  [AmFeature.AM_IDP_FRANCE_CONNECT]: {
    image: 'assets/gio-license/am-idp-france-connect.svg',
    description:
      'The France Connect identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  [AmFeature.AM_IDP_CAS]: {
    image: 'assets/gio-license/am-idp-cas.svg',
    description:
      'The CAS provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  [AmFeature.AM_IDP_GATEWAY_HANDLER_SAML]: {
    image: 'assets/gio-license/gravitee-ee-upgrade.svg',
    description:
      'The SAML 2.0 identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  [AmFeature.AM_IDP_HTTP_FLOW]: {
    image: 'assets/gio-license/gravitee-ee-upgrade.svg',
    description:
      'The HTTP Flow identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  // MFA
  [AmFeature.AM_GRAVITEE_RISK_ASSESSMENT]: {
    image: 'assets/gio-license/gravitee-risk-assessment.svg',
    description:
      'Risk-based Multi-factor authentication is part of Gravitee Enterprise. MFA allows you to prompt end-users to process MFA verification after they have been authenticated.',
  },
  // SAML2 support
  [AmFeature.AM_IDP_SAML2]: {
    image: 'assets/gio-license/am-idp-saml2.svg',
    description:
      'SAML 2.0 IdP support is part of Gravitee Enterprise. SAML 2.0 allows you to exchange security information between online business partners.',
  },
};

export function stringFeature(value: string): AmFeature {
  const feature = value as AmFeature;
  if (FeatureInfoData[feature]) {
    return feature;
  }
  throw new Error(`Unknown Feature value ${value}. Expected one of ${Object.keys(FeatureInfoData)}`);
}

export interface FeatureInfo {
  image: string;
  description: string;
  title?: string;
}
