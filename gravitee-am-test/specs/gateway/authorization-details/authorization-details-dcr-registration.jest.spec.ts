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
import { jira } from '@specs-utils/jira';
import { setup } from '../../test-fixture';
import { AUTHORIZATION_DETAILS_TEST } from './fixtures/authorization-details-fixture';
import {
  AuthorizationDetailsDcrFixture,
  expectInvalidClientMetadata,
  setupAuthorizationDetailsDcrFixture,
} from './fixtures/authorization-details-dcr-fixture';

// RFC 9396 section 10: the authorization_details_types client metadata on registration (RFC 7591)

setup(200000);

const { PAYMENT_TYPE, ACCOUNT_TYPE, NOT_ENABLED_TYPE } = AUTHORIZATION_DETAILS_TEST;

let fixture: AuthorizationDetailsDcrFixture;

beforeAll(async () => {
  fixture = await setupAuthorizationDetailsDcrFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('Gateway - DCR registration with authorization_details_types', () => {
  it(jira`should register a client without authorization detail types ${'AM-8147'}`, async () => {
    const response = await fixture.register().expect(201);

    expect(response.body.client_id).toEqual(expect.any(String));
    expect(response.body).not.toHaveProperty('authorization_details_types');
  });

  it(jira`should register a client with authorization detail types ${'AM-8147'}`, async () => {
    const response = await fixture.register({ authorization_details_types: [PAYMENT_TYPE, ACCOUNT_TYPE] }).expect(201);

    expect(response.body.authorization_details_types).toEqual([PAYMENT_TYPE, ACCOUNT_TYPE]);
  });

  it(jira`should register a client with a type not advertised in discovery ${'AM-8147'}`, async () => {
    const response = await fixture.register({ authorization_details_types: [NOT_ENABLED_TYPE] }).expect(201);

    expect(response.body.authorization_details_types).toEqual([NOT_ENABLED_TYPE]);
  });

  it(jira`should register a client with an empty list of authorization detail types ${'AM-8147'}`, async () => {
    const response = await fixture.register({ authorization_details_types: [] }).expect(201);

    expect(response.body).not.toHaveProperty('authorization_details_types');
  });
});

describe('Gateway - DCR registration with invalid authorization_details_types', () => {
  it(jira`should reject authorization detail types that are not an array ${'AM-8148'}`, async () => {
    expectInvalidClientMetadata(
      await fixture.register({ authorization_details_types: PAYMENT_TYPE }),
      'authorization_details_types: invalid value',
    );
  });

  it(jira`should reject an authorization detail type that is not a string ${'AM-8148'}`, async () => {
    expectInvalidClientMetadata(
      await fixture.register({ authorization_details_types: [{ type: PAYMENT_TYPE }] }),
      'authorization_details_types: invalid value',
    );
  });

  it(jira`should reject an empty authorization detail type ${'AM-8148'}`, async () => {
    expectInvalidClientMetadata(
      await fixture.register({ authorization_details_types: [PAYMENT_TYPE, ''] }),
      'authorization_details_types: type is required',
    );
  });

  it(jira`should reject a duplicated authorization detail type ${'AM-8148'}`, async () => {
    expectInvalidClientMetadata(
      await fixture.register({ authorization_details_types: [PAYMENT_TYPE, PAYMENT_TYPE] }),
      `authorization_details_types: type '${PAYMENT_TYPE}' appears more than once`,
    );
  });

  it(jira`should reject a type that does not exist in the domain ${'AM-8148'}`, async () => {
    expectInvalidClientMetadata(
      await fixture.register({ authorization_details_types: [PAYMENT_TYPE, 'unknown_type'] }),
      "authorization_details_types: type 'unknown_type' is not an authorization detail type of the domain",
    );
  });

  it(jira`should reject a type that differs from the domain type only by case ${'AM-8148'}`, async () => {
    expectInvalidClientMetadata(
      await fixture.register({ authorization_details_types: ['Payment_Initiation'] }),
      "authorization_details_types: type 'Payment_Initiation' is not an authorization detail type of the domain",
    );
  });

  it('should reject a malformed value of another client metadata member', async () => {
    expectInvalidClientMetadata(
      await fixture.register({ redirect_uris: AUTHORIZATION_DETAILS_TEST.REDIRECT_URI }),
      'redirect_uris: invalid value',
    );
  });
});

describe('Gateway - DCR registration from a template with authorization_details_types', () => {
  it(jira`should inherit the authorization detail types of the template ${'AM-8149'}`, async () => {
    const template = await fixture.createTemplate([{ type: PAYMENT_TYPE }]);

    const response = await fixture.register({ software_id: template.id }).expect(201);

    expect(response.body.authorization_details_types).toEqual([PAYMENT_TYPE]);
  });

  it(jira`should replace the template types with the types of the request ${'AM-8149'}`, async () => {
    const template = await fixture.createTemplate([{ type: PAYMENT_TYPE }]);

    const response = await fixture.register({ software_id: template.id, authorization_details_types: [ACCOUNT_TYPE] }).expect(201);

    expect(response.body.authorization_details_types).toEqual([ACCOUNT_TYPE]);
  });

  it(jira`should reject a request type that does not exist in the domain ${'AM-8148'}`, async () => {
    const template = await fixture.createTemplate([{ type: PAYMENT_TYPE }]);

    expectInvalidClientMetadata(
      await fixture.register({ software_id: template.id, authorization_details_types: ['unknown_type'] }),
      "authorization_details_types: type 'unknown_type' is not an authorization detail type of the domain",
    );
  });
});
