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
import { setup } from '../../test-fixture';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { createDpopKey } from '../dpop/dpop-proof';
import { IdJagFixture, setupIdJagFixture } from './fixtures/id-jag-fixture';
import { ACCESS_TOKEN_TYPE, TOKEN_EXCHANGE_GRANT } from './fixtures/token-exchange-fixture';

setup(300000);

let fixture: IdJagFixture;

const decode = (token: string) => jwt.decode(token) as any;

const calendarTarget = () => `&audience=${encodeURIComponent(fixture.audience)}&resource=${encodeURIComponent(fixture.calendar.resource)}`;

const requestIdJag = async (expectedStatus: number, dpopProof?: string) => {
  const { idToken } = await fixture.subjectTokens();
  const exchange = fixture.requestIdJag(idToken, calendarTarget());
  return (dpopProof ? exchange.set('DPoP', dpopProof) : exchange).expect(expectedStatus);
};

const tokenEndpointProof = async () => {
  const key = await createDpopKey();
  return { key, proof: await key.proof({ htu: fixture.oidc.token_endpoint, htm: 'POST' }) };
};

beforeAll(async () => {
  fixture = await setupIdJagFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('ID-JAG issuance - DPoP key binding', () => {
  it('should bind the ID-JAG to the key of a valid DPoP proof', async () => {
    const { key, proof } = await tokenEndpointProof();

    const response = await requestIdJag(200, proof);

    expect(response.body.token_type).toBe('N_A');
    expect(decode(response.body.access_token).cnf).toEqual({ jkt: key.jkt });
  });

  it('should issue an unbound ID-JAG without a DPoP proof', async () => {
    const response = await requestIdJag(200);

    expect(response.body.token_type).toBe('N_A');
    expect(decode(response.body.access_token)).not.toHaveProperty('cnf');
  });

  it('should refuse the exchange on an invalid DPoP proof', async () => {
    const key = await createDpopKey();
    const proof = await key.proof({ htu: 'https://attacker.example/token', htm: 'POST' });

    const response = await requestIdJag(400, proof);

    expect(response.body.error).toBe('invalid_dpop_proof');
    expect(response.body).not.toHaveProperty('access_token');
  });

  it('should keep binding an exchanged access token to the proof key', async () => {
    const { key, proof } = await tokenEndpointProof();
    const { accessToken } = await fixture.subjectTokens();

    const response = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=${TOKEN_EXCHANGE_GRANT}&subject_token=${accessToken}&subject_token_type=${ACCESS_TOKEN_TYPE}` +
        `&requested_token_type=${ACCESS_TOKEN_TYPE}`,
      { 'Content-type': 'application/x-www-form-urlencoded', Authorization: `Basic ${fixture.basicAuth}`, DPoP: proof },
    ).expect(200);

    expect(response.body.token_type).toBe('DPoP');
    expect(decode(response.body.access_token).cnf).toEqual({ jkt: key.jkt });
  });

  describe('with an application ID-JAG custom claim named cnf', () => {
    beforeAll(async () => {
      await fixture.setCrossAppAccess(fixture.bothResourceServerSettings(), 300, [
        { tokenType: 'ID_JAG', claimName: 'cnf', claimValue: 'forged' },
      ]);
    });

    it('should keep the binding to the proof key', async () => {
      const { key, proof } = await tokenEndpointProof();

      const response = await requestIdJag(200, proof);

      expect(decode(response.body.access_token).cnf).toEqual({ jkt: key.jkt });
    });

    it('should not forge a binding without a DPoP proof', async () => {
      const response = await requestIdJag(200);

      expect(decode(response.body.access_token)).not.toHaveProperty('cnf');
    });
  });
});
