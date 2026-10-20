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
/** The licence feature names the Angular console gates on, from `gravitee-am-ui/src/app/components/gio-license/gio-license-data.ts`. */
export const AmFeature = {
    AM_MFA_SMS: 'am-mfa-sms',
    AM_MFA_CALL: 'am-mfa-call',
    AM_MFA_FIDO2: 'am-mfa-fido2',
    AM_MFA_RESOURCE_HTTP_FACTOR: 'am-mfa-resource-http-factor',
    AM_MFA_HTTP: 'am-mfa-http',
    AM_MFA_RECOVERY_CODE: 'am-mfa-recovery-code',
    AM_RESOURCE_HTTP_FACTOR: 'am-resource-http-factor',
    AM_MFA_OTP_SENDER: 'am-mfa-otp-sender',
    AM_RESOURCE_TWILIO: 'am-resource-twilio',
    AM_IDP_SALESFORCE: 'am-idp-salesforce',
    AM_IDP_SAML: 'am-idp-saml',
    AM_IDP_LDAP: 'am-idp-ldap',
    AM_IDP_KERBEROS: 'am-idp-kerberos',
    AM_IDP_AZURE_AD: 'am-idp-azure-ad',
    AM_IDP_FRANCE_CONNECT: 'am-idp-france-connect',
    AM_IDP_CAS: 'am-idp-cas',
    AM_IDP_GATEWAY_HANDLER_SAML: 'am-idp-gateway-handler-saml',
    AM_IDP_HTTP_FLOW: 'am-idp-http-flow',
    AM_GRAVITEE_RISK_ASSESSMENT: 'gravitee-risk-assessment',
    AM_IDP_SAML2: 'am-idp-saml2',
    ALERT_ENGINE: 'alert-engine',
    AM_ENTERPRISE: 'am-enterprise',
} as const;

export type AmFeatureName = (typeof AmFeature)[keyof typeof AmFeature];

/** The text of the upgrade dialog per feature. */
export const FEATURE_DESCRIPTIONS: Record<AmFeatureName, string> = {
    [AmFeature.AM_MFA_SMS]:
        'The SMS factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
    [AmFeature.AM_MFA_CALL]:
        'The Call factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
    [AmFeature.AM_MFA_FIDO2]:
        'The FIDO2 factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
    [AmFeature.AM_MFA_RESOURCE_HTTP_FACTOR]:
        'The HTTP factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
    [AmFeature.AM_MFA_HTTP]:
        'The HTTP factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
    [AmFeature.AM_MFA_RECOVERY_CODE]:
        'The Recovery code factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
    [AmFeature.AM_RESOURCE_HTTP_FACTOR]:
        'The HTTP factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
    [AmFeature.AM_MFA_OTP_SENDER]:
        'The OTO Sender is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
    [AmFeature.AM_RESOURCE_TWILIO]:
        'The Twilio resource is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
    [AmFeature.AM_IDP_SALESFORCE]:
        'The Salesforce identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
    [AmFeature.AM_IDP_SAML]:
        'The SAML 2.0 identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
    [AmFeature.AM_IDP_LDAP]:
        'The LDAP identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
    [AmFeature.AM_IDP_KERBEROS]:
        'The Kerberos identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
    [AmFeature.AM_IDP_AZURE_AD]:
        'The Azure AD identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
    [AmFeature.AM_IDP_FRANCE_CONNECT]:
        'The France Connect identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
    [AmFeature.AM_IDP_CAS]:
        'The CAS provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
    [AmFeature.AM_IDP_GATEWAY_HANDLER_SAML]:
        'The SAML 2.0 identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
    [AmFeature.AM_IDP_HTTP_FLOW]:
        'The HTTP Flow identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
    [AmFeature.AM_GRAVITEE_RISK_ASSESSMENT]:
        'Risk-based Multi-factor authentication is part of Gravitee Enterprise. MFA allows you to prompt end-users to process MFA verification after they have been authenticated.',
    [AmFeature.AM_IDP_SAML2]:
        'SAML 2.0 IdP support is part of Gravitee Enterprise. SAML 2.0 allows you to exchange security information between online business partners.',
    [AmFeature.ALERT_ENGINE]:
        'Alert Engine allows you to isolate, understand and remediate for API performance and security risks before they cause a problem for your customers.',
    [AmFeature.AM_ENTERPRISE]: 'This is part of Gravitee Enterprise. Upgrade your license to enable it.',
};
