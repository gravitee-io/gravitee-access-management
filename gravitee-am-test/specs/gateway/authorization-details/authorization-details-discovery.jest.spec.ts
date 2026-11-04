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
import { AuthorizationDetailsFixture, setupAuthorizationDetailsFixture } from './fixtures/authorization-details-fixture';

// RFC 9396 Section 10: authorization_details_types_supported lists the types marked for discovery

setup(200000);

let fixture: AuthorizationDetailsFixture;
let fixtureWithoutTypes: AuthorizationDetailsFixture;

beforeAll(async () => {
  [fixture, fixtureWithoutTypes] = await Promise.all([
    setupAuthorizationDetailsFixture(),
    setupAuthorizationDetailsFixture({ withTypes: false }),
  ]);
});

afterAll(async () => {
  await Promise.all([fixture?.cleanUp(), fixtureWithoutTypes?.cleanUp()]);
});

describe('OpenID Provider metadata - authorization_details_types_supported', () => {
  it(jira`should list the types marked for discovery, and no others ${'AM-8049'}`, async () => {
    const metadata = await fixture.discovery();

    expect(metadata.authorization_details_types_supported).toEqual(['account_information', 'payment_initiation']);
  });

  it(jira`should omit the member when no type is marked for discovery ${'AM-8049'}`, async () => {
    const metadata = await fixtureWithoutTypes.discovery();

    expect(metadata).not.toHaveProperty('authorization_details_types_supported');
  });
});
