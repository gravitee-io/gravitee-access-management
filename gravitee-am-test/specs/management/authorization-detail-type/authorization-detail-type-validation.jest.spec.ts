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
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { setup } from '../../test-fixture';
import { jira } from '@specs-utils/jira';
import {
  AUTHORIZATION_DETAIL_TYPE_TEST,
  AuthorizationDetailTypeFixture,
  newType,
  setupAuthorizationDetailTypeFixture,
  uniqueType,
  validSchema,
} from './fixtures/authorization-detail-type-fixture';
import { createAuthorizationDetailType } from '@management-commands/authorization-detail-type-management-commands';
import { NewAuthorizationDetailType } from '@management-models/NewAuthorizationDetailType';

setup();

let fixture: AuthorizationDetailTypeFixture;

beforeAll(async () => {
  fixture = await setupAuthorizationDetailTypeFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

const expectRejected = (body: NewAuthorizationDetailType, message: string) =>
  expect(createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, body)).rejects.toMatchObject({
    response: { status: 400 },
    message: expect.stringContaining(message),
  });

describe('Validation of type', () => {
  it(jira`should reject a missing type ${'AM-7922'}`, async () => {
    await expectRejected({ name: 'Payment initiation' } as NewAuthorizationDetailType, 'Type is required');
  });

  it(jira`should reject an empty type ${'AM-7922'}`, async () => {
    await expectRejected(newType(''), 'Type is required');
  });

  it(jira`should reject a type longer than 512 characters ${'AM-7922'}`, async () => {
    await expectRejected(newType('a'.repeat(513)), 'Type must be at most 512 characters');
  });

  it.each([
    ['a space', 'payment initiation'],
    ['a leading space', ' payment_initiation'],
    ['a control character', 'payment\tinitiation'],
    ['a non-ASCII character', 'paiement_initié'],
  ])(jira`should reject a type containing %s without rewriting it ${'AM-7922'}`, async (_, type) => {
    await expectRejected(newType(type), 'Type must contain only visible ASCII characters');
  });
});

describe('Validation of name and description', () => {
  it(jira`should reject a blank name ${'AM-7922'}`, async () => {
    await expectRejected(newType(uniqueType(), { name: '   ' }), 'Name is required');
  });

  it(jira`should reject a name longer than 255 characters ${'AM-7922'}`, async () => {
    await expectRejected(newType(uniqueType(), { name: 'n'.repeat(256) }), 'Name must be at most 255 characters');
  });

  it(jira`should reject a description longer than 1024 characters ${'AM-7922'}`, async () => {
    await expectRejected(newType(uniqueType(), { description: 'd'.repeat(1025) }), 'Description must be at most 1024 characters');
  });
});

describe('Validation of schema', () => {
  it(jira`should reject a schema longer than 65536 characters ${'AM-7923'}`, async () => {
    const type = uniqueType();

    await expectRejected(newType(type, { schema: validSchema(type) + ' '.repeat(65536) }), 'Schema must be at most 65536 characters');
  });

  it(jira`should reject a schema that is not JSON ${'AM-7923'}`, async () => {
    await expectRejected(newType(uniqueType(), { schema: '{not json' }), 'Schema is not valid JSON');
  });

  it(jira`should reject a schema that is not a JSON object ${'AM-7923'}`, async () => {
    await expectRejected(newType(uniqueType(), { schema: '[]' }), 'Schema must be a JSON object');
  });

  it(jira`should reject a schema declaring another dialect ${'AM-7923'}`, async () => {
    const type = uniqueType();

    await expectRejected(
      newType(type, { schema: validSchema(type, { $schema: 'https://json-schema.org/draft/2020-12/schema' }) }),
      'Schema must use JSON Schema Draft 7',
    );
  });

  it(jira`should accept a schema declaring Draft 7 ${'AM-7923'}`, async () => {
    const type = uniqueType();

    const created = await createAuthorizationDetailType(
      fixture.domain.id,
      fixture.accessToken,
      newType(type, { schema: validSchema(type, { $schema: AUTHORIZATION_DETAIL_TYPE_TEST.DRAFT_7 }) }),
    );

    expect(created.type).toEqual(type);
  });

  it(jira`should reject a schema failing the Draft 7 meta-schema ${'AM-7923'}`, async () => {
    const type = uniqueType();

    await expectRejected(newType(type, { schema: validSchema(type, { minProperties: -1 }) }), 'Schema is not a valid JSON Schema Draft 7');
  });

  it(jira`should reject a schema whose root is not an object ${'AM-7923'}`, async () => {
    await expectRejected(
      newType(uniqueType(), { schema: JSON.stringify({ type: 'string' }) }),
      'Schema must declare \\"type\\": \\"object\\" at its root',
    );
  });

  it(jira`should reject a schema with a remote reference ${'AM-7923'}`, async () => {
    const type = uniqueType();

    await expectRejected(
      newType(type, { schema: validSchema(type, { definitions: { remote: { $ref: 'https://example.org/schema.json' } } }) }),
      'Schema must only contain local references',
    );
  });

  it(jira`should accept a schema with a local reference ${'AM-7923'}`, async () => {
    const type = uniqueType();
    const schema = JSON.stringify({
      type: 'object',
      definitions: { amount: { type: 'string' } },
      properties: { type: { const: type }, amount: { $ref: '#/definitions/amount' } },
      required: ['type'],
    });

    const created = await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(type, { schema }));

    expect(created.schema).toEqual(schema);
  });

  it(jira`should reject a schema that does not pin type to the submitted value ${'AM-7923'}`, async () => {
    const type = uniqueType();

    await expectRejected(newType(type, { schema: validSchema(type.toUpperCase()) }), `Schema must constrain \\"type\\" to \\"${type}\\"`);
  });

  it(jira`should reject a schema that does not require type ${'AM-7923'}`, async () => {
    const type = uniqueType();
    const schema = JSON.stringify({ type: 'object', properties: { type: { const: type } } });

    await expectRejected(newType(type, { schema }), `Schema must constrain \\"type\\" to \\"${type}\\"`);
  });

  it(jira`should accept keywords Draft 7 does not define ${'AM-7923'}`, async () => {
    const type = uniqueType();
    const schema = validSchema(type, { 'x-masked': ['iban'] });

    const created = await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(type, { schema }));

    expect(created.schema).toEqual(schema);
  });
});

describe('Validation of schema references', () => {
  it(jira`should reject a root reference beside the pinned type ${'AM-7923'}`, async () => {
    const type = uniqueType();

    await expectRejected(
      newType(type, { schema: validSchema(type, { $ref: '#/definitions/loose', definitions: { loose: {} } }) }),
      'Schema must not combine \\"$ref\\" with other keywords',
    );
  });

  it(jira`should reject a reference beside the type constant ${'AM-7923'}`, async () => {
    const type = uniqueType();
    const schema = JSON.stringify({
      type: 'object',
      definitions: { loose: {} },
      properties: { type: { const: type, $ref: '#/definitions/loose' } },
      required: ['type'],
    });

    await expectRejected(newType(type, { schema }), 'at #/properties/type');
  });

  it.each([
    ['a reference cycle', { definitions: { a: { $ref: '#/definitions/b' }, b: { $ref: '#/definitions/a' } } }],
    ['a root that applies itself', { allOf: [{ $ref: '#' }] }],
  ])(jira`should reject %s ${'AM-7923'}`, async (_, extra) => {
    const type = uniqueType();

    await expectRejected(newType(type, { schema: validSchema(type, extra) }), 'Schema must not apply itself to the same value without end');
  });

  it(jira`should reject a reference that does not resolve ${'AM-7923'}`, async () => {
    const type = uniqueType();

    await expectRejected(
      newType(type, { schema: validSchema(type, { definitions: { a: { $ref: '#/definitions/missing' } } }) }),
      'Schema reference \\"#/definitions/missing\\" does not resolve to a subschema',
    );
  });

  it(jira`should reject an identifier below the root ${'AM-7923'}`, async () => {
    const type = uniqueType();

    await expectRejected(
      newType(type, { schema: validSchema(type, { definitions: { a: { $id: '#a', type: 'string' } } }) }),
      'Schema must not declare \\"$id\\" below its root',
    );
  });

  it(jira`should accept recursion into the value ${'AM-7923'}`, async () => {
    const type = uniqueType();
    const schema = JSON.stringify({
      type: 'object',
      properties: { type: { const: type }, children: { type: 'array', items: { $ref: '#' } } },
      required: ['type'],
    });

    const created = await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(type, { schema }));

    expect(created.schema).toEqual(schema);
  });
});
