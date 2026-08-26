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
import { enrichFormWithSystemClusterCreationHints, enrichFormWithSystemClusterRestrictions } from './provider.form.enricher';

describe('enrichFormWithSystemClusterRestrictions', () => {
  const mongoSchema = () =>
    ({
      id: 'urn:jsonschema:io:gravitee:am:identityprovider:mongo:MongoIdentityProviderConfiguration',
      version: '1',
      properties: {
        useSystemCluster: { type: 'boolean' },
        database: { type: 'string' },
        usersCollection: { type: 'string' },
        usernameField: { type: 'string' },
      },
    }) as any;

  it('marks the storage fields read-only for a restricted mongo provider', () => {
    const enriched = enrichFormWithSystemClusterRestrictions(mongoSchema(), 'mongo-am-idp', true);

    expect(enriched.properties.database.readonly).toBe(true);
    expect(enriched.properties.usersCollection.readonly).toBe(true);
  });

  it('leaves the other fields editable', () => {
    const enriched = enrichFormWithSystemClusterRestrictions(mongoSchema(), 'mongo-am-idp', true);

    expect(enriched.properties.usernameField.readonly).toBeUndefined();
  });

  it('locks the system cluster toggle', () => {
    const enriched = enrichFormWithSystemClusterRestrictions(mongoSchema(), 'mongo-am-idp', true);

    expect(enriched.properties.useSystemCluster.readonly).toBe(true);
  });

  it('leaves the schema alone when the provider is not restricted', () => {
    const enriched = enrichFormWithSystemClusterRestrictions(mongoSchema(), 'mongo-am-idp', false);

    expect(enriched.properties.database.readonly).toBeUndefined();
    expect(enriched.properties.usersCollection.readonly).toBeUndefined();
  });

  it('leaves the schema alone for a provider of another type', () => {
    const enriched = enrichFormWithSystemClusterRestrictions(mongoSchema(), 'inline-am-idp', true);

    expect(enriched.properties.database.readonly).toBeUndefined();
  });

  it('does not mutate the schema it was given', () => {
    const original = mongoSchema();

    enrichFormWithSystemClusterRestrictions(original, 'mongo-am-idp', true);

    expect(original.properties.database.readonly).toBeUndefined();
  });

  it('ignores a schema that does not carry the storage fields', () => {
    const schema = { id: 'other', version: '1', properties: { usernameField: { type: 'string' } } } as any;

    const enriched = enrichFormWithSystemClusterRestrictions(schema, 'mongo-am-idp', true);

    expect(enriched.properties.usernameField.readonly).toBeUndefined();
  });

  it('leaves the toggle editable when the provider is not restricted', () => {
    const enriched = enrichFormWithSystemClusterRestrictions(mongoSchema(), 'mongo-am-idp', false);

    expect(enriched.properties.useSystemCluster.readonly).toBeUndefined();
  });
});

describe('enrichFormWithSystemClusterCreationHints', () => {
  const mongoSchema = () =>
    ({
      id: 'urn:jsonschema:io:gravitee:am:identityprovider:mongo:MongoIdentityProviderConfiguration',
      version: '1',
      properties: {
        useSystemCluster: { type: 'boolean' },
        database: { type: 'string', description: 'The database.' },
        usersCollection: { type: 'string' },
        usernameField: { type: 'string' },
      },
    }) as any;

  const pinned = { pinDatabase: true, prefixUsersCollection: true };

  it('appends the hint to the pinned fields', () => {
    const enriched = enrichFormWithSystemClusterCreationHints(mongoSchema(), 'mongo-am-idp', pinned);

    expect(enriched.properties.database.description).toEqual(
      'The database. The platform sets this value when "use system cluster" is selected.',
    );
    expect(enriched.properties.usersCollection.description).toEqual('The platform sets this value when "use system cluster" is selected.');
  });

  it('keeps the fields editable', () => {
    const enriched = enrichFormWithSystemClusterCreationHints(mongoSchema(), 'mongo-am-idp', pinned);

    expect(enriched.properties.database.readonly).toBeUndefined();
    expect(enriched.properties.usersCollection.readonly).toBeUndefined();
  });

  it('hints only the database when the collection rule is off', () => {
    const enriched = enrichFormWithSystemClusterCreationHints(mongoSchema(), 'mongo-am-idp', {
      pinDatabase: true,
      prefixUsersCollection: false,
    });

    expect(enriched.properties.database.description).toEqual(
      'The database. The platform sets this value when "use system cluster" is selected.',
    );
    expect(enriched.properties.usersCollection.description).toBeUndefined();
  });

  it('hints only the collection when the database rule is off', () => {
    const enriched = enrichFormWithSystemClusterCreationHints(mongoSchema(), 'mongo-am-idp', {
      pinDatabase: false,
      prefixUsersCollection: true,
    });

    expect(enriched.properties.database.description).toEqual('The database.');
    expect(enriched.properties.usersCollection.description).toEqual('The platform sets this value when "use system cluster" is selected.');
  });

  it('leaves the schema alone when the rule is off', () => {
    const enriched = enrichFormWithSystemClusterCreationHints(mongoSchema(), 'mongo-am-idp', {
      pinDatabase: false,
      prefixUsersCollection: false,
    });

    expect(enriched.properties.usersCollection.description).toBeUndefined();
  });

  it('leaves the schema alone for a provider of another type', () => {
    const enriched = enrichFormWithSystemClusterCreationHints(mongoSchema(), 'inline-am-idp', pinned);

    expect(enriched.properties.usersCollection.description).toBeUndefined();
  });

  it('does not mutate the schema it was given', () => {
    const original = mongoSchema();

    enrichFormWithSystemClusterCreationHints(original, 'mongo-am-idp', pinned);

    expect(original.properties.usersCollection.description).toBeUndefined();
  });
});
