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
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { AgentGatewayFixture, setupAgentGatewayFixture } from './fixtures/agent-app-fixture';
import { setup } from '../../../test-fixture';

setup();

let fixture: AgentGatewayFixture;

beforeAll(async () => {
  fixture = await setupAgentGatewayFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('AGENT application - client_credentials grant', () => {
  it('should issue an access token via client_credentials', async () => {
    const response = await performPost(fixture.oidc.token_endpoint, '', 'grant_type=client_credentials', {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(fixture.application),
    }).expect(200);

    expect(response.body.access_token).toBeDefined();
    expect(response.body.token_type).toEqual('bearer');
    expect(response.body.expires_in).toBeDefined();
  });

  it('should not include a refresh_token in the response', async () => {
    const response = await performPost(fixture.oidc.token_endpoint, '', 'grant_type=client_credentials&scope=scope1', {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(fixture.application),
    }).expect(200);

    expect(response.body.access_token).toBeDefined();
    expect(response.body.refresh_token).toBeUndefined();
  });
});

describe('AGENT application - forbidden grant types', () => {
  it('should reject password grant', async () => {
    const response = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=password&username=${fixture.user.username}&password=${encodeURIComponent(fixture.user.password)}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + applicationBase64Token(fixture.application),
      },
    );

    expect(response.status).toBeGreaterThanOrEqual(400);
    expect(response.body.error).toBeDefined();
  });

  it('should reject refresh_token grant', async () => {
    const response = await performPost(fixture.oidc.token_endpoint, '', 'grant_type=refresh_token&refresh_token=fake-token', {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(fixture.application),
    });

    expect(response.status).toBeGreaterThanOrEqual(400);
    expect(response.body.error).toBeDefined();
  });
});

describe('AGENT application - grant type enforcement', () => {
  it('should have forbidden grant types stripped from app settings', () => {
    const grantTypes = fixture.application.settings.oauth.grantTypes;
    expect(grantTypes).not.toContain('implicit');
    expect(grantTypes).not.toContain('password');
    expect(grantTypes).not.toContain('refresh_token');
  });

  it('should contain only allowed grant types', () => {
    const grantTypes = fixture.application.settings.oauth.grantTypes;
    expect(grantTypes).toContain('client_credentials');
    expect(grantTypes).toContain('authorization_code');
  });
});
