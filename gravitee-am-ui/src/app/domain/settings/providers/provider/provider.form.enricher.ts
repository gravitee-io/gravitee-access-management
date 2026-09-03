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

export const MONGO_IDP_TYPE = 'mongo-am-idp';

export const PINNED_STORAGE_FIELDS = ['database', 'usersCollection'];

/** The widget only binds a form control when `readonly` is falsy, so the toggle reads it too. */
export const PINNED_STORAGE_TOGGLE = 'useSystemCluster';

const CREATION_HINT = 'The platform sets this value when "use system cluster" is selected.';

const IMMUTABLE_HINT = 'Once saved, this option cannot be changed.';

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

/** Edit screen only: `restricted` is the provider's own flag. */
export function enrichFormWithSystemClusterRestrictions(schema: FormSchema, providerType: string, restricted: boolean): FormSchema {
  if (!restricted || providerType !== MONGO_IDP_TYPE || !schema?.properties) {
    return schema;
  }

  const updatedSchema = { ...schema, properties: { ...schema.properties } };
  [PINNED_STORAGE_TOGGLE, ...PINNED_STORAGE_FIELDS]
    .filter((field) => updatedSchema.properties[field])
    .forEach((field) => {
      updatedSchema.properties[field] = { ...updatedSchema.properties[field], readonly: true };
    });
  return updatedSchema;
}

/** Edit screen only: the toggle is closed under the storage rule, whichever way it is set. */
export function enrichFormWithSystemClusterLock(schema: FormSchema, providerType: string, restricted: boolean): FormSchema {
  if (!restricted || providerType !== MONGO_IDP_TYPE || !schema?.properties?.[PINNED_STORAGE_TOGGLE]) {
    return schema;
  }

  const updatedSchema = { ...schema, properties: { ...schema.properties } };
  updatedSchema.properties[PINNED_STORAGE_TOGGLE] = { ...updatedSchema.properties[PINNED_STORAGE_TOGGLE], readonly: true };
  return updatedSchema;
}

/** The widget drawing a boolean reads only `title`, never `description`, so the sentence rides on the label. */
export function enrichFormWithSystemClusterDisclaimer(schema: FormSchema, providerType: string, restricted: boolean): FormSchema {
  if (!restricted || providerType !== MONGO_IDP_TYPE || !schema?.properties?.[PINNED_STORAGE_TOGGLE]) {
    return schema;
  }

  const updatedSchema = { ...schema, properties: { ...schema.properties } };
  const property = updatedSchema.properties[PINNED_STORAGE_TOGGLE];
  updatedSchema.properties[PINNED_STORAGE_TOGGLE] = {
    ...property,
    title: property.title ? `${property.title}<br><small>${IMMUTABLE_HINT}</small>` : IMMUTABLE_HINT,
  };
  return updatedSchema;
}

/**
 * Creation screen. The fields stay editable: the plugin schema makes `usersCollection` mandatory and
 * a provider that does not reuse the system cluster still needs both values.
 */
export function enrichFormWithSystemClusterCreationHints(schema: FormSchema, providerType: string, restricted: boolean): FormSchema {
  if (!restricted || providerType !== MONGO_IDP_TYPE || !schema?.properties) {
    return schema;
  }

  const hinted = PINNED_STORAGE_FIELDS.filter((field) => schema.properties[field]);
  if (hinted.length === 0) {
    return schema;
  }

  const updatedSchema = { ...schema, properties: { ...schema.properties } };
  hinted.forEach((field) => {
    const property = updatedSchema.properties[field];
    updatedSchema.properties[field] = {
      ...property,
      description: property.description ? `${property.description} ${CREATION_HINT}` : CREATION_HINT,
    };
  });
  return updatedSchema;
}

function supportsMTls(schema: FormSchema): boolean {
  return (
    (schema.id === OIDC_JSON_FORM.id && schema?.version == OIDC_JSON_FORM.version) ||
    (schema.id === LDAP_JSON_FORM.id && schema?.version == LDAP_JSON_FORM.version)
  );
}

interface FormProperty {
  enum?: string[];
  enumNames?: string[];
  description?: string;
  title?: string;
  readonly?: boolean;
}

interface FormSchema {
  id: string;
  version: string;
  properties: Record<string, FormProperty> & {
    clientAuthenticationCertificate: FormProperty;
  };
}

interface Certificate {
  id: string;
  name: string;
  usage: string[];
}
