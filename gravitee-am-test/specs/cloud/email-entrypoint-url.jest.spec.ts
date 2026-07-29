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
 * In managed cloud, email links are built from the environment's entrypoint rather than the data
 * plane's gateway url. These are the request-less flows, with no end-user request to take a hostname
 * from, so the entrypoint is the only thing that can produce a correct link.
 *
 * The entrypoint host is synthetic and never resolves: assert on the link string, never follow it.
 */
describe('AM - Cloud - entrypoint url in email links', () => {
  let fixture: CloudEmailFixture;
  let expectedOrigin: string;
  let generatedOrigin: string;

  beforeAll(async () => {
    const accessToken = await requestAdminAccessToken();
    fixture = await setupCloudEmailFixture(accessToken);
    // Through URL rather than string concatenation: uniqueName produces mixed case and hostnames are
    // case-insensitive, so both sides have to be normalised the same way.
    expectedOrigin = new URL(`https://${fixture.overridingHost}`).origin;
    generatedOrigin = new URL(`https://${fixture.entrypointHost}`).origin;
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
    // The gateway's own email path, and the one flow with no end-user request to fall back on.
    const email = emailAddress();
    await clearEmails(email);

    await fixture.createScimUser(email);

    const link = (await getLastEmail(5000, email)).extractLink();
    expect(new URL(link).origin).toEqual(expectedOrigin);
    await clearEmails(email);
  });

  it('builds the reset password link from the host the user reached the gateway on', async () => {
    // The generated access point is a real host for this environment but not the one the request-less
    // flows above resolve to, so seeing it here is only possible if the request decided it.
    await clearEmails(fixture.resetPasswordUserEmail);

    await fixture.requestForgotPassword(fixture.entrypointHost);

    const link = (await getLastEmail(5000, fixture.resetPasswordUserEmail)).extractLink();
    expect(new URL(link).origin).toEqual(generatedOrigin);
    await clearEmails(fixture.resetPasswordUserEmail);
  });

  it('ignores a request host that is not one of the environment entrypoints', async () => {
    // A forged Host must not steer the link, the reset token travels in its query string.
    await clearEmails(fixture.resetPasswordUserEmail);

    await fixture.requestForgotPassword('evil.example.com');

    const link = (await getLastEmail(5000, fixture.resetPasswordUserEmail)).extractLink();
    expect(new URL(link).origin).toEqual(expectedOrigin);
    expect(link).not.toContain('evil.example.com');
    await clearEmails(fixture.resetPasswordUserEmail);
  });

  // The blocked-account mail links to /resetPassword too, so its host matters for the same reason.
  // It travels a different route to get there: the origin comes off the authentication context
  // rather than the routing context, and survives a detached thread.
  it('builds the blocked account link from the host the user reached the gateway on', async () => {
    const user = await fixture.createLoginUser();
    await clearEmails(user.email);

    await fixture.failLogin(user.username, fixture.entrypointHost);

    const email = await getLastEmail(5000, user.email);
    const link = email.extractLink();
    expect(new URL(link).origin).toEqual(generatedOrigin);
    expect(new URL(link).pathname).toContain('/resetPassword');
    await clearEmails(user.email);
  });

  it('ignores a forged request host on the blocked account link', async () => {
    // A fresh user: the previous lockout is sticky for accountBlockedDuration, so re-using one
    // never sends a second mail.
    const user = await fixture.createLoginUser();
    await clearEmails(user.email);

    await fixture.failLogin(user.username, 'evil.example.com');

    const link = (await getLastEmail(5000, user.email)).extractLink();
    expect(new URL(link).origin).toEqual(expectedOrigin);
    expect(link).not.toContain('evil.example.com');
    await clearEmails(user.email);
  });

  // Self-service registration is the third request-bearing flow, and the only registration one. Its
  // origin comes off the routing context in RegisterProcessHandler, so it reaches the url builder by
  // yet another route than the two above.
  it('builds the self-service registration link from the host the user reached the gateway on', async () => {
    const email = emailAddress();
    await clearEmails(email);

    await fixture.selfServiceRegister(email, fixture.entrypointHost);

    const link = (await getLastEmail(5000, email)).extractLink();
    expect(new URL(link).origin).toEqual(generatedOrigin);
    await clearEmails(email);
  });

  it('ignores a forged request host on the self-service registration link', async () => {
    const email = emailAddress();
    await clearEmails(email);

    await fixture.selfServiceRegister(email, 'evil.example.com');

    const link = (await getLastEmail(5000, email)).extractLink();
    expect(new URL(link).origin).toEqual(expectedOrigin);
    expect(link).not.toContain('evil.example.com');
    await clearEmails(email);
  });
});
