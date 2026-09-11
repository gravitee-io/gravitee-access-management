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
import { waitForDomainSync, waitForOidcReady } from '@management-commands/domain-management-commands';
import { Domain, initClient, initDomain, enableDomain, removeDomain, TestSuiteContext } from './fixture/mfa-setup-fixture';
import { get, processLoginFromContext, processMfaEndToEnd } from './fixture/mfa-flow-fixture';
import { setup } from '../../test-fixture';

setup(300000);

/**
 * AM-2828 / UC-AM-MFA10 — a risk-based MFA challenge.
 *
 * The gateway does not score risk itself. `RiskAssessmentHandler` gathers the current IP and the
 * user's known devices, sends them to the `service:risk-assessment` plugin over the event bus, and
 * puts the verdict in the session for the challenge rule to read. These tests therefore need the
 * plugin present in the gateway distribution — without it the request goes unanswered, no
 * assessment reaches the rule, and every sign-in ends the same way whatever the risk.
 *
 * Each pair is its own control: the same application and the same user, changing only the risk
 * input. One is challenged and the other is not, which is what shows the assessment is being
 * consulted rather than the rule simply being constant.
 */

/** Scored as untrusted by the plugin's bundled ipset. Any address absent from it scores safe. */
const FLAGGED_IP = '103.251.167.21';
const CLEAN_IP = '8.8.8.8';

/**
 * `DeviceRiskAssessmentProvider` scores 0 when the evaluated device is among the user's known
 * ones and 100 when it is not, so these thresholds put the two either side of SAFE.
 */
const THRESHOLDS = { LOW: 1, MEDIUM: 30, HIGH: 70 };

/** Above any score the provider can produce, so even a flagged address comes back SAFE. */
const LENIENT_THRESHOLDS = { LOW: 200, MEDIUM: 300, HIGH: 400 };

const KNOWN_DEVICE = 'risk-known-device';
const UNKNOWN_DEVICE = 'risk-unknown-device';

const domain = {
  admin: { username: 'admin', password: 'adminadmin' },
  domain: { domainHrid: 'mfa-risk-based-am2828' },
} as Domain;

/** Rules read the verdict off the session. `assessment` is an enum, so it needs `name()`. */
const safeRule = (type: string) => `{#context.attributes['risk_assessment'].${type}.assessment.name() == 'SAFE'}`;

const mfaSettings = (dom: Domain, extra: any) => ({
  factors: [],
  settings: {
    mfa: {
      factor: {
        defaultFactorId: dom.domain.factors[0].id,
        applicationFactors: [dom.domain.factors[0]],
      },
      enroll: { active: true, forceEnrollment: true, type: 'required' },
      ...extra.mfa,
    },
    riskAssessment: extra.riskAssessment,
  },
});

const ipSettings = (dom: Domain) =>
  mfaSettings(dom, {
    mfa: { challenge: { active: true, type: 'RISK_BASED', challengeRule: safeRule('ipReputation') } },
    riskAssessment: {
      enabled: true,
      deviceAssessment: { enabled: false },
      ipReputationAssessment: { enabled: true, thresholds: THRESHOLDS },
      geoVelocityAssessment: { enabled: false },
    },
  });

const deviceSettings = (dom: Domain) =>
  mfaSettings(dom, {
    mfa: {
      challenge: { active: true, type: 'RISK_BASED', challengeRule: safeRule('devices') },
      rememberDevice: {
        active: true,
        skipRememberDevice: false,
        expirationTimeSeconds: 3600,
        deviceIdentifierId: dom.domain.devices[0].id,
      },
    },
    riskAssessment: {
      enabled: true,
      deviceAssessment: { enabled: true, thresholds: THRESHOLDS },
      ipReputationAssessment: { enabled: false },
      geoVelocityAssessment: { enabled: false },
    },
  });

/** Same rule as the IP application, but risk assessment switched off, so no verdict is produced. */
const noAssessmentSettings = (dom: Domain) =>
  mfaSettings(dom, {
    mfa: { challenge: { active: true, type: 'RISK_BASED', challengeRule: safeRule('ipReputation') } },
    riskAssessment: {
      enabled: false,
      deviceAssessment: { enabled: false },
      ipReputationAssessment: { enabled: false },
      geoVelocityAssessment: { enabled: false },
    },
  });

/** Same flagged address as the IP application, judged against thresholds it cannot reach. */
const lenientSettings = (dom: Domain) =>
  mfaSettings(dom, {
    mfa: { challenge: { active: true, type: 'RISK_BASED', challengeRule: safeRule('ipReputation') } },
    riskAssessment: {
      enabled: true,
      deviceAssessment: { enabled: false },
      ipReputationAssessment: { enabled: true, thresholds: LENIENT_THRESHOLDS },
      geoVelocityAssessment: { enabled: false },
    },
  });

let ipClient: any;
let deviceClient: any;
let noAssessmentClient: any;
let lenientClient: any;
let authorizationEndpoint: string;

/**
 * A sign-in from a clean session. `MFAChallengeStep.riskBased()` skips the challenge when the user
 * is already strongly authenticated, so reusing a session would hide whatever the assessment said.
 */
const signIn = async (client: any, userIndex: number, options: { deviceId?: string; ip?: string } = {}) => {
  const ctx = new TestSuiteContext(domain, client, domain.domain.users[userIndex], authorizationEndpoint);
  const headers = options.ip ? { 'X-Forwarded-For': options.ip } : {};
  const response = await processLoginFromContext(ctx, Boolean(options.deviceId), options.deviceId, headers);
  const location = response.headers['location'];
  expect(location).toBeDefined();
  return { location, challenged: location.includes('/mfa/challenge'), letThrough: location.includes('code=') };
};

beforeAll(async () => {
  await initDomain(domain, 5);
  ipClient = await initClient(domain, 'risk-ip', ipSettings(domain));
  deviceClient = await initClient(domain, 'risk-device', deviceSettings(domain));
  noAssessmentClient = await initClient(domain, 'risk-none', noAssessmentSettings(domain));
  lenientClient = await initClient(domain, 'risk-lenient', lenientSettings(domain));
  await enableDomain(domain);
  await waitForDomainSync(domain.domain.domainId);
  const oidc = await waitForOidcReady(domain.domain.domainHrid);
  authorizationEndpoint = oidc.body.authorization_endpoint;

  // Both users enrol first: a risk-based challenge only applies to someone who holds a method.
  // The device user remembers KNOWN_DEVICE at the same time, which is what later marks it known.
  await processMfaEndToEnd(new TestSuiteContext(domain, ipClient, domain.domain.users[0], authorizationEndpoint));
  await processMfaEndToEnd(new TestSuiteContext(domain, deviceClient, domain.domain.users[1], authorizationEndpoint), true, KNOWN_DEVICE);
  await processMfaEndToEnd(new TestSuiteContext(domain, noAssessmentClient, domain.domain.users[2], authorizationEndpoint));
  await processMfaEndToEnd(new TestSuiteContext(domain, lenientClient, domain.domain.users[3], authorizationEndpoint));
});

afterAll(async () => {
  await removeDomain(domain);
});

describe('Risk-based challenge', () => {
  it(jira`an address with no reputation against it is let through ${'AM-2828'}`, async () => {
    const result = await signIn(ipClient, 0, { ip: CLEAN_IP });

    expect(result.challenged).toBe(false);
    expect(result.letThrough).toBe(true);
  });

  it(jira`an address the plugin holds as untrusted is challenged ${'AM-2828'}`, async () => {
    // Same application and same user as above — only the address differs.
    const result = await signIn(ipClient, 0, { ip: FLAGGED_IP });

    expect(result.challenged).toBe(true);
    expect(result.letThrough).toBe(false);
  });

  it(jira`a device the user has used before is let through ${'AM-2828'}`, async () => {
    const result = await signIn(deviceClient, 1, { deviceId: KNOWN_DEVICE });

    expect(result.challenged).toBe(false);
    expect(result.letThrough).toBe(true);
  });

  it(jira`a device the user has not used before is challenged ${'AM-2828'}`, async () => {
    // Same application and same user as above — only the device differs.
    const result = await signIn(deviceClient, 1, { deviceId: UNKNOWN_DEVICE });

    expect(result.challenged).toBe(true);
    expect(result.letThrough).toBe(false);
  });

  it(jira`a user is challenged when no assessment is produced at all ${'AM-2828'}`, async () => {
    // The same rule as the IP application, with risk assessment switched off. Nothing reaches the
    // session for the rule to read, so it cannot hold and the challenge stands. Worth pinning: the
    // safe outcome when the assessment is unavailable is to ask for a code, not to wave the user
    // through — and this is what fails if the plugin is ever dropped from the distribution again.
    const result = await signIn(noAssessmentClient, 2, { ip: CLEAN_IP });

    expect(result.challenged).toBe(true);
    expect(result.letThrough).toBe(false);
  });

  it(jira`the same flagged address is let through when the thresholds are not reached ${'AM-2828'}`, async () => {
    // The address the test above is challenged for, judged against thresholds it cannot cross.
    // Only the threshold configuration differs, so the score is shown to be read against it rather
    // than the verdict being fixed by the address alone.
    const result = await signIn(lenientClient, 3, { ip: FLAGGED_IP });

    expect(result.challenged).toBe(false);
    expect(result.letThrough).toBe(true);
  });

  it(jira`a user who has already answered a challenge is not asked again on a flagged address ${'AM-2828'}`, async () => {
    // `MFAChallengeStep.riskBased()` skips the challenge when the user is already strongly
    // authenticated, before the assessment is considered at all. Pinned here because it is easy to
    // mistake for the risk rule passing, and it is why every other test in this file signs in from
    // a clean session.
    const ctx = new TestSuiteContext(domain, ipClient, domain.domain.users[4], authorizationEndpoint);
    const session = await processMfaEndToEnd(ctx);

    const response = await get(ctx.clientAuthUrl, 302, { Cookie: session.cookie, 'X-Forwarded-For': FLAGGED_IP });
    const location = response.headers['location'];

    expect(location).not.toContain('/mfa/challenge');
    expect(location).toContain('code=');
  });
});
