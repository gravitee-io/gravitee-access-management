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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createApplication } from '@management-commands/application-management-commands';
import {
  patchDomain,
  safeDeleteDomain,
  setupDomainForTest,
  waitForDomainSync,
} from '@management-commands/domain-management-commands';
import { getWellKnownOpenIdConfiguration, performGet } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';
import { getDomainApi } from '../../../api/commands/management/service/utils';
import { Domain } from '../../../api/management/models/Domain';
import { Application } from '../../../api/management/models/Application';
import { Entrypoint } from '../../../api/management/models/Entrypoint';
import { NewApplicationTypeEnum } from '../../../api/management/models/NewApplication';
import { setup } from '../../test-fixture';

setup(200000);

/**
 * When multiple entrypoints exist (tag-based routing), the non-default entrypoint
 * is the effective one for a domain — it represents the tag-matched routing path.
 * Falls back to the first entrypoint in single-entrypoint environments.
 */
function selectEffectiveEntrypoint(entrypoints: Entrypoint[]): Entrypoint {
  return entrypoints.length === 1 ? entrypoints[0] : (entrypoints.find((e) => !e.defaultEntrypoint) ?? entrypoints[0]);
}

let accessToken: string;
let domain: Domain;
let application: Application;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  const result = await setupDomainForTest(uniqueName('app-endpoints', true), { accessToken, waitForStart: true });
  domain = result.domain;

  application = await createApplication(domain.id, accessToken, {
    name: uniqueName('endpoints-app', true),
    type: NewApplicationTypeEnum.Service,
  });
});

afterAll(async () => {
  if (domain?.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});

describe('GET domain entrypoints', () => {
  let entrypoints: Entrypoint[];

  beforeAll(async () => {
    entrypoints = await getDomainApi(accessToken).getDomainEntrypoints({
      organizationId: process.env.AM_DEF_ORG_ID,
      environmentId: process.env.AM_DEF_ENV_ID,
      domain: domain.id,
    });
  });

  it('should return at least one entrypoint for the domain', () => {
    expect(entrypoints).toBeDefined();
    expect(entrypoints.length).toBeGreaterThanOrEqual(1);
  });

  it('should include a url on each entrypoint that serves as the base for all application endpoint URLs', () => {
    for (const entrypoint of entrypoints) {
      expect(entrypoint.url).toBeDefined();
      expect(entrypoint.url).toMatch(/^https?:\/\//);
    }
  });

  it('should return the sole entrypoint in a single-entrypoint environment', () => {
    // No other test creates or modifies environment-level entrypoints so the count
    // is always 1 in this environment. The non-default tag-based routing path in
    // selectEffectiveEntrypoint requires a multi-entrypoint environment to exercise directly.
    expect(entrypoints.length).toBe(1);
    expect(selectEffectiveEntrypoint(entrypoints)).toBe(entrypoints[0]);
  });

  it('should resolve to standard OAuth2/OIDC endpoint paths from the entrypoint base URL', async () => {
    const entrypoint = selectEffectiveEntrypoint(entrypoints);
    const oidcConfig = (await getWellKnownOpenIdConfiguration(domain.hrid)).body;

    expect(oidcConfig.authorization_endpoint).toContain(entrypoint.url);
    expect(oidcConfig.authorization_endpoint).toContain('/oauth/authorize');
    expect(oidcConfig.token_endpoint).toContain('/oauth/token');
    expect(oidcConfig.userinfo_endpoint).toContain('/oidc/userinfo');
    expect(oidcConfig.end_session_endpoint).toContain('/logout');
    expect(oidcConfig.introspection_endpoint).toContain('/oauth/introspect');
    expect(oidcConfig.jwks_uri).toContain('/oidc/.well-known/jwks.json');
    expect(oidcConfig.revocation_endpoint).toContain('/oauth/revoke');
  });
});

describe('Endpoint visibility based on domain protocol settings', () => {
  it('should not have SAML enabled by default', () => {
    expect(domain.saml?.enabled).toBeFalsy();
  });

  describe('when SAML is enabled on the domain', () => {
    beforeAll(async () => {
      domain = await patchDomain(domain.id, accessToken, {
        saml: { enabled: true, entityId: `${process.env.AM_GATEWAY_URL}/${domain.hrid}` },
      });
      await waitForDomainSync(domain.id);
    });

    it('should reflect SAML as enabled in domain settings', () => {
      expect(domain.saml?.enabled).toBe(true);
    });

    it('should persist the SAML entityId in domain settings', () => {
      expect(domain.saml?.entityId).toBe(`${process.env.AM_GATEWAY_URL}/${domain.hrid}`);
    });
  });

  describe('when SAML is disabled on the domain', () => {
    beforeAll(async () => {
      domain = await patchDomain(domain.id, accessToken, {
        saml: { enabled: false, entityId: `${process.env.AM_GATEWAY_URL}/${domain.hrid}` },
      });
      await waitForDomainSync(domain.id);
    });

    it('should reflect SAML as disabled in domain settings', () => {
      expect(domain.saml?.enabled).toBe(false);
    });

    it('should not expose SAML endpoints when SAML is disabled', async () => {
      const response = await performGet(process.env.AM_GATEWAY_URL, `/${domain.hrid}/saml2/idp/metadata`);
      expect(response.status).toBe(404);
    });
  });
});
