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
import { AgentCibaFixture, setupAgentCibaFixture } from './fixtures/agent-ciba-fixture';
import { JWT_FORMAT } from '../../utils/jwt-format';

setup(300000);

const NON_EMPTY_ID = expect.stringMatching(/^.+$/);

let fixture: AgentCibaFixture;

beforeAll(async () => {
  fixture = await setupAgentCibaFixture();
});

afterAll(async () => {
  await fixture?.cleanUp();
});

describe('CIBA × HOSTED_DELEGATED (client_secret_basic) — case A', () => {
  it('issues an access_token and id_token after the device notifier auto-accepts', async () => {
    const { clientId, clientSecret } = fixture.hostedDelegatedBasic;
    await fixture.registerClientWithDelegatedService(clientId, clientSecret);

    const init = await fixture.initiateCiba(clientId, fixture.user.username, clientSecret);
    expect(init.status).toBe(200);
    expect(init.body).toMatchObject({ auth_req_id: NON_EMPTY_ID });

    const token = await fixture.pollTokenUntilGranted(init.body.auth_req_id, clientId, clientSecret);
    expect(token.status).toBe(200);
    expect(token.body.access_token).toEqual(expect.any(String));
    expect(token.body.id_token).toMatch(JWT_FORMAT);
  });
});

describe('CIBA × non-agent control (client_secret_basic) — case C', () => {
  it('issues an access_token for a regular WEB client (proves the rig works)', async () => {
    const { clientId, clientSecret } = fixture.control;
    await fixture.registerClientWithDelegatedService(clientId, clientSecret);

    const init = await fixture.initiateCiba(clientId, fixture.user.username, clientSecret);
    expect(init.status).toBe(200);
    expect(init.body).toMatchObject({ auth_req_id: NON_EMPTY_ID });

    const token = await fixture.pollTokenUntilGranted(init.body.auth_req_id, clientId, clientSecret);
    expect(token.status).toBe(200);
    expect(token.body.access_token).toEqual(expect.any(String));
  });
});

describe('CIBA × HOSTED_DELEGATED with reject-all notifier — case D', () => {
  it('returns access_denied after the device notifier rejects', async () => {
    const { clientId, clientSecret } = fixture.hostedDelegatedBasic;
    await fixture.registerClientWithDelegatedService(clientId, clientSecret);
    await fixture.switchNotifier('reject-all');
    try {
      const init = await fixture.initiateCiba(clientId, fixture.user.username, clientSecret);
      expect(init.status).toBe(200);

      // pollTokenUntilGranted throws on access_denied (it's not a "pending" terminal state);
      // the throw is the proof the reject-all notifier fired.
      await expect(
        fixture.pollTokenUntilGranted(init.body.auth_req_id, clientId, clientSecret),
      ).rejects.toThrow(/access_denied/);
    } finally {
      await fixture.switchNotifier('accept-all');
    }
  });
});

describe('CIBA × CIMD HOSTED_DELEGATED — case F', () => {
  it('issues an access_token for a CIMD-resolved agent client (proves the CIMD path)', async () => {
    expect(fixture.cimdHostedDelegated).not.toBeNull();
    const clientId = fixture.cimdHostedDelegated!.clientId;
    // private_key_jwt agents have no client_secret — the delegated-service callback to AM still needs basic creds,
    // so re-register a confidential client (hostedDelegatedBasic). AM's callback only validates auth_req_id,
    // not which client authenticated, so this works.
    await fixture.registerClientWithDelegatedService(
      fixture.hostedDelegatedBasic.clientId,
      fixture.hostedDelegatedBasic.clientSecret,
    );
    // No JAR — CIMD doesn't map `request_object_signing_alg`, so a signed request JWT would be rejected.
    // Plain `private_key_jwt` still proves the CIMD-resolved client is recognised.
    const init = await fixture.initiateCibaWithPrivateKeyJwt(clientId, fixture.user.username);
    expect(init.status).toBe(200);
    expect(init.body).toMatchObject({ auth_req_id: NON_EMPTY_ID });

    const token = await fixture.pollTokenWithPrivateKeyJwtUntilGranted(init.body.auth_req_id, clientId);
    expect(token.status).toBe(200);
    expect(token.body.access_token).toEqual(expect.any(String));
  });
});

describe('CIBA × HOSTED_DELEGATED with private_key_jwt + JAR — case G', () => {
  it('issues tokens for a signed request object', async () => {
    const { clientId } = fixture.hostedDelegatedPrivateKeyJwt;
    // Same callback-creds workaround as case F (private_key_jwt agent has no secret).
    await fixture.registerClientWithDelegatedService(
      fixture.hostedDelegatedBasic.clientId,
      fixture.hostedDelegatedBasic.clientSecret,
    );

    const requestJwt = await fixture.createRequestJwt(clientId, fixture.user.username);
    const init = await fixture.initiateCibaWithPrivateKeyJwt(clientId, fixture.user.username, requestJwt);
    expect(init.status).toBe(200);
    expect(init.body).toMatchObject({ auth_req_id: NON_EMPTY_ID });

    const token = await fixture.pollTokenWithPrivateKeyJwtUntilGranted(init.body.auth_req_id, clientId);
    expect(token.status).toBe(200);
    expect(token.body.access_token).toEqual(expect.any(String));
    expect(token.body.id_token).toMatch(JWT_FORMAT);
  });
});

/**
 * AM-6854 says CIBA + `token_endpoint_auth_method=none` must be rejected on public agent blueprints.
 * As of this commit AM accepts it. The first test below captures today's behavior (passes now,
 * starts failing the moment the guard ships). The second is the placeholder for the expected
 * behavior — flip the `.skip` when AM-6854 lands.
 */
describe('CIBA × USER_EMBEDDED + none — AM-6854 guard (case B)', () => {
  it('current behavior: gateway accepts CIBA initiation and returns auth_req_id', async () => {
    expect(fixture.userEmbeddedNone).not.toBeNull();
    const { clientId } = fixture.userEmbeddedNone!;
    const init = await fixture.initiateCiba(clientId, fixture.user.username);
    expect(init.status).toBe(200);
    expect(init.body).toMatchObject({ auth_req_id: NON_EMPTY_ID });
  });

  it.skip('expected once AM-6854 ships: gateway rejects CIBA initiation for public agents', async () => {
    expect(fixture.userEmbeddedNone).not.toBeNull();
    const { clientId } = fixture.userEmbeddedNone!;
    const init = await fixture.initiateCiba(clientId, fixture.user.username);
    expect(init.status).toBeGreaterThanOrEqual(400);
    expect(['invalid_client', 'unauthorized_client', 'invalid_grant', 'invalid_request']).toContain(init.body.error);
  });
});

describe('CIBA × AUTONOMOUS — case H', () => {
  it('rejects CIBA initiation for an AUTONOMOUS blueprint', async () => {
    expect(fixture.autonomousBasic).not.toBeNull();
    const { clientId } = fixture.autonomousBasic!;
    const init = await fixture.initiateCiba(clientId, fixture.user.username);
    expect(init.status).toBeGreaterThanOrEqual(400);
    expect(['invalid_client', 'unauthorized_client', 'invalid_grant', 'invalid_request', 'invalid_scope']).toContain(
      init.body.error,
    );
  });
});
