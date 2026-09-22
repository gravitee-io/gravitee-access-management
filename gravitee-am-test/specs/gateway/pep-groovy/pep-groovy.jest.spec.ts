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
import jwt from 'jsonwebtoken';
import { retryImmediatelyForThisFile, setup } from '../../test-fixture';
import {
  AGENTS,
  EnterpriseIdpFixture,
  ID_JAG_TOKEN_TYPE,
  IdJagRequest,
  ISSUE_ID_JAG,
  PARTNERS,
  Partner,
  setupEnterpriseIdp,
  Tuple,
  USERS,
} from './fixtures/enterprise-idp-fixture';

setup(300000);
retryImmediatelyForThisFile();

const TUPLES: Tuple[] = [
  { subject: 'user1', action: ISSUE_ID_JAG, resource: PARTNERS.chat.audience },
  { subject: 'user1', action: ISSUE_ID_JAG, resource: PARTNERS.calculator.audience },
  { subject: 'user2', action: ISSUE_ID_JAG, resource: PARTNERS.weather.audience },

  { subject: 'claude', action: ISSUE_ID_JAG, resource: PARTNERS.chat.audience },
  { subject: 'claude', action: ISSUE_ID_JAG, resource: PARTNERS.weather.audience },
  { subject: 'codex', action: ISSUE_ID_JAG, resource: PARTNERS.calculator.audience },
];

const EVERY_COMBINATION = AGENTS.flatMap((agent) =>
  USERS.flatMap((user) => (Object.keys(PARTNERS) as Partner[]).map((partner) => ({ agent, user, partner }))),
);

const EXPECTED_DECISIONS: (IdJagRequest & { decision: string })[] = [
  { agent: 'claude', user: 'user1', partner: 'chat', decision: 'permit' },
  { agent: 'claude', user: 'user1', partner: 'weather', decision: 'denied: user' },
  { agent: 'claude', user: 'user1', partner: 'calculator', decision: 'denied: client' },
  { agent: 'claude', user: 'user2', partner: 'chat', decision: 'denied: user' },
  { agent: 'claude', user: 'user2', partner: 'weather', decision: 'permit' },
  { agent: 'claude', user: 'user2', partner: 'calculator', decision: 'denied: user, client' },
  { agent: 'codex', user: 'user1', partner: 'chat', decision: 'denied: client' },
  { agent: 'codex', user: 'user1', partner: 'weather', decision: 'denied: user, client' },
  { agent: 'codex', user: 'user1', partner: 'calculator', decision: 'permit' },
  { agent: 'codex', user: 'user2', partner: 'chat', decision: 'denied: user, client' },
  { agent: 'codex', user: 'user2', partner: 'weather', decision: 'denied: client' },
  { agent: 'codex', user: 'user2', partner: 'calculator', decision: 'denied: user' },
];

let fixture: EnterpriseIdpFixture;

beforeAll(async () => {
  fixture = await setupEnterpriseIdp();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Without the PEP, the Enterprise IdP lets every agent reach every partner', () => {
  beforeAll(async () => {
    await fixture.setPep(null);
  });

  it.each(EVERY_COMBINATION)('$agent acting for $user gets an ID-JAG for $partner', async (idJagRequest) => {
    const response = await fixture.requestIdJag(idJagRequest);

    expect(response.status).toBe(200);
    expect(response.body.issued_token_type).toBe(ID_JAG_TOKEN_TYPE);
  });
});

describe('The PEP asks the PDP one AuthZEN batch evaluation for the user and the client', () => {
  beforeAll(async () => {
    await fixture.setPep(TUPLES, { inspect: true });
  });

  it('should evaluate the user and the client against the partner audience', async () => {
    const response = await fixture.requestIdJag({ agent: 'claude', user: 'user1', partner: 'chat' });

    expect(response.status).toBe(403);
    const evaluation = (type: string, id: string) => ({
      subject: { type, id },
      action: { name: ISSUE_ID_JAG },
      resource: { type: 'audience', id: PARTNERS.chat.audience },
    });
    expect(JSON.parse(response.body.error_description)).toEqual({
      context: { client_id: 'claude' },
      evaluations: [evaluation('user', fixture.subjectOf('user1')), evaluation('client', 'claude')],
      options: { evaluations_semantic: 'execute_all' },
    });
  });
});

describe('With the PEP, an ID-JAG needs both the user and the client to be permitted for the audience', () => {
  beforeAll(async () => {
    await fixture.setPep(TUPLES);
  });

  it.each(EXPECTED_DECISIONS)('$agent acting for $user on $partner: $decision', async ({ agent, user, partner, decision }) => {
    const response = await fixture.requestIdJag({ agent, user, partner });

    if (decision === 'permit') {
      expect(response.status).toBe(200);
      const idJag = jwt.decode(response.body.access_token) as Record<string, any>;
      expect(idJag.sub).toBe(fixture.subjectOf(user));
      expect(idJag.client_id).toBe(`${agent}-at-${partner}`);
      expect(idJag.aud).toBe(PARTNERS[partner].audience);
      expect(idJag.resource).toBe(PARTNERS[partner].resource);
    } else {
      expect(response.status).toBe(403);
      expect(response.body).toEqual({ error: 'invalid_grant', error_description: decision });
    }
  });
});
