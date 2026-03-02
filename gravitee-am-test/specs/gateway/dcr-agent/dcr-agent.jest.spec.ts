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
import { getApplication } from '@management-commands/application-management-commands';
import { setup } from '../../test-fixture';
import { DcrAgentFixture, DCR_AGENT_TEST, setupDcrAgentFixture } from './fixtures/dcr-agent-fixture';

setup();

let fixture: DcrAgentFixture;

beforeAll(async () => {
  fixture = await setupDcrAgentFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('DCR Agent - Default Behavior', () => {
  let agentApp: any;

  it('should create an agent application with default settings', async () => {
    const response = await fixture.registerAgent({
      redirect_uris: [DCR_AGENT_TEST.REDIRECT_URI],
      client_name: 'My Agent App',
      application_type: 'agent',
    });

    expect(response.status).toBe(201);
    agentApp = response.body;
    expect(agentApp.application_type).toBe('agent');
    expect(agentApp.client_id).toBeDefined();
    expect(agentApp.client_secret).toBeDefined();
    expect(agentApp.grant_types).toContain('authorization_code');
    expect(agentApp.response_types).toContain('code');
    expect(agentApp.token_endpoint_auth_method).toBe('client_secret_basic');
  });

  it('should have AGENT type in Management API', async () => {
    expect(agentApp).toBeDefined();
    const app = await getApplication(fixture.domain.id, fixture.accessToken, agentApp.id);
    expect(app.type).toBe('agent');
  });
});

describe('DCR Agent - Grant Type Constraints', () => {
  it('should strip forbidden grant types (implicit, password, refresh_token)', async () => {
    const response = await fixture.registerAgent({
      redirect_uris: [DCR_AGENT_TEST.REDIRECT_URI],
      client_name: 'Agent Forbidden Grants',
      application_type: 'agent',
      grant_types: ['authorization_code', 'client_credentials', 'implicit', 'password', 'refresh_token'],
      response_types: ['code'],
    });

    expect(response.status).toBe(201);
    expect(response.body.grant_types).toContain('authorization_code');
    expect(response.body.grant_types).toContain('client_credentials');
    expect(response.body.grant_types).not.toContain('implicit');
    expect(response.body.grant_types).not.toContain('password');
    expect(response.body.grant_types).not.toContain('refresh_token');
  });

  it('should default to authorization_code when all grant types are forbidden', async () => {
    const response = await fixture.registerAgent({
      redirect_uris: [DCR_AGENT_TEST.REDIRECT_URI],
      client_name: 'Agent All Forbidden',
      application_type: 'agent',
      grant_types: ['implicit', 'password'],
      response_types: ['token'],
    });

    expect(response.status).toBe(201);
    expect(response.body.grant_types).toEqual(['authorization_code']);
    expect(response.body.response_types).toContain('code');
    expect(response.body.response_types).not.toContain('token');
  });
});

describe('DCR Agent - Response Type Constraints', () => {
  it('should strip implicit response types (token, id_token, id_token token)', async () => {
    const response = await fixture.registerAgent({
      redirect_uris: [DCR_AGENT_TEST.REDIRECT_URI],
      client_name: 'Agent Implicit Response Types',
      application_type: 'agent',
      grant_types: ['authorization_code'],
      response_types: ['code', 'token', 'id_token', 'id_token token'],
    });

    expect(response.status).toBe(201);
    expect(response.body.response_types).toContain('code');
    expect(response.body.response_types).not.toContain('token');
    expect(response.body.response_types).not.toContain('id_token');
    expect(response.body.response_types).not.toContain('id_token token');
  });
});

describe('DCR Agent - Update Constraints (PATCH)', () => {
  it('should strip forbidden grant types and response types on PATCH even when application_type is omitted', async () => {
    const regResponse = await fixture.registerAgent({
      redirect_uris: [DCR_AGENT_TEST.REDIRECT_URI],
      client_name: 'Agent For Patch Test',
      application_type: 'agent',
      grant_types: ['authorization_code', 'client_credentials'],
      response_types: ['code'],
    });
    expect(regResponse.status).toBe(201);

    const response = await fixture.patchAgent(regResponse.body.client_id, regResponse.body.registration_access_token, {
      grant_types: ['authorization_code', 'client_credentials', 'implicit', 'password', 'refresh_token'],
      response_types: ['code', 'token', 'id_token', 'id_token token'],
    });

    expect(response.status).toBe(200);
    expect(response.body.grant_types).toContain('authorization_code');
    expect(response.body.grant_types).toContain('client_credentials');
    expect(response.body.grant_types).not.toContain('implicit');
    expect(response.body.grant_types).not.toContain('password');
    expect(response.body.grant_types).not.toContain('refresh_token');
    expect(response.body.response_types).toContain('code');
    expect(response.body.response_types).not.toContain('token');
    expect(response.body.response_types).not.toContain('id_token');
    expect(response.body.response_types).not.toContain('id_token token');
  });
});

describe('DCR Agent - Update Constraints (PUT)', () => {
  it('should strip forbidden grant types and response types on PUT even when application_type is omitted', async () => {
    const regResponse = await fixture.registerAgent({
      redirect_uris: [DCR_AGENT_TEST.REDIRECT_URI],
      client_name: 'Agent For Put Test',
      application_type: 'agent',
      grant_types: ['authorization_code', 'client_credentials'],
      response_types: ['code'],
    });
    expect(regResponse.status).toBe(201);

    const response = await fixture.putAgent(regResponse.body.client_id, regResponse.body.registration_access_token, {
      redirect_uris: [DCR_AGENT_TEST.REDIRECT_URI],
      grant_types: ['authorization_code', 'client_credentials', 'implicit', 'password', 'refresh_token'],
      response_types: ['code', 'token', 'id_token', 'id_token token'],
    });

    expect(response.status).toBe(200);
    expect(response.body.grant_types).toContain('authorization_code');
    expect(response.body.grant_types).toContain('client_credentials');
    expect(response.body.grant_types).not.toContain('implicit');
    expect(response.body.grant_types).not.toContain('password');
    expect(response.body.grant_types).not.toContain('refresh_token');
    expect(response.body.response_types).toContain('code');
    expect(response.body.response_types).not.toContain('token');
    expect(response.body.response_types).not.toContain('id_token');
    expect(response.body.response_types).not.toContain('id_token token');
  });
});

describe('DCR Agent - Token Flow', () => {
  it('should obtain a token via client_credentials for a DCR-registered agent', async () => {
    const regResponse = await fixture.registerAgent({
      redirect_uris: [DCR_AGENT_TEST.REDIRECT_URI],
      client_name: 'Agent CC Flow',
      application_type: 'agent',
      grant_types: ['client_credentials', 'authorization_code'],
      response_types: ['code'],
    });

    expect(regResponse.status).toBe(201);
    const { client_id, client_secret } = regResponse.body;

    const tokenResponse = await fixture.getTokenWithClientCredentials(client_id, client_secret);
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.body.access_token).toBeDefined();
  });
});
