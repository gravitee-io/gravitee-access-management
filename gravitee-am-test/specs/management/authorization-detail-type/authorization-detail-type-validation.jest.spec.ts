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

describe('Validation of expiresIn', () => {
  it.each([0, -1])('should reject an expiresIn of %d', async (expiresIn) => {
    await expectRejected(newType(uniqueType(), { expiresIn }), 'Expires in must be greater than 0');
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
      newType(type, { schema: validSchema(type, { $schema: 'http://json-schema.org/draft-07/schema#' }) }),
      'Schema must use JSON Schema 2020-12 ($schema must be absent or \\"https://json-schema.org/draft/2020-12/schema\\"), found at #/$schema',
    );
  });

  it(jira`should accept a schema declaring 2020-12 ${'AM-7923'}`, async () => {
    const type = uniqueType();

    const created = await createAuthorizationDetailType(
      fixture.domain.id,
      fixture.accessToken,
      newType(type, { schema: validSchema(type, { $schema: AUTHORIZATION_DETAIL_TYPE_TEST.DIALECT }) }),
    );

    expect(created.type).toEqual(type);
  });

  it(jira`should reject a schema failing the 2020-12 meta-schema ${'AM-7923'}`, async () => {
    const type = uniqueType();

    await expectRejected(newType(type, { schema: validSchema(type, { minProperties: -1 }) }), 'Schema is not a valid JSON Schema 2020-12');
  });

  it(jira`should reject definitions in favor of $defs ${'AM-7923'}`, async () => {
    const type = uniqueType();

    await expectRejected(
      newType(type, { schema: validSchema(type, { definitions: { amount: { type: 'string' } } }) }),
      'Schema must declare reusable subschemas in \\"$defs\\", not \\"definitions\\", found at #/definitions',
    );
  });

  it(jira`should reject a schema whose root is not an object ${'AM-7923'}`, async () => {
    await expectRejected(
      newType(uniqueType(), { schema: JSON.stringify({ type: 'string' }) }),
      'Schema must declare \\"type\\": \\"object\\" at its root, found at #/type',
    );
  });

  it(jira`should reject a schema with a remote reference ${'AM-7923'}`, async () => {
    const type = uniqueType();

    await expectRejected(
      newType(type, { schema: validSchema(type, { $defs: { remote: { $ref: 'https://example.org/schema.json' } } }) }),
      'Schema must only contain local references (starting with \\"#\\"), found at #/$defs/remote/$ref',
    );
  });

  it(jira`should accept a schema with a local reference ${'AM-7923'}`, async () => {
    const type = uniqueType();
    const schema = JSON.stringify({
      type: 'object',
      $defs: { amount: { type: 'string' } },
      properties: { type: { const: type }, amount: { $ref: '#/$defs/amount' } },
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

    await expectRejected(
      newType(type, { schema }),
      'Schema must constrain \\"type\\": its root must declare properties.type.const and list \\"type\\" in required, found at #',
    );
  });

  it(jira`should accept keywords 2020-12 does not define ${'AM-7923'}`, async () => {
    const type = uniqueType();
    const schema = validSchema(type, { 'x-masked': ['iban'] });

    const created = await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(type, { schema }));

    expect(created.schema).toEqual(schema);
  });
});

describe('Validation of schema references', () => {
  it(jira`should accept references beside other keywords ${'AM-7923'}`, async () => {
    const type = uniqueType();
    const schema = JSON.stringify({
      type: 'object',
      $ref: '#/$defs/loose',
      $defs: { loose: {} },
      properties: { type: { const: type, $ref: '#/$defs/loose' } },
      required: ['type'],
    });

    const created = await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(type, { schema }));

    expect(created.schema).toEqual(schema);
  });

  it.each([
    ['a reference cycle', { $defs: { a: { $ref: '#/$defs/b' }, b: { $ref: '#/$defs/a' } } }],
    ['a root that applies itself', { allOf: [{ $ref: '#' }] }],
  ])(jira`should reject %s ${'AM-7923'}`, async (_, extra) => {
    const type = uniqueType();

    await expectRejected(newType(type, { schema: validSchema(type, extra) }), 'Schema must not apply itself to the same value without end');
  });

  it.each([
    ['is missing', '#/$defs/missing'],
    ['points into an unknown keyword', '#/x-hidden'],
  ])(jira`should reject a reference that %s ${'AM-7923'}`, async (_, ref) => {
    const type = uniqueType();

    await expectRejected(
      newType(type, { schema: validSchema(type, { 'x-hidden': { type: 'string' }, $defs: { a: { $ref: ref } } }) }),
      `Schema reference \\"${ref}\\" does not resolve to a subschema`,
    );
  });

  it.each([
    ['an identifier', { $defs: { a: { $id: 'https://example.org/a', type: 'string' } } }, '$id', '#/$defs/a/$id'],
    ['an anchor', { $defs: { a: { $anchor: 'a', type: 'string' } } }, '$anchor', '#/$defs/a/$anchor'],
    ['a dynamic reference', { $defs: { a: { $dynamicRef: '#a' } } }, '$dynamicRef', '#/$defs/a/$dynamicRef'],
  ])(jira`should reject %s ${'AM-7923'}`, async (_, extra, keyword, location) => {
    const type = uniqueType();

    await expectRejected(
      newType(type, { schema: validSchema(type, extra) }),
      `Schema must not declare \\"${keyword}\\", found at ${location}`,
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
