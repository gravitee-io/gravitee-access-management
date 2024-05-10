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
const OIDC_JSON_FORM_ID: string = 'urn:jsonschema:io:gravitee:am:identityprovider:oauth2:OAuth2GenericIdentityProvider';
const OIDC_JSON_FORM_VERSION: string = '05-2024';

export function enrichOIDCFormWithCerts(schema: OIDCFormSchema, certs: Certificate[]): OIDCFormSchema {
  const mTlsCerts = certs?.filter((c) => c?.usage.includes('mtls'));
  if (mTlsCerts?.length > 0 && schema.id === OIDC_JSON_FORM_ID && schema?.version == OIDC_JSON_FORM_VERSION) {
    const updatedSchema = { ...schema };
    updatedSchema.properties.clientAuthenticationCertificate.enum = mTlsCerts.map((c) => c.id);
    updatedSchema.properties.clientAuthenticationCertificate.enumNames = mTlsCerts.map((c) => c.name);
    updatedSchema.properties.clientAuthenticationCertificate.readonly = false;
    return updatedSchema;
  }

  return schema;
}

interface OIDCFormSchema {
  id: string;
  version: string;
  properties: {
    clientAuthenticationCertificate: {
      enum: string[];
      enumNames: string[];
      readonly: boolean;
    };
  };
}

interface Certificate {
  id: string;
  name: string;
  usage: string[];
}
