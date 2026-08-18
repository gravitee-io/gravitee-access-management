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
import { createDomain, safeDeleteDomain, startDomain, waitForOidcReady } from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createExtensionGrant } from '@management-commands/extension-grant-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { uniqueName } from '@utils-commands/misc';
import { getDomainManagerUrl } from '@management-commands/service/utils';
import { Application } from '@management-models/Application';
import { Domain } from '@management-models/Domain';
import { SignJWT } from 'jose';
import request from 'supertest';

import { ClaimMapping, OidcConfiguration } from './token-exchange-fixture';

const TOKEN_EXCHANGE_GRANT = 'urn:ietf:params:oauth:grant-type:token-exchange';
const JWT_BEARER_GRANT = 'urn:ietf:params:oauth:grant-type:jwt-bearer';
const ACCESS_TOKEN_TYPE = 'urn:ietf:params:oauth:token-type:access_token';

/**
 * HS256 shared secret for the JWT Bearer extension grant. The grant's GIVEN_KEY resolver uses the
 * configured key bytes directly as the MAC secret, so the test can sign an assertion per request
 * instead of replaying a pre-signed one.
 */
const ASSERTION_SECRET = 'BfC_B_jsbmj81-d0P9680nsOQWixjv5VsBjlj6BUzGg';

/** The claim the external issuer puts on the assertion, and that the exchange must propagate. */
export const ASSERTION_CLAIM = 'claim_id';

/** Where the mapper writes it on the exchanged token. */
export const MAPPED_CLAIM = 'business_claim_id';

/** Where a custom claim written in the expression language writes it on the exchanged token. */
export const EL_CLAIM = 'el_claim_id';

export interface JwtBearerSubjectFixture {
  domain: Domain;
  subjectApp: Application;
  exchangeApp: Application;
  oidc: OidcConfiguration;
  /** Signs an assertion carrying a per-request claim value. */
  mintAssertion: (claimValue: string, subject?: string) => Promise<string>;
  /** Runs the JWT Bearer grant and returns the subject token. */
  obtainSubjectToken: (claimValue: string, subject?: string) => Promise<string>;
  /** Exchanges a subject token and returns the token response body. */
  exchange: (subjectToken: string, extraParams?: string) => Promise<Record<string, any>>;
  cleanup: () => Promise<void>;
}

export interface JwtBearerSubjectFixtureConfig {
  /** Mappings set on the exchange application. Defaults to claim_id -> business_claim_id. */
  claimsMapper?: ClaimMapping[];
}

/**
 * Reproduces the reported customer topology: an external issuer mints an assertion, the JWT Bearer
 * extension grant turns it into a subject token, and token exchange issues the final token.
 */
export const setupJwtBearerSubjectFixture = async (config: JwtBearerSubjectFixtureConfig = {}): Promise<JwtBearerSubjectFixture> => {
  const { claimsMapper = [{ source: 'SUBJECT_TOKEN', sourceClaim: ASSERTION_CLAIM, tokenClaim: MAPPED_CLAIM }] } = config;

  const accessToken = await requestAdminAccessToken();
  let domain: Domain | null = null;

  try {
    domain = await createDomain(accessToken, uniqueName('tx-jwt-bearer', true), 'Token exchange over a JWT Bearer subject token');

    await request(getDomainManagerUrl(domain.id))
      .patch('')
      .set('Authorization', `Bearer ${accessToken}`)
      .set('Content-Type', 'application/json')
      .send({
        tokenExchangeSettings: {
          enabled: true,
          allowedSubjectTokenTypes: [ACCESS_TOKEN_TYPE],
          allowedRequestedTokenTypes: [ACCESS_TOKEN_TYPE, 'urn:ietf:params:oauth:token-type:id_token'],
          allowImpersonation: true,
          allowDelegation: false,
        },
      })
      .expect(200);

    const extensionGrant = await createExtensionGrant(domain.id, accessToken, {
      type: 'jwtbearer-am-extension-grant',
      grantType: JWT_BEARER_GRANT,
      name: 'external-issuer',
      configuration: JSON.stringify({
        publicKeyResolver: 'GIVEN_KEY',
        publicKey: ASSERTION_SECRET,
        claimsMapper: [{ assertion_claim: ASSERTION_CLAIM, token_claim: ASSERTION_CLAIM }],
      }),
    });

    // The extension grant only copies the assertion claim onto the user, so the subject application
    // needs a custom claim to put it on the subject token itself.
    const subjectApp = await createTestApp(uniqueName('tx-jwt-bearer-subject', true), domain, accessToken, 'service', {
      settings: {
        oauth: {
          grantTypes: [`${JWT_BEARER_GRANT}~${extensionGrant.id}`],
          scopeSettings: [{ scope: 'openid', defaultScope: true }],
          tokenCustomClaims: [
            {
              claimName: ASSERTION_CLAIM,
              claimValue: `{#context.attributes['user']['additionalInformation']['${ASSERTION_CLAIM}']}`,
              tokenType: 'ACCESS_TOKEN',
            },
          ],
        },
      },
    });

    const exchangeApp = await createTestApp(uniqueName('tx-jwt-bearer-exchange', true), domain, accessToken, 'service', {
      settings: {
        oauth: {
          grantTypes: [TOKEN_EXCHANGE_GRANT],
          scopeSettings: [{ scope: 'openid', defaultScope: true }],
          tokenExchangeOAuthSettings: { inherited: false, claimsMapper },
          // The declarative mapper and the expression language read the same subject token, so both
          // routes are covered against a subject token the JWT Bearer grant issued.
          tokenCustomClaims: [
            {
              claimName: EL_CLAIM,
              claimValue: `{#context.attributes['token_exchange']['subject']['subject_token_claims']['${ASSERTION_CLAIM}']}`,
              tokenType: 'ACCESS_TOKEN',
            },
          ],
        },
      },
    });

    const startedDomain = await startDomain(domain.id, accessToken);
    domain = startedDomain;

    const oidcResponse = await waitForOidcReady(startedDomain.hrid, { timeoutMs: 30000, intervalMs: 500 });
    expect(oidcResponse.status).toBe(200);
    const oidc = oidcResponse.body as OidcConfiguration;

    const subjectAuth = applicationBase64Token(subjectApp);
    const exchangeAuth = applicationBase64Token(exchangeApp);

    const mintAssertion = (claimValue: string, subject = 'external-user') =>
      new SignJWT({ [ASSERTION_CLAIM]: claimValue })
        .setProtectedHeader({ alg: 'HS256', typ: 'JWT' })
        .setSubject(subject)
        .setIssuedAt()
        .setExpirationTime('5m')
        .sign(new TextEncoder().encode(ASSERTION_SECRET));

    const obtainSubjectToken = async (claimValue: string, subject?: string) => {
      const assertion = await mintAssertion(claimValue, subject);
      const response = await performPost(oidc.token_endpoint, '', `grant_type=${JWT_BEARER_GRANT}&assertion=${assertion}`, {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${subjectAuth}`,
      }).expect(200);
      return response.body.access_token;
    };

    const exchange = async (subjectToken: string, extraParams = '') => {
      const response = await performPost(
        oidc.token_endpoint,
        '',
        `grant_type=${TOKEN_EXCHANGE_GRANT}&subject_token=${subjectToken}&subject_token_type=${ACCESS_TOKEN_TYPE}${extraParams}`,
        {
          'Content-type': 'application/x-www-form-urlencoded',
          Authorization: `Basic ${exchangeAuth}`,
        },
      ).expect(200);
      return response.body;
    };

    return {
      domain: startedDomain,
      subjectApp,
      exchangeApp,
      oidc,
      mintAssertion,
      obtainSubjectToken,
      exchange,
      cleanup: async () => {
        await safeDeleteDomain(startedDomain.id, accessToken);
      },
    };
  } catch (error) {
    if (domain?.id) {
      await safeDeleteDomain(domain.id, accessToken);
    }
    throw error;
  }
};
