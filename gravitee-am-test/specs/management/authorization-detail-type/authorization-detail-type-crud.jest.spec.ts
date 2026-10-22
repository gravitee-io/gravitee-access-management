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
  AuthorizationDetailTypeFixture,
  newType,
  setupAuthorizationDetailTypeFixture,
  uniqueType,
  validSchema,
} from './fixtures/authorization-detail-type-fixture';
import {
  createAuthorizationDetailType,
  createAuthorizationDetailTypeRaw,
  getAuthorizationDetailType,
} from '@management-commands/authorization-detail-type-management-commands';

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

describe('Create', () => {
  it(jira`should create a type and point at it ${'AM-7920'}`, async () => {
    const type = uniqueType();
    const schema = validSchema(type);

    const response = await createAuthorizationDetailTypeRaw(
      fixture.domain.id,
      fixture.accessToken,
      newType(type, { description: 'Initiates a payment', schema, discovery: true, expiresIn: 3600 }),
    );

    expect(response.raw.status).toEqual(201);
    const created = await response.value();
    expect(created.id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    expect(response.raw.headers.get('location')).toMatch(
      new RegExp(`/domains/${fixture.domain.id}/authorization-detail-types/${created.id}$`),
    );
    expect(created).toMatchObject({
      domain: fixture.domain.id,
      type,
      name: 'Payment initiation',
      description: 'Initiates a payment',
      schema,
      discovery: true,
      expiresIn: 3600,
      version: 1,
      createdAt: expect.any(Number),
    });
    expect(created.updatedAt).toEqual(created.createdAt);
  });

  it(jira`should default discovery to false and leave optional fields out ${'AM-7920'}`, async () => {
    const created = await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(uniqueType()));

    expect(created.version).toEqual(1);
    expect(created.discovery).toBe(false);
    expect(created.expiresIn).toBeUndefined();
    expect(created.description).toBeUndefined();
    expect(created.schema).toBeUndefined();
  });

  it(jira`should return the schema exactly as submitted ${'AM-7923'}`, async () => {
    const type = uniqueType();
    const schema = `{ "type" : "object",\n  "x-unknown-keyword": [1, 2],\n  "properties": { "type": { "const": "${type}" } },\n  "required": ["type"] }`;

    const created = await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(type, { schema }));

    expect(created.schema).toEqual(schema);
    const read = await getAuthorizationDetailType(fixture.domain.id, fixture.accessToken, created.id);
    expect(read.schema).toEqual(schema);
  });

  it(jira`should accept a URI as type ${'AM-7920'}`, async () => {
    const type = `https://scheme.example.org/${uniqueType('files')}`;

    const created = await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(type));

    expect(created.type).toEqual(type);
  });

  it(jira`should refuse a domain that does not exist ${'AM-7921'}`, async () => {
    // refused by the permission check, which cannot place an unknown domain in the environment
    await expect(createAuthorizationDetailType('unknown-domain', fixture.accessToken, newType(uniqueType()))).rejects.toMatchObject({
      response: { status: 403 },
    });
  });
});

describe('Uniqueness', () => {
  it(jira`should reject a type that differs from an existing one only by case ${'AM-7921'}`, async () => {
    const type = uniqueType();
    await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(type));

    await expect(createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(type.toUpperCase()))).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('already exists'),
    });
  });

  it(jira`should reject the exact same type ${'AM-7921'}`, async () => {
    const type = uniqueType();
    await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(type));

    await expect(createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(type))).rejects.toMatchObject({
      response: { status: 400 },
      message: expect.stringContaining('already exists'),
    });
  });

  it(jira`should create only one of several concurrent case variants ${'AM-7921'}`, async () => {
    const type = uniqueType();
    const variants = [type, type.toUpperCase(), type, type.toUpperCase(), type];

    const results = await Promise.allSettled(
      variants.map((variant) => createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(variant))),
    );

    expect(results.filter((result) => result.status === 'fulfilled')).toHaveLength(1);
    const rejected = results.filter((result): result is PromiseRejectedResult => result.status === 'rejected');
    expect(rejected).toHaveLength(variants.length - 1);
    rejected.forEach((result) =>
      expect(result.reason).toMatchObject({ response: { status: 400 }, message: expect.stringContaining('already exists') }),
    );
  });

  it(jira`should accept the same type in another domain ${'AM-7921'}`, async () => {
    const type = uniqueType();
    await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(type));
    const otherDomain = await fixture.otherDomain();

    const created = await createAuthorizationDetailType(otherDomain.id, fixture.accessToken, newType(type));

    expect(created.domain).toEqual(otherDomain.id);
  });
});

describe('Read', () => {
  it(jira`should read a type by id ${'AM-7920'}`, async () => {
    const created = await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(uniqueType()));

    const read = await getAuthorizationDetailType(fixture.domain.id, fixture.accessToken, created.id);

    expect(read).toEqual(created);
  });

  it(jira`should return 404 for an unknown id ${'AM-7920'}`, async () => {
    await expect(getAuthorizationDetailType(fixture.domain.id, fixture.accessToken, 'unknown-id')).rejects.toMatchObject({
      response: { status: 404 },
    });
  });

  it(jira`should return 404 for a type of another domain ${'AM-7921'}`, async () => {
    const created = await createAuthorizationDetailType(fixture.domain.id, fixture.accessToken, newType(uniqueType()));
    const otherDomain = await fixture.otherDomain();

    await expect(getAuthorizationDetailType(otherDomain.id, fixture.accessToken, created.id)).rejects.toMatchObject({
      response: { status: 404 },
    });
  });
});
