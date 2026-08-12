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
import { createIdp, deleteIdp, getIdp } from '@management-commands/idp-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { deleteApplication } from '@management-commands/application-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../../test-fixture';
import { jira } from '@specs-utils/jira';
import { IdpFixture, setupIdpFixture, buildInlineIdpBody } from './fixtures/idp-fixture';

setup();

let fixture: IdpFixture;

beforeAll(async () => {
  fixture = await setupIdpFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

const newIdp = () =>
  createIdp(
    fixture.domain.id,
    fixture.accessToken,
    buildInlineIdpBody([{ firstname: 'Guard', lastname: 'Test', username: 'guard', password: 'Guard#P4ssw0rd!' }]),
  );

const appUsing = (idpId: string) =>
  createTestApp(uniqueName('guard-app', true), fixture.domain, fixture.accessToken, 'WEB', {
    identityProviders: new Set([{ identity: idpId, priority: 0 }]),
    settings: {
      oauth: {
        redirectUris: ['https://example.com/callback'],
        grantTypes: ['authorization_code'],
      },
    },
  });

describe('Identity provider deletion guard', () => {
  it(jira`should reject deletion while an application still uses the provider ${'AM-7447'}`, async () => {
    const idp = await newIdp();
    await appUsing(idp.id);

    await expect(deleteIdp(fixture.domain.id, fixture.accessToken, idp.id)).rejects.toMatchObject({
      response: { status: 400 },
    });

    // The provider must survive the rejected delete.
    const stillThere = await getIdp(fixture.domain.id, fixture.accessToken, idp.id);
    expect(stillThere.id).toEqual(idp.id);
  });

  it(jira`should allow deletion when no application uses the provider ${'AM-7447'}`, async () => {
    const idp = await newIdp();

    await deleteIdp(fixture.domain.id, fixture.accessToken, idp.id);

    await expect(getIdp(fixture.domain.id, fixture.accessToken, idp.id)).rejects.toMatchObject({
      response: { status: 404 },
    });
  });

  it(jira`should allow deletion once the last application using it is removed ${'AM-7447'}`, async () => {
    const idp = await newIdp();
    const app = await appUsing(idp.id);

    await expect(deleteIdp(fixture.domain.id, fixture.accessToken, idp.id)).rejects.toMatchObject({
      response: { status: 400 },
    });

    await deleteApplication(fixture.domain.id, fixture.accessToken, app.id);
    await deleteIdp(fixture.domain.id, fixture.accessToken, idp.id);

    await expect(getIdp(fixture.domain.id, fixture.accessToken, idp.id)).rejects.toMatchObject({
      response: { status: 404 },
    });
  });
});
