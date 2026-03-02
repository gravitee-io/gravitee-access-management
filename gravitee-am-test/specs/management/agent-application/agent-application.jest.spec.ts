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
import { getApplication, patchApplication, getApplicationAgentCard } from '@management-commands/application-management-commands';
import { Application } from '@management-models/Application';
import { uniqueName } from '@utils-commands/misc';
import { AgentApplicationFixture, setupAgentApplicationFixture } from './fixtures/agent-application-fixture';
import { setup } from '../../test-fixture';

setup();

let fixture: AgentApplicationFixture;

beforeAll(async () => {
  fixture = await setupAgentApplicationFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('AGENT application - creation defaults', () => {
  let app: Application;

  beforeAll(async () => {
    app = await fixture.createAgentApp(uniqueName('agent-defaults', true));
  });

  it('should create application with type agent', () => {
    expect(app).toBeDefined();
    expect(app.type.toLowerCase()).toEqual('agent');
  });

  it('should have authorization_code as the default grant type', () => {
    expect(app.settings.oauth.grantTypes).toEqual(['authorization_code']);
  });

  it('should have client_secret_basic as the default token endpoint auth method', () => {
    expect(app.settings.oauth.tokenEndpointAuthMethod).toEqual('client_secret_basic');
  });

  it('should generate clientId and clientSecret', () => {
    expect(app.settings.oauth.clientId).toBeDefined();
    expect(app.settings.oauth.clientId.length).toBeGreaterThan(0);
    expect(app.settings.oauth.clientSecret).toBeDefined();
    expect(app.settings.oauth.clientSecret.length).toBeGreaterThan(0);
  });

  it('should not include implicit, password, or refresh_token in default grant types', () => {
    const grantTypes = app.settings.oauth.grantTypes;
    expect(grantTypes).not.toContain('implicit');
    expect(grantTypes).not.toContain('password');
    expect(grantTypes).not.toContain('refresh_token');
  });

  it('should be retrievable by ID', async () => {
    const fetched = await getApplication(fixture.domain.id, fixture.accessToken, app.id);
    expect(fetched).toBeDefined();
    expect(fetched.id).toEqual(app.id);
    expect(fetched.type.toLowerCase()).toEqual('agent');
  });
});

describe('AGENT application - grant type restrictions', () => {
  let app: Application;

  beforeAll(async () => {
    app = await fixture.createAgentApp(uniqueName('agent-grants', true));
  });

  it('should allow adding client_credentials grant type', async () => {
    const patched = await patchApplication(
      fixture.domain.id,
      fixture.accessToken,
      { settings: { oauth: { grantTypes: ['authorization_code', 'client_credentials'] } } },
      app.id,
    );
    expect(patched.settings.oauth.grantTypes).toContain('authorization_code');
    expect(patched.settings.oauth.grantTypes).toContain('client_credentials');
    app = patched;
  });

  it('should strip implicit grant type on update', async () => {
    const patched = await patchApplication(
      fixture.domain.id,
      fixture.accessToken,
      { settings: { oauth: { grantTypes: ['authorization_code', 'client_credentials', 'implicit'] } } },
      app.id,
    );
    expect(patched.settings.oauth.grantTypes).toContain('authorization_code');
    expect(patched.settings.oauth.grantTypes).toContain('client_credentials');
    expect(patched.settings.oauth.grantTypes).not.toContain('implicit');
    app = patched;
  });

  it('should strip password grant type on update', async () => {
    const patched = await patchApplication(
      fixture.domain.id,
      fixture.accessToken,
      { settings: { oauth: { grantTypes: ['authorization_code', 'password'] } } },
      app.id,
    );
    expect(patched.settings.oauth.grantTypes).toContain('authorization_code');
    expect(patched.settings.oauth.grantTypes).not.toContain('password');
    app = patched;
  });

  it('should strip refresh_token grant type on update', async () => {
    const patched = await patchApplication(
      fixture.domain.id,
      fixture.accessToken,
      { settings: { oauth: { grantTypes: ['authorization_code', 'refresh_token'] } } },
      app.id,
    );
    expect(patched.settings.oauth.grantTypes).toContain('authorization_code');
    expect(patched.settings.oauth.grantTypes).not.toContain('refresh_token');
    app = patched;
  });

  it('should strip all forbidden grant types when sent together', async () => {
    const patched = await patchApplication(
      fixture.domain.id,
      fixture.accessToken,
      {
        settings: {
          oauth: { grantTypes: ['authorization_code', 'client_credentials', 'implicit', 'password', 'refresh_token'] },
        },
      },
      app.id,
    );
    expect(patched.settings.oauth.grantTypes).toContain('authorization_code');
    expect(patched.settings.oauth.grantTypes).toContain('client_credentials');
    expect(patched.settings.oauth.grantTypes).not.toContain('implicit');
    expect(patched.settings.oauth.grantTypes).not.toContain('password');
    expect(patched.settings.oauth.grantTypes).not.toContain('refresh_token');
  });
});

describe('AGENT application - AgentCard URL', () => {
  const validAgentCardUrl = 'https://example.com/.well-known/agent-card.json';

  it('should create application with agentCardUrl and persist it', async () => {
    const app = await fixture.createAgentApp(uniqueName('agent-agentcard-create', true), {
      agentCardUrl: validAgentCardUrl,
    });
    expect(app).toBeDefined();
    expect(app.settings?.advanced?.agentCardUrl).toEqual(validAgentCardUrl);

    const fetched = await getApplication(fixture.domain.id, fixture.accessToken, app.id);
    expect(fetched.settings?.advanced?.agentCardUrl).toEqual(validAgentCardUrl);
  });

  it('should patch application to set agentCardUrl and persist it', async () => {
    const app = await fixture.createAgentApp(uniqueName('agent-agentcard-patch', true));
    expect(app.settings?.advanced?.agentCardUrl).toBeFalsy();

    const patched = await patchApplication(
      fixture.domain.id,
      fixture.accessToken,
      { settings: { advanced: { agentCardUrl: validAgentCardUrl } } },
      app.id,
    );
    expect(patched.settings?.advanced?.agentCardUrl).toEqual(validAgentCardUrl);

    const fetched = await getApplication(fixture.domain.id, fixture.accessToken, app.id);
    expect(fetched.settings?.advanced?.agentCardUrl).toEqual(validAgentCardUrl);
  });

  it('should patch application to update agentCardUrl', async () => {
    const app = await fixture.createAgentApp(uniqueName('agent-agentcard-update', true), {
      agentCardUrl: validAgentCardUrl,
    });
    expect(app.settings?.advanced?.agentCardUrl).toEqual(validAgentCardUrl);

    const newUrl = 'https://other.example.com/.well-known/agent-card.json';
    const patched = await patchApplication(
      fixture.domain.id,
      fixture.accessToken,
      { settings: { advanced: { agentCardUrl: newUrl } } },
      app.id,
    );
    expect(patched.settings?.advanced?.agentCardUrl).toEqual(newUrl);

    const fetched = await getApplication(fixture.domain.id, fixture.accessToken, app.id);
    expect(fetched.settings?.advanced?.agentCardUrl).toEqual(newUrl);
  });

  it('should patch application to clear agentCardUrl', async () => {
    const app = await fixture.createAgentApp(uniqueName('agent-agentcard-clear', true), {
      agentCardUrl: validAgentCardUrl,
    });
    expect(app.settings?.advanced?.agentCardUrl).toEqual(validAgentCardUrl);

    const patched = await patchApplication(
      fixture.domain.id,
      fixture.accessToken,
      { settings: { advanced: { agentCardUrl: '' } } },
      app.id,
    );
    expect(patched.settings?.advanced?.agentCardUrl).toBe('');

    const fetched = await getApplication(fixture.domain.id, fixture.accessToken, app.id);
    expect(fetched.settings?.advanced?.agentCardUrl).toBe('');
  });
});

describe('AGENT application - agent card endpoint', () => {
  it('should return 400 when no agentCardUrl is configured', async () => {
    const app = await fixture.createAgentApp(uniqueName('agent-card-nourl', true));
    await expect(getApplicationAgentCard(fixture.domain.id, fixture.accessToken, app.id)).rejects.toMatchObject({ response: { status: 400 } });
  });

  it('should return 200 with valid agent card JSON', async () => {
    const agentCard = JSON.stringify({ name: 'Test Agent', version: '1.0', url: 'https://example.com' });
    const path = `/agent-card-valid-${Date.now()}`;
    await fixture.createWiremockStub(path, 200, agentCard);

    const app = await fixture.createAgentApp(uniqueName('agent-card-ok', true), {
      agentCardUrl: `${fixture.internalWiremockUrl}${path}`,
    });
    const result = await getApplicationAgentCard(fixture.domain.id, fixture.accessToken, app.id);
    expect(result).toMatchObject({ name: 'Test Agent', version: '1.0' });
  });

  it('should return 502 when agentCardUrl is unreachable', async () => {
    const app = await fixture.createAgentApp(uniqueName('agent-card-unreachable', true), {
      agentCardUrl: 'https://this-url-definitely-does-not-exist.invalid/card.json',
    });
    await expect(getApplicationAgentCard(fixture.domain.id, fixture.accessToken, app.id)).rejects.toMatchObject({ response: { status: 502 } });
  });

  it('should return 502 when remote returns 404', async () => {
    const path = `/agent-card-not-found-${Date.now()}`;
    await fixture.createWiremockStub(path, 404, '{"error":"not found"}');

    const app = await fixture.createAgentApp(uniqueName('agent-card-remote-404', true), {
      agentCardUrl: `${fixture.internalWiremockUrl}${path}`,
    });
    await expect(getApplicationAgentCard(fixture.domain.id, fixture.accessToken, app.id)).rejects.toMatchObject({ response: { status: 502 } });
  });

  it('should return 502 when remote returns 500', async () => {
    const path = `/agent-card-remote-500-${Date.now()}`;
    await fixture.createWiremockStub(path, 500, '{"error":"server error"}');

    const app = await fixture.createAgentApp(uniqueName('agent-card-remote-500', true), {
      agentCardUrl: `${fixture.internalWiremockUrl}${path}`,
    });
    await expect(getApplicationAgentCard(fixture.domain.id, fixture.accessToken, app.id)).rejects.toMatchObject({ response: { status: 502 } });
  });

  it('should return 502 when remote returns invalid JSON', async () => {
    const path = `/agent-card-bad-json-${Date.now()}`;
    await fixture.createWiremockStub(path, 200, 'this is not valid json{{{', 'text/plain');

    const app = await fixture.createAgentApp(uniqueName('agent-card-badjson', true), {
      agentCardUrl: `${fixture.internalWiremockUrl}${path}`,
    });
    await expect(getApplicationAgentCard(fixture.domain.id, fixture.accessToken, app.id)).rejects.toMatchObject({ response: { status: 502 } });
  });
});
