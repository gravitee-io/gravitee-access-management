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
import { BlueprintFixture, setupBlueprintFixture } from './fixtures/blueprint-fixture';

setup(60000);

let fixture: BlueprintFixture;

beforeAll(async () => {
  fixture = await setupBlueprintFixture();
});

afterAll(async () => {
  await fixture?.cleanUp();
});

describe('Blueprint Application — Creation & Profile', () => {
  it('should create USER_EMBEDDED agent typed as AGENT', async () => {
    const app = await fixture.createBlueprintApp('USER_EMBEDDED');

    expect(app.type).toEqual('agent');
    expect(app.kind).toEqual('USER_EMBEDDED');
  });

  it('should create HOSTED_DELEGATED agent typed as AGENT', async () => {
    const app = await fixture.createBlueprintApp('HOSTED_DELEGATED');

    expect(app.type).toEqual('agent');
    expect(app.kind).toEqual('HOSTED_DELEGATED');
  });

  it('should create AUTONOMOUS agent typed as AGENT', async () => {
    const app = await fixture.createBlueprintApp('AUTONOMOUS');

    expect(app.type).toEqual('agent');
    expect(app.kind).toEqual('AUTONOMOUS');
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

  it('should default tokenEndpointAuthMethod to client_secret_basic for AUTONOMOUS and HOSTED_DELEGATED', async () => {
    const autonomous = await fixture.createBlueprintApp('AUTONOMOUS');
    const hosted = await fixture.createBlueprintApp('HOSTED_DELEGATED');

    expect(autonomous.settings.oauth.tokenEndpointAuthMethod).toEqual('client_secret_basic');
    expect(hosted.settings.oauth.tokenEndpointAuthMethod).toEqual('client_secret_basic');
  });
});


