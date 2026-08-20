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
const OIDC_JSON_FORM = {
  id: 'urn:jsonschema:io:gravitee:am:identityprovider:oauth2:OAuth2GenericIdentityProvider',
  version: '05-2024',
};

const MONGO_IDP_TYPE = 'mongo-am-idp';

/** Storage fields the platform owns once an identity provider reuses the system cluster. */
const PINNED_STORAGE_FIELDS = ['useSystemCluster', 'database', 'usersCollection'];

const LDAP_JSON_FORM = {
  id: 'urn:jsonschema:com:graviteesource:am:identityprovider:ldap:LdapIdentityProviderConfiguration',
  version: '07-2024',
};

export function enrichFormWithCerts(schema: FormSchema, certs: Certificate[]): FormSchema {
  const mTlsCerts = certs?.filter((c) => c?.usage?.includes('mtls'));
  if (mTlsCerts?.length > 0 && supportsMTls(schema)) {
    const updatedSchema = { ...schema };
    updatedSchema.properties.clientAuthenticationCertificate.enum = mTlsCerts.map((c) => c.id);
    updatedSchema.properties.clientAuthenticationCertificate.enumNames = mTlsCerts.map((c) => c.name);
    updatedSchema.properties.clientAuthenticationCertificate.readonly = false;
    return updatedSchema;
  }

  return schema;
}

/**
 * Marks the storage fields of a mongo identity provider read-only when the platform owns where it
 * stores its users. On the edit screen `restricted` is the provider's own flag; on the creation
 * screen it is whether this installation is a managed cloud one, since the flag does not exist yet.
 */
export function enrichFormWithSystemClusterRestrictions(schema: FormSchema, providerType: string, restricted: boolean): FormSchema {
  if (!restricted || providerType !== MONGO_IDP_TYPE || !schema?.properties) {
    return schema;
  }

  const updatedSchema = { ...schema, properties: { ...schema.properties } };
  PINNED_STORAGE_FIELDS.filter((field) => updatedSchema.properties[field]).forEach((field) => {
    updatedSchema.properties[field] = { ...updatedSchema.properties[field], readonly: true };
  });
  return updatedSchema;
}

function supportsMTls(schema: FormSchema): boolean {
  return (
    (schema.id === OIDC_JSON_FORM.id && schema?.version == OIDC_JSON_FORM.version) ||
    (schema.id === LDAP_JSON_FORM.id && schema?.version == LDAP_JSON_FORM.version)
  );
}

interface FormSchema {
  id: string;
  version: string;
  properties: {
    clientAuthenticationCertificate: {
      enum: string[];
      enumNames: string[];
      readonly: boolean;
    };
    // Other plugin-specific properties, e.g. the mongo storage fields pinned below.
    [field: string]: any;
  };
}

interface Certificate {
  id: string;
  name: string;
  usage: string[];
}
