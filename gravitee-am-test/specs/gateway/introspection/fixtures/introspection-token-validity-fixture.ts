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

import { expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { performPost, requestClientCredentialsToken } from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { createTestApp } from '@utils-commands/application-commands';
import { uniqueName } from '@utils-commands/misc';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { Fixture } from '../../../test-fixture';

/**
 * BaseIntrospectionTokenService skips the database revocation check for a window after a
 * token is issued (handlers.oauth2.introspect.offlineVerificationTimerSeconds), because the
 * async token store may not have caught up yet. The local stack pins this to 0
 * (docker/local-stack/dev/docker-compose.yml), so revocation and expiry are visible on the
 * very next introspection call with no wait required. The window's own behaviour is
 * unit-tested in IntrospectionAccessTokenServiceTest and is deliberately not re-asserted here.
 */

/** Access token lifetime for the short-lived issuer, in seconds. */
export const SHORT_TOKEN_VALIDITY_SECONDS = 5;

export const INTROSPECTION_VALIDITY_TEST = {
  DOMAIN_PREFIX: 'introspect-validity',
  ISSUER_APP: 'introspect-validity-issuer',
  SHORT_LIVED_APP: 'introspect-validity-shortlived',
  RESOURCE_SERVER_APP: 'introspect-validity-rs',
} as const;

export interface IntrospectionTokenValidityFixture extends Fixture {
  domain: Domain;
  oidc: any;
  /** Mints tokens with the platform default lifetime. */
  issuer: Application;
  /** Mints tokens that expire after SHORT_TOKEN_VALIDITY_SECONDS. */
  shortLivedIssuer: Application;
  /** Confidential client that performs the introspection calls. */
  resourceServer: Application;

  issueToken: () => Promise<string>;
  issueShortLivedToken: () => Promise<string>;
  /** Introspects as the resource server; resolves to the parsed response body. */
  introspect: (token: string) => Promise<any>;
  /** Revokes a token using the credentials of the client that issued it. */
  revokeToken: (token: string, issuingApp: Application) => Promise<void>;
}

function oauthSettings(accessTokenValiditySeconds?: number) {
  const oauth: Record<string, unknown> = { grantTypes: ['client_credentials'] };
  if (accessTokenValiditySeconds !== undefined) {
    oauth.accessTokenValiditySeconds = accessTokenValiditySeconds;
  }
  return { settings: { oauth } };
}

export const setupIntrospectionTokenValidityFixture = async (): Promise<IntrospectionTokenValidityFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();

    const setupResult = await setupDomainForTest(uniqueName(INTROSPECTION_VALIDITY_TEST.DOMAIN_PREFIX, true), {
      accessToken,
      waitForStart: true,
    });
    domain = setupResult.domain;
    const oidc = setupResult.oidcConfig;
    expect(oidc.introspection_endpoint).toEqual(expect.any(String));
    expect(oidc.revocation_endpoint).toEqual(expect.any(String));

    const issuer = await createTestApp(
      uniqueName(INTROSPECTION_VALIDITY_TEST.ISSUER_APP, true),
      domain,
      accessToken,
      'service',
      oauthSettings(),
    );

    const shortLivedIssuer = await createTestApp(
      uniqueName(INTROSPECTION_VALIDITY_TEST.SHORT_LIVED_APP, true),
      domain,
      accessToken,
      'service',
      oauthSettings(SHORT_TOKEN_VALIDITY_SECONDS),
    );
    expect(shortLivedIssuer.settings.oauth.accessTokenValiditySeconds).toBe(SHORT_TOKEN_VALIDITY_SECONDS);

    const resourceServer = await createTestApp(
      uniqueName(INTROSPECTION_VALIDITY_TEST.RESOURCE_SERVER_APP, true),
      domain,
      accessToken,
      'service',
      oauthSettings(),
    );

    const mint = (app: Application) => requestClientCredentialsToken(app.settings.oauth.clientId, app.settings.oauth.clientSecret, oidc);

    const introspect = async (token: string): Promise<any> => {
      const response = await performPost(oidc.introspection_endpoint, '', `token=${encodeURIComponent(token)}`, {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(resourceServer.settings.oauth.clientId, resourceServer.settings.oauth.clientSecret),
      }).expect(200);
      return response.body;
    };

    const revokeToken = async (token: string, issuingApp: Application): Promise<void> => {
      await performPost(oidc.revocation_endpoint, '', `token=${encodeURIComponent(token)}&token_type_hint=access_token`, {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(issuingApp.settings.oauth.clientId, issuingApp.settings.oauth.clientSecret),
      }).expect(200);
    };

    const cleanUp = async () => {
      await safeDeleteDomain(domain?.id, accessToken);
    };

    return {
      accessToken,
      domain,
      oidc,
      issuer,
      shortLivedIssuer,
      resourceServer,
      issueToken: () => mint(issuer),
      issueShortLivedToken: () => mint(shortLivedIssuer),
      introspect,
      revokeToken,
      cleanUp,
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (cleanupError) {
        console.error('Failed to cleanup domain after setup failure:', cleanupError);
      }
    }
    throw error;
  }
};

/**
 * Rewrites a JWT's payload without re-signing it, so the signature no longer matches.
 * Models a caller presenting a token they have edited in transit.
 */
export function tamperWithPayload(token: string): string {
  const [header, payload, signature] = token.split('.');
  const decoded = JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
  decoded.sub = 'tampered-subject';
  const rewritten = Buffer.from(JSON.stringify(decoded), 'utf8').toString('base64url');
  return [header, rewritten, signature].join('.');
}
