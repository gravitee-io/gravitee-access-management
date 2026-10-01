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
import { JWT_FORMAT } from '@specs-utils/jwt-format';
import { retryImmediatelyForThisFile, setup } from '../../test-fixture';
import {
  OPTIONAL_PARAMETER,
  TokenFlowPoliciesFixture,
  latencyPolicy,
  parameterValidationPolicy,
  setupTokenFlowPoliciesFixture,
} from './fixture/token-flow-policies-fixture';

setup(200000);
// The Token flow policies are set once per describe block and the tests read them back,
// so a failed test has to be retried before the flow moves on to the next block.
retryImmediatelyForThisFile();

/**
 * The Latency and Validate Request policies on the Token flow of a backend to backend
 * application (client_credentials grant).
 *
 * Both policies come from external plugins, so these tests are what shows a version of either one
 * that stops loading or stops behaving as configured.
 */
let fixture: TokenFlowPoliciesFixture;

beforeAll(async () => {
  fixture = await setupTokenFlowPoliciesFixture();
  // The first token request pays for loading keys and warming the gateway up: keep that out of the timings.
  const warmUp = await fixture.requestToken();
  expect(warmUp.status).toBe(200);
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Token flow - Latency policy', () => {
  const NO_DELAY_BELOW_MS = 1000;
  // Slack above the configured latency: it is a lower bound that is enforced, this one only
  // catches a delay that is applied twice or in the wrong unit.
  const SLACK_MS = 3000;

  it('does not delay the token response when no latency policy is set', async () => {
    await fixture.setTokenPolicies([]);

    const { status, elapsedMs } = await fixture.requestToken();

    expect(status).toBe(200);
    expect(elapsedMs).toBeLessThan(NO_DELAY_BELOW_MS);
  });

  it.each([
    { time: 1500, timeUnit: 'MILLISECONDS' as const, expectedMs: 1500 },
    { time: 2, timeUnit: 'SECONDS' as const, expectedMs: 2000 },
  ])('delays the token response by $time $timeUnit', async ({ time, timeUnit, expectedMs }) => {
    await fixture.setTokenPolicies([latencyPolicy(time, timeUnit)]);

    const { status, body, elapsedMs } = await fixture.requestToken();

    // The delay does not get in the way of the token being issued.
    expect(status).toBe(200);
    expect(body.access_token).toMatch(JWT_FORMAT);
    expect(elapsedMs).toBeGreaterThanOrEqual(expectedMs);
    expect(elapsedMs).toBeLessThan(expectedMs + SLACK_MS);
  });
});

describe('Token flow - Validate Request policy on an optional parameter', () => {
  const REFUSAL_STATUS = 422;

  beforeAll(async () => {
    await fixture.setTokenPolicies([parameterValidationPolicy(String(REFUSAL_STATUS), false)]);
  });

  it('refuses a value that does not satisfy the rule, with the configured status', async () => {
    const { status, body } = await fixture.requestToken({ [OPTIONAL_PARAMETER]: 'NOT-LOWERCASE-123' });

    expect(status).toBe(REFUSAL_STATUS);
    expect(body.error).toBe('invalid_grant');
    expect(body.error_description).toContain("'NOT-LOWERCASE-123' is not valid");
    expect(body.access_token).toBeUndefined();
  });

  it('accepts a value that satisfies the rule', async () => {
    const { status, body } = await fixture.requestToken({ [OPTIONAL_PARAMETER]: 'lowercase' });

    expect(status).toBe(200);
    expect(body.access_token).toMatch(JWT_FORMAT);
  });

  it('accepts a request that leaves the optional parameter out', async () => {
    // The rule is live, as the refusal above shows: it is the parameter being null that lets the
    // request through, not the policy being skipped.
    const { status, body } = await fixture.requestToken();

    expect(status).toBe(200);
    expect(body.access_token).toMatch(JWT_FORMAT);
  });
});

describe('Token flow - Validate Request policy on a required parameter', () => {
  const REFUSAL_STATUS = 422;

  beforeAll(async () => {
    await fixture.setTokenPolicies([parameterValidationPolicy(String(REFUSAL_STATUS), true)]);
  });

  it('refuses a request that leaves the parameter out', async () => {
    // Same rule as above, only marked required: this is what the optional flag changes.
    const { status, body } = await fixture.requestToken();

    expect(status).toBe(REFUSAL_STATUS);
    expect(body.error).toBe('invalid_grant');
    expect(body.access_token).toBeUndefined();
  });
});
