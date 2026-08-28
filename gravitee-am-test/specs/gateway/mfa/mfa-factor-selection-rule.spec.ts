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
import * as cheerio from 'cheerio';
import { getWellKnownOpenIdConfiguration } from '@gateway-commands/oauth-oidc-commands';
import { waitFor } from '@management-commands/domain-management-commands';
import { Domain, initClient, initDomain, enableDomain, removeDomain, TestSuiteContext } from './fixture/mfa-setup-fixture';
import { followUpGet, processLoginFromContext } from './fixture/mfa-flow-fixture';
import { setup } from '../../test-fixture';

setup(300000);

/**
 * AM-2819 — a selection rule decides which factors a user is offered.
 *
 * Every test reads the factor ids actually rendered on the enrolment page, so what is asserted is
 * what a user would be shown. Rules that exclude are paired with rules that include: a rule which
 * excluded everything would look identical to a rule that was never evaluated.
 */
const domain = {
  admin: { username: 'admin', password: 'adminadmin' },
  domain: { domainHrid: 'mfa-factor-selection-rule-am2819' },
} as Domain;

/** Matches one specific username, so the same rule gives two users different results. */
const matchesUsername = (username: string) => `{#context.attributes['user'].username == '${username}'}`;

/** Unparseable on purpose — an unterminated expression. */
const MALFORMED_RULE = "{#context.attributes[";

let factorA: string;
let factorB: string;

/** An application whose factors carry the given rules; the first is always the default. */
const applicationWith = (factors: { id: string; selectionRule: string }[]) => ({
  factors: [],
  settings: {
    mfa: {
      factor: {
        defaultFactorId: factors[0].id,
        applicationFactors: factors,
      },
      stepUpAuthenticationRule: '',
      stepUpAuthentication: { active: false, stepUpAuthenticationRule: '' },
      adaptiveAuthenticationRule: '',
      enrollment: { forceEnrollment: false },
      enroll: { active: true, enrollmentSkipActive: false, forceEnrollment: false, type: 'required' },
      challenge: { active: true, challengeRule: '', type: 'required' },
    },
  },
});

/** The factor ids offered on the enrolment page, in the order they are rendered. */
const offeredFactors = async (ctx: TestSuiteContext): Promise<string[]> => {
  const login = await processLoginFromContext(ctx);
  expect(login.headers['location']).toContain('/mfa/enroll');
  const page = await followUpGet(login, 200);
  const $ = cheerio.load(page.text);
  return $('[name=factorId]')
    .map((_, el) => $(el).attr('value'))
    .get();
};

let ruledFactorBCtx: TestSuiteContext;
let ruledFactorBOtherUserCtx: TestSuiteContext;
let bothRuledFirstUserCtx: TestSuiteContext;
let bothRuledSecondUserCtx: TestSuiteContext;
let bothRuledUnmatchedCtx: TestSuiteContext;
let singleDefaultRuledFalseCtx: TestSuiteContext;
let malformedRuleCtx: TestSuiteContext;

beforeAll(async () => {
  await initDomain(domain, 3);
  factorA = domain.domain.factors[0].id;
  factorB = domain.domain.factors[1].id;

  const [userOne, userTwo, userThree] = domain.domain.users;

  // Factor A carries no rule and is the default; factor B is offered only to userOne.
  const oneRuled = await initClient(
    domain,
    'one-ruled',
    applicationWith([
      { id: factorA, selectionRule: '' },
      { id: factorB, selectionRule: matchesUsername(userOne.username) },
    ]),
  );

  // Each factor is claimed by a different user, and factor A is the default.
  const bothRuled = await initClient(
    domain,
    'both-ruled',
    applicationWith([
      { id: factorA, selectionRule: matchesUsername(userOne.username) },
      { id: factorB, selectionRule: matchesUsername(userTwo.username) },
    ]),
  );

  const singleDefaultRuledFalse = await initClient(
    domain,
    'single-default-false',
    applicationWith([{ id: factorA, selectionRule: '{{ false }}' }]),
  );

  const malformed = await initClient(
    domain,
    'malformed-rule',
    applicationWith([
      { id: factorA, selectionRule: '' },
      { id: factorB, selectionRule: MALFORMED_RULE },
    ]),
  );

  await enableDomain(domain);
  await waitFor(3000);

  const oidc = await getWellKnownOpenIdConfiguration(domain.domain.domainHrid).expect(200);
  const endpoint = oidc.body.authorization_endpoint;

  ruledFactorBCtx = new TestSuiteContext(domain, oneRuled, userOne, endpoint);
  ruledFactorBOtherUserCtx = new TestSuiteContext(domain, oneRuled, userTwo, endpoint);
  bothRuledFirstUserCtx = new TestSuiteContext(domain, bothRuled, userOne, endpoint);
  bothRuledSecondUserCtx = new TestSuiteContext(domain, bothRuled, userTwo, endpoint);
  bothRuledUnmatchedCtx = new TestSuiteContext(domain, bothRuled, userThree, endpoint);
  singleDefaultRuledFalseCtx = new TestSuiteContext(domain, singleDefaultRuledFalse, userOne, endpoint);
  malformedRuleCtx = new TestSuiteContext(domain, malformed, userOne, endpoint);
});

afterAll(async () => {
  await removeDomain(domain);
});

describe('Factor selection rules decide what a user is offered', () => {
  it(jira`a user matching the rule is offered that factor as well as the unruled one ${'AM-2819'}`, async () => {
    expect(await offeredFactors(ruledFactorBCtx)).toEqual(expect.arrayContaining([factorA, factorB]));
  });

  it(jira`a user not matching the rule is offered only the unruled factor ${'AM-2819'}`, async () => {
    // The same application and the same rule as above — only the user differs, which is what
    // shows the rule is being evaluated against the user rather than ignored.
    expect(await offeredFactors(ruledFactorBOtherUserCtx)).toEqual([factorA]);
  });

  it(jira`each user is offered only the factor whose rule names them ${'AM-2819'}`, async () => {
    expect(await offeredFactors(bothRuledFirstUserCtx)).toEqual([factorA]);
    expect(await offeredFactors(bothRuledSecondUserCtx)).toEqual([factorB]);
  });

  it(jira`a user matching no rule falls back to the default factor ${'AM-2819'}`, async () => {
    // Both rules name somebody else, so nothing passes and the default is all that is left.
    expect(await offeredFactors(bothRuledUnmatchedCtx)).toEqual([factorA]);
  });

  it(jira`a lone default factor is offered even when its rule does not pass ${'AM-2819'}`, async () => {
    // The user is never left with nothing to enrol in. Two things in the gateway would each
    // produce this on their own — the default fallback, and a single-factor exemption — so this
    // pins the outcome a user sees rather than which of them delivered it.
    expect(await offeredFactors(singleDefaultRuledFalseCtx)).toEqual([factorA]);
  });

  it(jira`a rule that cannot be evaluated leaves its factor out ${'AM-2819'}`, async () => {
    // The sign-in still reaches enrolment rather than failing on the broken expression.
    expect(await offeredFactors(malformedRuleCtx)).toEqual([factorA]);
  });
});
