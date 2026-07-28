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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { clearEmails, getLastEmail } from '@utils-commands/email-commands';
import { uniqueName } from '@utils-commands/misc';
import { CloudEmailFixture, setupCloudEmailFixture } from './fixtures/cloud-email-fixture';
import { setup } from '../test-fixture';

setup(200000);

/**
 * AM-7229 — in managed cloud, links in emails are built from the environment's entrypoint rather than
 * from the data plane's gateway url.
 *
 * These are the request-less flows: nothing in them carries an end-user request AM could take a
 * hostname from, so the entrypoint is the only thing that can produce a correct link.
 *
 * The entrypoint host is synthetic and does not resolve, so every assertion is on the link string.
 * Never follow it.
 */
describe('AM - Cloud - entrypoint url in email links', () => {
  let fixture: CloudEmailFixture;
  let expectedOrigin: string;

  beforeAll(async () => {
    const accessToken = await requestAdminAccessToken();
    fixture = await setupCloudEmailFixture(accessToken);
    // Through URL rather than string concatenation: uniqueName produces mixed case and hostnames are
    // case-insensitive, so both sides have to be normalised the same way.
    expectedOrigin = new URL(`https://${fixture.entrypointHost}`).origin;
  });

  afterAll(async () => {
    if (fixture) await fixture.cleanup();
  });

  const emailAddress = () => `${uniqueName('am7229', true)}@acme.fr`;

  it('builds the pre-registration link from the environment entrypoint', async () => {
    const email = emailAddress();
    await clearEmails(email);

    await fixture.createPreRegisteredUser(email);

    const link = (await getLastEmail(5000, email)).extractLink();
    expect(new URL(link).origin).toEqual(expectedOrigin);
    await clearEmails(email);
  });

  it('builds the re-sent registration confirmation link from the environment entrypoint', async () => {
    const email = emailAddress();
    await clearEmails(email);

    const user = await fixture.createPreRegisteredUser(email);
    // Drop the email the creation itself sent, so the assertion cannot pass on the wrong one.
    await getLastEmail(5000, email);
    await clearEmails(email);

    await fixture.resendRegistrationConfirmation(user.id);

    const link = (await getLastEmail(5000, email)).extractLink();
    expect(new URL(link).origin).toEqual(expectedOrigin);
    await clearEmails(email);
  });

  it('builds the SCIM-provisioned registration link from the environment entrypoint', async () => {
    // The gateway's own email path. SCIM has no end-user request, so it keeps resolving from the
    // entrypoint even once AM-7230 makes request-bearing flows use the request hostname.
    const email = emailAddress();
    await clearEmails(email);

    await fixture.createScimUser(email);

    const link = (await getLastEmail(5000, email)).extractLink();
    expect(new URL(link).origin).toEqual(expectedOrigin);
    await clearEmails(email);
  });
});
