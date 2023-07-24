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
import {Injectable} from '@angular/core';

const featureMoreInformationData = {
  'am-mfa-sms': {
    utm: 'mfa_sms_factor',
    image: 'assets/gio-ee-unlock-dialog/am-mfa-sms.svg',
    description:
      'The SMS factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  'am-mfa-call': {
    utm: 'mfa_call_factor',
    image: 'assets/gio-ee-unlock-dialog/am-mfa-call.svg',
    description:
      'The Call factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  'am-mfa-fido2': {
    utm: 'mfa_fido_factor',
    image: 'assets/gio-ee-unlock-dialog/am-mfa-fido2.svg',
    description:
      'The FIDO2 factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  'am-mfa-resource-http-factor': {
    utm: 'mfa_http_factor',
    image: 'assets/gio-ee-unlock-dialog/am-mfa-http.svg',
    description:
      'The HTTP factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  'am-mfa-http': {
    utm: 'mfa_http',
    image: 'assets/gio-ee-unlock-dialog/am-mfa-http.svg',
    description:
      'The HTTP factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  'am-mfa-recovery-code': {
    utm: 'mfa_recovery_code_factor',
    image: 'assets/gio-ee-unlock-dialog/am-mfa-recovery-code.svg',
    description:
      'The Recovery code factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  // todo: Missing images and description
  'am-resource-http-factor': {
    utm: 'resource_http',
    image: 'assets/gio-ee-unlock-dialog/am-mfa-http.svg',
    description:
      'The HTTP factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  'am-mfa-otp-sender': {
    image: 'assets/gio-ee-unlock-dialog/gravitee-ee-upgrade.svg',
    description:
      'The OTO Sender is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  'am-resource-twilio': {
    utm: 'resource_twilio_verify',
    image: 'assets/gio-ee-unlock-dialog/gravitee-ee-upgrade.svg',
    description:
      'The Twilio resource is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  // IDPs
  'am-idp-salesforce': {
    utm: 'provider_salesforce',
    image: 'assets/gio-ee-unlock-dialog/am-idp-salesforce.svg',
    description:
      'The Salesforce identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  'am-idp-saml': {
    utm: 'provider_saml',
    image: 'assets/gio-ee-unlock-dialog/am-idp-saml.svg',
    description:
      'The SAML 2.0 identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  'am-idp-ldap': {
    utm: 'provider_ldap',
    image: 'assets/gio-ee-unlock-dialog/am-idp-ldap.svg',
    description:
      'The LDAP identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  'am-idp-kerberos': {
    utm: 'provider_kerberos',
    image: 'assets/gio-ee-unlock-dialog/am-idp-kerberos.svg',
    description:
      'The Kerberos identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  'am-idp-azure-ad': {
    utm: 'provider_azure_ad',
    image: 'assets/gio-ee-unlock-dialog/am-idp-azure-ad.svg',
    description:
      'The Azure AD identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  'am-idp-france-connect': {
    utm: 'provider_france_connect',
    image: 'assets/gio-ee-unlock-dialog/am-idp-france-connect.svg',
    description:
      'The France Connect identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  'am-idp-cas': { // utm_source=oss_am&utm_medium=provider_cas&utm_campaign=oss_am_to_ee_am
    utm: 'provider_cas',
    image: 'assets/gio-ee-unlock-dialog/am-idp-cas.svg',
    description:
      'The CAS provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  // todo: Missing information
  'am-idp-gateway-handler-saml': {
    utm: 'provider_saml',
    image: 'assets/gio-ee-unlock-dialog/gravitee-ee-upgrade.svg',
    description:
      'The SAML 2.0 identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  'am-idp-http-flow': {
    utm: 'provider_http_flow',
    image: 'assets/gio-ee-unlock-dialog/gravitee-ee-upgrade.svg',
    description:
      'The HTTP Flow identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  // Resources
  'am-smtp': { // todo: This resource is missing
    utm: '',
    image: 'assets/gio-ee-unlock-dialog/am-smtp.svg',
    description:
      'The SMTP resource is part of Gravitee Enterprise. Resources allow you to easily reuse some settings.',
  },
  // MFA
  'gravitee-risk-assessment': {
    utm: 'mfa_risk_base',
    image: 'assets/gio-ee-unlock-dialog/gravitee-risk-assessment.svg',
    description:
      'Risk-based Multi-factor authentication is part of Gravitee Enterprise. MFA allows you to prompt end-users to process MFA verification after they have been authenticated.',
  },
  // SAML2 support
  'am-idp-saml2': {
    utm: 'provider_saml',
    image: 'assets/gio-ee-unlock-dialog/am-idp-saml2.svg',
    description:
      'SAML 2.0 IdP support is part of Gravitee Enterprise. SAML 2.0 allows you to exchange security information between online business partners.',
  },
};

@Injectable({
  providedIn: 'root',
})
export class GioLicenseService {

  getFeatureMoreInformation(feature: string): any {
    const featureMoreInformation = featureMoreInformationData[feature];
    if (!featureMoreInformation) {
      throw new Error(`No data defined for '${feature}', you must use one of ${Object.keys(featureMoreInformationData)}`);
    }
    return featureMoreInformation;
  }
}
