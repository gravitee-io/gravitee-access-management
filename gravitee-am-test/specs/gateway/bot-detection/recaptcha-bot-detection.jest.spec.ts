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
import {
  expectBotRejection,
  RECAPTCHA,
  RecaptchaFixture,
  setupRecaptchaFixture,
  siteVerifyCalls,
  submitForgotPassword,
  submitLogin,
} from './fixtures/recaptcha-fixture';

setup(200000);

/**
 * Bot detection on the gateway with the Google reCAPTCHA v3 plugin. The gateway forwards the
 * form's reCAPTCHA token to the plugin's serviceUrl (a WireMock stub here) and only lets the
 * request through when the verdict is a success with a score at or above the plugin's minScore.
 * A rejected request is sent to the error page; the gateway deliberately does not say why.
 */
let fixture: RecaptchaFixture;

beforeAll(async () => {
  fixture = await setupRecaptchaFixture();
});

afterAll(async () => {
  await fixture?.cleanUp();
});

const { TOKENS } = RECAPTCHA;

describe('Bot detection - login is let through or blocked by the reCAPTCHA verdict', () => {
  it('should let a login through when the token scores above the minimum', async () => {
    const response = await submitLogin(fixture, TOKENS.HUMAN);
    expect(response.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/oauth/authorize?`);
  });

  it('should block a login when the token scores below the minimum', async () => {
    const response = await submitLogin(fixture, TOKENS.BOT);
    expectBotRejection(fixture, response);
  });

  it('should block a login when Google does not accept the token', async () => {
    const response = await submitLogin(fixture, TOKENS.REJECTED);
    expectBotRejection(fixture, response);
  });

  it('should block a login when the verify service is unavailable', async () => {
    const response = await submitLogin(fixture, TOKENS.OUTAGE);
    expectBotRejection(fixture, response);
  });

  it('should block a login with no token without calling the verify service', async () => {
    const callsBefore = (await siteVerifyCalls(fixture)).length;

    const response = await submitLogin(fixture);
    expectBotRejection(fixture, response);

    expect((await siteVerifyCalls(fixture)).length).toEqual(callsBefore);
  });
});

describe('Bot detection - the verify call', () => {
  it('should send the configured secret and the submitted token to the verify service', async () => {
    await submitLogin(fixture, TOKENS.HUMAN);

    const calls = await siteVerifyCalls(fixture);
    const last = calls[calls.length - 1];
    expect(last.body).toContain(`secret=${RECAPTCHA.SECRET_KEY}`);
    expect(last.body).toContain(`response=${TOKENS.HUMAN}`);
  });
});

describe('Bot detection - forgot password is guarded the same way', () => {
  it('should let a forgot-password request through when the token scores above the minimum', async () => {
    const response = await submitForgotPassword(fixture, TOKENS.HUMAN);
    expect(response.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/forgotPassword?`);
    expect(response.headers['location']).toContain('success=forgot_password_completed');
  });

  it('should block a forgot-password request when the token scores below the minimum', async () => {
    const response = await submitForgotPassword(fixture, TOKENS.BOT);
    expectBotRejection(fixture, response);
  });
});
