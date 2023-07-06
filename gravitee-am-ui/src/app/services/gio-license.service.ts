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
    image: 'assets/gio-ee-unlock-dialog/am-mfa-sms.png',
    description:
      'The SMS factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  'am-mfa-call': {
    image: 'assets/gio-ee-unlock-dialog/am-mfa-call.png',
    description:
      'The Call factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  'am-mfa-fido2': {
    image: 'assets/gio-ee-unlock-dialog/am-mfa-fido2.png',
    description:
      'The FIDO2 factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  'am-mfa-resource-http-factor': {
    image: 'assets/gio-ee-unlock-dialog/dcr-providers.png',
    description:
      'Dynamic Client Registration (DCR) Provider is part of Gravitee Enterprise. DCR enhances your API\'s security by seamlessly integrating OAuth 2.0 and OpenID Connect.',
  },
  'am-mfa-http': {
    image: 'assets/gio-ee-unlock-dialog/am-mfa-http.png.png',
    description:
      'The HTTP factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  'am-mfa-recovery-code': {
    image: 'assets/gio-ee-unlock-dialog/am-mfa-recovery-code.png',
    description:
      'The Recovery code factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  // todo: Missing images and description
  'am-resource-http-factor': {
    image: 'assets/gio-ee-unlock-dialog/am-mfa-recovery-code.png',
    description:
      'The HTTP factor is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  'am-mfa-otp-sender': {
    image: 'assets/gio-ee-unlock-dialog/am-mfa-recovery-code.png',
    description:
      'The OTO Sender is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  'am-resource-twilio': {
    image: 'assets/gio-ee-unlock-dialog/am-mfa-recovery-code.png',
    description:
      'The Twilio resource is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  // IDPs
  'am-idp-salesforce': {
    image: 'assets/gio-ee-unlock-dialog/am-idp-salesforce.png',
    description:
      'The Salesforce identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  'am-idp-saml': {
    image: 'assets/gio-ee-unlock-dialog/am-idp-saml.png',
    description:
      'The SAML 2.0 identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  'am-idp-ldap': {
    image: 'assets/gio-ee-unlock-dialog/am-idp-ldap.png',
    description:
      'The LDAP identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  'am-idp-kerberos': {
    image: 'assets/gio-ee-unlock-dialog/am-idp-kerberos.png',
    description:
      'The Kerberos identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  'am-idp-azure-ad': {
    image: 'assets/gio-ee-unlock-dialog/am-idp-azure-ad.png',
    description:
      'The Azure AD identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  'am-idp-france-connect': {
    image: 'assets/gio-ee-unlock-dialog/am-idp-france-connect.png',
    description:
      'The France Connect identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  'am-idp-cas': {
    image: 'assets/gio-ee-unlock-dialog/am-idp-cas.png',
    description:
      'The CAS provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  // todo: Missing information
  'am-idp-gateway-handler-saml': {
    image: 'assets/gio-ee-unlock-dialog/am-mfa-recovery-code.png',
    description:
      'The Twilio resource is part of Gravitee Enterprise. Multi-factor authentication is an additional step during login to enforce access control.',
  },
  'am-idp-http-flow': {
    image: 'assets/gio-ee-unlock-dialog/am-mfa-recovery-code.png',
    description:
      'The HTTP Flow identity provider is part of Gravitee Enterprise. Identity providers allow you to configure authentication methods familiar to your users and comply with your security requirement.',
  },
  // Resources
  'am-smtp': { // todo: This resource is missing
    image: 'assets/gio-ee-unlock-dialog/am-smtp.png',
    description:
      'The SMTP resource is part of Gravitee Enterprise. Resources allow you to easily reuse some settings.',
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
