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
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../../test-fixture';
import { BlueprintFixture, setupBlueprintFixture } from './fixtures/blueprint-fixture';

setup(60000);

let fixture: BlueprintFixture;

beforeAll(async () => {
  fixture = await setupBlueprintFixture();
});

afterAll(async () => {
  await fixture?.cleanUp();
});

describe('Blueprint Application — Creation & Type Inference', () => {
  it('should create USER_EMBEDDED agent and infer NATIVE type', async () => {
    const app = await fixture.createBlueprintApp('USER_EMBEDDED');

    expect(app.type).toEqual('native');
    expect(app.settings.advanced.agentIdentityMode).toBe(true);
    expect(app.settings.agent.agentType).toEqual('user_embedded');
  });

  it('should create HOSTED_DELEGATED agent and infer WEB type', async () => {
    const app = await fixture.createBlueprintApp('HOSTED_DELEGATED');

    expect(app.type).toEqual('web');
    expect(app.settings.advanced.agentIdentityMode).toBe(true);
    expect(app.settings.agent.agentType).toEqual('hosted_delegated');
  });

  it('should create AUTONOMOUS agent and infer SERVICE type', async () => {
    const app = await fixture.createBlueprintApp('AUTONOMOUS');

    expect(app.type).toEqual('service');
    expect(app.settings.advanced.agentIdentityMode).toBe(true);
    expect(app.settings.agent.agentType).toEqual('autonomous');
  });

  it('should apply PKCE defaults for USER_EMBEDDED', async () => {
    const app = await fixture.createBlueprintApp('USER_EMBEDDED');

    expect(app.settings.oauth.forcePKCE).toBe(true);
    expect(app.settings.oauth.forceS256CodeChallengeMethod).toBe(true);
    expect(app.settings.oauth.tokenEndpointAuthMethod).toEqual('none');
  });

  it('should not require redirect URI for AUTONOMOUS', async () => {
    const app = await fixture.createBlueprintApp('AUTONOMOUS');

    expect(app.settings.oauth.redirectUris).toBeUndefined();
  });

  it('should include token_exchange in default grant types for HOSTED_DELEGATED', async () => {
    const app = await fixture.createBlueprintApp('HOSTED_DELEGATED');

    expect(app.settings.oauth.grantTypes).toContain('urn:ietf:params:oauth:grant-type:token-exchange');
    expect(app.settings.oauth.grantTypes).toContain('authorization_code');
    expect(app.settings.oauth.grantTypes).toContain('client_credentials');
  });

  it('should include token_exchange in default grant types for AUTONOMOUS', async () => {
    const app = await fixture.createBlueprintApp('AUTONOMOUS');

    expect(app.settings.oauth.grantTypes).toContain('urn:ietf:params:oauth:grant-type:token-exchange');
    expect(app.settings.oauth.grantTypes).toContain('client_credentials');
  });
});

describe('Blueprint Application — Validation', () => {
  it('should reject creation without agentSettings when type is AGENT', async () => {
    const response = await fixture.createRawApp({
      name: uniqueName('agent-no-settings', true),
      type: 'AGENT',
    });

    expect(response.status).toBeGreaterThanOrEqual(400);
  });

  it('should reject changing existing app type to AGENT via updateType', async () => {
    // Create a normal web app first
    const createResponse = await fixture.createRawApp({
      name: uniqueName('web-app', true),
      type: 'WEB',
      redirectUris: ['https://example.com/callback'],
    });
    const app = await createResponse.json();

    // Try to change type to AGENT
    const response = await fetch(
      `${process.env.AM_MANAGEMENT_URL}/management/organizations/${process.env.AM_DEF_ORG_ID}/environments/${process.env.AM_DEF_ENV_ID}/domains/${fixture.domain.id}/applications/${app.id}/type`,
      {
        method: 'PUT',
        headers: {
          Authorization: `Bearer ${fixture.accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ type: 'AGENT' }),
      },
    );

    expect(response.status).toBeGreaterThanOrEqual(400);
  });
});

describe('Blueprint Application — JWKS Management', () => {
  let blueprintApp: any;

  beforeAll(async () => {
    blueprintApp = await fixture.createBlueprintApp('AUTONOMOUS');
  });

  it('should add a key to the blueprint JWKS', async () => {
    const jwk = {
      kty: 'EC',
      crv: 'P-256',
      kid: 'test-key-1',
      x: 'f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU',
      y: 'x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0',
      use: 'sig',
    };

    const response = await fixture.addAgentKey(blueprintApp.id, jwk);
    expect(response.status).toEqual(200);
  });

  it('should list keys on the blueprint', async () => {
    const keys = await fixture.listAgentKeys(blueprintApp.id);

    expect(keys).toBeInstanceOf(Array);
    expect(keys.length).toBeGreaterThanOrEqual(1);
    expect(keys.find((k: any) => k.kid === 'test-key-1')).toBeTruthy();
  });

  it('should reject duplicate kid', async () => {
    const jwk = {
      kty: 'EC',
      crv: 'P-256',
      kid: 'test-key-1',
      x: 'f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU',
      y: 'x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0',
      use: 'sig',
    };

    const response = await fixture.addAgentKey(blueprintApp.id, jwk);
    expect(response.status).toBeGreaterThanOrEqual(400);
  });

  it('should reject key without kid', async () => {
    const jwk = {
      kty: 'EC',
      crv: 'P-256',
      x: 'f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU',
      y: 'x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0',
      use: 'sig',
    };

    const response = await fixture.addAgentKey(blueprintApp.id, jwk);
    expect(response.status).toBeGreaterThanOrEqual(400);
  });

  it('should remove a key by kid', async () => {
    const response = await fixture.removeAgentKey(blueprintApp.id, 'test-key-1');
    expect(response.status).toEqual(200);

    const keys = await fixture.listAgentKeys(blueprintApp.id);
    expect(keys.find((k: any) => k.kid === 'test-key-1')).toBeFalsy();
  });

  it('should enforce max public keys limit', async () => {
    // Create a blueprint with max 2 keys
    const response = await fixture.createRawApp({
      name: uniqueName('agent-max-keys', true),
      type: 'AGENT',
      agentSettings: {
        agentType: 'AUTONOMOUS',
        maxPublicKeysPerWorkload: 2,
      },
    });
    const limitedApp = await response.json();

    const makeKey = (kid: string) => ({
      kty: 'EC',
      crv: 'P-256',
      kid,
      x: 'f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU',
      y: 'x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0',
      use: 'sig',
    });

    // Add 2 keys — should succeed
    const r1 = await fixture.addAgentKey(limitedApp.id, makeKey('key-a'));
    expect(r1.status).toEqual(200);
    const r2 = await fixture.addAgentKey(limitedApp.id, makeKey('key-b'));
    expect(r2.status).toEqual(200);

    // 3rd key should fail
    const r3 = await fixture.addAgentKey(limitedApp.id, makeKey('key-c'));
    expect(r3.status).toBeGreaterThanOrEqual(400);
  });
});
