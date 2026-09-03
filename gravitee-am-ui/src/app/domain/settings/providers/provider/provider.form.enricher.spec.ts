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
import {
  enrichFormWithSystemClusterCreationHints,
  enrichFormWithSystemClusterDisclaimer,
  enrichFormWithSystemClusterLock,
  enrichFormWithSystemClusterRestrictions,
} from './provider.form.enricher';

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

  const pinned = true;

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

  it('leaves the schema alone when the rule is off', () => {
    const enriched = enrichFormWithSystemClusterCreationHints(mongoSchema(), 'mongo-am-idp', false);

    expect(enriched.properties.database.description).toEqual('The database.');
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

describe('enrichFormWithSystemClusterLock', () => {
  const mongoSchema = () =>
    ({
      id: 'urn:jsonschema:io:gravitee:am:identityprovider:mongo:MongoIdentityProviderConfiguration',
      version: '1',
      properties: {
        useSystemCluster: { type: 'boolean' },
        database: { type: 'string' },
        usersCollection: { type: 'string' },
        datasourceId: { type: 'string' },
      },
    }) as any;

  it('locks the toggle of a provider that already reuses the system cluster', () => {
    const enriched = enrichFormWithSystemClusterLock(mongoSchema(), 'mongo-am-idp', true);

    expect(enriched.properties.useSystemCluster.readonly).toBe(true);
  });

  it('leaves the storage fields and the datasource editable', () => {
    const enriched = enrichFormWithSystemClusterLock(mongoSchema(), 'mongo-am-idp', true);

    expect(enriched.properties.database.readonly).toBeUndefined();
    expect(enriched.properties.usersCollection.readonly).toBeUndefined();
    expect(enriched.properties.datasourceId.readonly).toBeUndefined();
  });

  it('leaves the schema alone for a provider of another type', () => {
    const enriched = enrichFormWithSystemClusterLock(mongoSchema(), 'inline-am-idp', true);

    expect(enriched.properties.useSystemCluster.readonly).toBeUndefined();
  });

  it('leaves a schema without the toggle alone', () => {
    const schema = { id: 'other', version: '1', properties: { usernameField: { type: 'string' } } } as any;

    expect(enrichFormWithSystemClusterLock(schema, 'mongo-am-idp', true)).toBe(schema);
  });

  it('does not mutate the schema it was given', () => {
    const original = mongoSchema();

    enrichFormWithSystemClusterLock(original, 'mongo-am-idp', true);

    expect(original.properties.useSystemCluster.readonly).toBeUndefined();
  });
});

describe('enrichFormWithSystemClusterDisclaimer', () => {
  const mongoSchema = (title?: string) =>
    ({
      id: 'urn:jsonschema:io:gravitee:am:identityprovider:mongo:MongoIdentityProviderConfiguration',
      version: '1',
      properties: {
        useSystemCluster: title ? { type: 'boolean', title } : { type: 'boolean' },
        usernameField: { type: 'string' },
      },
    }) as any;

  // The widget drawing a boolean reads the title and never the description, so the sentence has to
  // ride on the label the administrator already sees.
  it('appends the disclaimer to the toggle label', () => {
    const enriched = enrichFormWithSystemClusterDisclaimer(mongoSchema('Use System Cluster'), 'mongo-am-idp', true);

    expect(enriched.properties.useSystemCluster.title).toEqual(
      'Use System Cluster<br><small>Once saved, this option cannot be changed.</small>',
    );
  });

  it('leaves the description alone', () => {
    const enriched = enrichFormWithSystemClusterDisclaimer(mongoSchema('Use System Cluster'), 'mongo-am-idp', true);

    expect(enriched.properties.useSystemCluster.description).toBeUndefined();
  });

  it('sets the disclaimer when the toggle has no label', () => {
    const enriched = enrichFormWithSystemClusterDisclaimer(mongoSchema(), 'mongo-am-idp', true);

    expect(enriched.properties.useSystemCluster.title).toEqual('Once saved, this option cannot be changed.');
  });

  it('leaves the toggle editable', () => {
    const enriched = enrichFormWithSystemClusterDisclaimer(mongoSchema(), 'mongo-am-idp', true);

    expect(enriched.properties.useSystemCluster.readonly).toBeUndefined();
  });

  it('leaves the schema alone for a provider of another type', () => {
    const enriched = enrichFormWithSystemClusterDisclaimer(mongoSchema('Use System Cluster'), 'inline-am-idp', true);

    expect(enriched.properties.useSystemCluster.title).toEqual('Use System Cluster');
  });

  it('does not mutate the schema it was given', () => {
    const original = mongoSchema('Use System Cluster');

    enrichFormWithSystemClusterDisclaimer(original, 'mongo-am-idp', true);

    expect(original.properties.useSystemCluster.title).toEqual('Use System Cluster');
  });
});

describe('system cluster enrichers outside the storage rule', () => {
  const mongoSchema = () =>
    ({
      id: 'urn:jsonschema:io:gravitee:am:identityprovider:mongo:MongoIdentityProviderConfiguration',
      version: '1',
      properties: { useSystemCluster: { type: 'boolean', title: 'Use System Cluster' } },
    }) as any;

  it('leaves the toggle open, because the setting is not held outside the storage rule', () => {
    const enriched = enrichFormWithSystemClusterLock(mongoSchema(), 'mongo-am-idp', false);

    expect(enriched.properties.useSystemCluster.readonly).toBeUndefined();
  });

  it('leaves the label alone, because there is nothing irreversible to warn about', () => {
    const enriched = enrichFormWithSystemClusterDisclaimer(mongoSchema(), 'mongo-am-idp', false);

    expect(enriched.properties.useSystemCluster.title).toEqual('Use System Cluster');
  });
});
