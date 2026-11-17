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
import { AUTHORIZATION_DETAILS_TEST, expectClientError, expectLoginPage, toDetailsParam } from './fixtures/authorization-details-fixture';
import {
  AuthorizationDetailsDcrFixture,
  expectInvalidClientMetadata,
  setupAuthorizationDetailsDcrFixture,
} from './fixtures/authorization-details-dcr-fixture';

// RFC 9396 section 10: the authorization_details_types client metadata on the client configuration endpoint (RFC 7592)
// and in authorization requests of a client registered through DCR

setup(200000);

const { PAYMENT_TYPE, ACCOUNT_TYPE } = AUTHORIZATION_DETAILS_TEST;

let fixture: AuthorizationDetailsDcrFixture;

beforeAll(async () => {
  fixture = await setupAuthorizationDetailsDcrFixture();
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('Gateway - DCR client configuration with authorization_details_types', () => {
  it(jira`should read the authorization detail types of a client ${'AM-8147'}`, async () => {
    const client = await fixture.registerClient({ authorization_details_types: [PAYMENT_TYPE] });

    const response = await fixture.read(client).expect(200);

    expect(response.body.authorization_details_types).toEqual([PAYMENT_TYPE]);
  });

  it(jira`should omit authorization detail types when reading a client without any ${'AM-8147'}`, async () => {
    const client = await fixture.registerClient();

    const response = await fixture.read(client).expect(200);

    expect(response.body).not.toHaveProperty('authorization_details_types');
  });

  it(jira`should replace the authorization detail types on PUT ${'AM-8147'}`, async () => {
    const client = await fixture.registerClient({ authorization_details_types: [PAYMENT_TYPE] });

    const response = await fixture.update(client, { authorization_details_types: [ACCOUNT_TYPE] }).expect(200);

    expect(response.body.authorization_details_types).toEqual([ACCOUNT_TYPE]);
  });

  it(jira`should keep the authorization detail types on PUT without the member ${'AM-8147'}`, async () => {
    const client = await fixture.registerClient({ authorization_details_types: [PAYMENT_TYPE] });

    const response = await fixture.update(client, { client_name: 'renamed' }).expect(200);

    expect(response.body.authorization_details_types).toEqual([PAYMENT_TYPE]);
  });

  it(jira`should remove every authorization detail type on PUT with an empty list ${'AM-8147'}`, async () => {
    const client = await fixture.registerClient({ authorization_details_types: [PAYMENT_TYPE] });

    const response = await fixture.update(client, { authorization_details_types: [] }).expect(200);

    expect(response.body).not.toHaveProperty('authorization_details_types');
  });

  it(jira`should replace the authorization detail types on PATCH ${'AM-8147'}`, async () => {
    const client = await fixture.registerClient({ authorization_details_types: [PAYMENT_TYPE] });

    const response = await fixture.patch(client, { authorization_details_types: [PAYMENT_TYPE, ACCOUNT_TYPE] }).expect(200);

    expect(response.body.authorization_details_types).toEqual([PAYMENT_TYPE, ACCOUNT_TYPE]);
  });

  it(jira`should keep the authorization detail types on PATCH without the member ${'AM-8147'}`, async () => {
    const client = await fixture.registerClient({ authorization_details_types: [PAYMENT_TYPE] });

    const response = await fixture.patch(client, { client_name: 'renamed' }).expect(200);

    expect(response.body.authorization_details_types).toEqual([PAYMENT_TYPE]);
  });

  it(jira`should reject an update with a type that does not exist in the domain and keep the client unchanged ${'AM-8148'}`, async () => {
    const client = await fixture.registerClient({ authorization_details_types: [PAYMENT_TYPE] });

    expectInvalidClientMetadata(
      await fixture.patch(client, { authorization_details_types: ['unknown_type'] }),
      "authorization_details_types: type 'unknown_type' is not an authorization detail type of the domain",
    );
    expect((await fixture.read(client).expect(200)).body.authorization_details_types).toEqual([PAYMENT_TYPE]);
  });

  it(jira`should reject an update with authorization detail types that are not an array ${'AM-8148'}`, async () => {
    const client = await fixture.registerClient();

    expectInvalidClientMetadata(
      await fixture.update(client, { authorization_details_types: PAYMENT_TYPE }),
      'authorization_details_types: invalid value',
    );
  });
});

describe('Gateway - authorization requests of a client registered through DCR', () => {
  const notAllowed = (type: string) => `authorization_details[0] of type '${type}' is not allowed for this client`;

  it(jira`should accept authorization details of a registered type ${'AM-8150'}`, async () => {
    const client = await fixture.registerClient({ authorization_details_types: [ACCOUNT_TYPE] });

    expectLoginPage(await fixture.authorize({ client_id: client.clientId, authorization_details: toDetailsParam({ type: ACCOUNT_TYPE }) }));
  });

  it(jira`should reject authorization details of a type the client did not register ${'AM-8150'}`, async () => {
    const client = await fixture.registerClient({ authorization_details_types: [ACCOUNT_TYPE] });

    expectClientError(
      await fixture.authorize({ client_id: client.clientId, authorization_details: toDetailsParam({ type: PAYMENT_TYPE }) }),
      'invalid_authorization_details',
      notAllowed(PAYMENT_TYPE),
    );
  });

  it(jira`should accept authorization details of a type added through the client configuration endpoint ${'AM-8150'}`, async () => {
    const client = await fixture.registerClient();
    const details = toDetailsParam({ type: ACCOUNT_TYPE });
    expectClientError(
      await fixture.authorize({ client_id: client.clientId, authorization_details: details }),
      'invalid_authorization_details',
      notAllowed(ACCOUNT_TYPE),
    );

    await fixture.patch(client, { authorization_details_types: [ACCOUNT_TYPE] }).expect(200);

    expectLoginPage(await fixture.authorize({ client_id: client.clientId, authorization_details: details }));
  });
});
