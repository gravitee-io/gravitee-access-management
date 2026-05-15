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

const CIBA_GRANT_TYPE = 'urn:openid:params:grant-type:ciba';
const ORG_ID = process.env.AM_DEF_ORG_ID;
const ENV_ID = process.env.AM_DEF_ENV_ID;
const MANAGEMENT_BASE = `${process.env.AM_MANAGEMENT_URL}/management/organizations/${ORG_ID}/environments/${ENV_ID}`;

let fixture: BlueprintFixture;

beforeAll(async () => {
  fixture = await setupBlueprintFixture();
});

afterAll(async () => {
  await fixture?.cleanUp();
});

async function patchAppRaw(domainId: string, appId: string, body: any, accessToken: string): Promise<Response> {
  return fetch(`${MANAGEMENT_BASE}/domains/${domainId}/applications/${appId}`, {
    method: 'PATCH',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  });
}

/**
 * The AM-6854 walkthrough doc anticipated that AM would either reject CIBA on a public/AUTONOMOUS agent
 * at the management API or filter the grant from the persisted app. Neither happens today — the grant
 * persists. These tests capture that current behavior. When AM-6854 lands the assertions will start
 * failing and the spec needs to flip back to expecting rejection.
 */
describe('AM-6854 — current behavior: CIBA grant is accepted on public agent blueprints', () => {
  it('USER_EMBEDDED (token_endpoint_auth_method=none) currently persists the CIBA grant', async () => {
    const app = await fixture.createBlueprintApp('USER_EMBEDDED');
    expect(app.settings.oauth.tokenEndpointAuthMethod).toEqual('none');

    const grantTypes = Array.from(new Set([...(app.settings.oauth.grantTypes ?? []), CIBA_GRANT_TYPE]));
    const res = await patchAppRaw(fixture.domain.id, app.id, { settings: { oauth: { grantTypes } } }, fixture.accessToken);

    expect(res.ok).toBe(true);
    const body = await res.json();
    expect(body.settings.oauth.grantTypes).toContain(CIBA_GRANT_TYPE);
    expect(body.settings.oauth.tokenEndpointAuthMethod).toEqual('none');
    console.warn(
      'AM-6854 gap: CIBA grant accepted on public USER_EMBEDDED agent. ' +
        'When AM-6854 enforces the public-client ban, flip this assertion back to expect rejection.',
    );
  });
});

describe('AUTONOMOUS agents — current behavior: CIBA grant is accepted', () => {
  it('AUTONOMOUS currently persists the CIBA grant alongside its default grants', async () => {
    const app = await fixture.createBlueprintApp('AUTONOMOUS');

    const grantTypes = Array.from(new Set([...(app.settings.oauth.grantTypes ?? []), CIBA_GRANT_TYPE]));
    const res = await patchAppRaw(fixture.domain.id, app.id, { settings: { oauth: { grantTypes } } }, fixture.accessToken);

    expect(res.ok).toBe(true);
    const body = await res.json();
    expect(body.settings.oauth.grantTypes).toContain(CIBA_GRANT_TYPE);
    console.warn(
      'AM-6854 gap: CIBA grant accepted on AUTONOMOUS agent (Type C is meant to be client_credentials-only). ' +
        'When the blueprint guard ships, flip this assertion back to expect rejection.',
    );
  });
});
