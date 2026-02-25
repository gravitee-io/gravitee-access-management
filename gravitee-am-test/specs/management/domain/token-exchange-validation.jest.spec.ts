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
import { createDomain, patchDomain, safeDeleteDomain, startDomain } from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../../test-fixture';
import { patchDomainRaw } from '../../gateway/token-exchange/fixtures/trusted-issuer-fixture';
import { createTrustedIssuerKeyMaterial, TrustedIssuerKeyMaterial } from '../../gateway/token-exchange/fixtures/trusted-issuer-jwt-helper';
import { TOKEN_EXCHANGE_TEST } from '../../gateway/token-exchange/fixtures/token-exchange-fixture';

setup();

let accessToken: string;
let domain: any;
let trustedKey: TrustedIssuerKeyMaterial;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  trustedKey = createTrustedIssuerKeyMaterial();

  const createdDomain = await createDomain(accessToken, uniqueName('txn-valid', true), 'Token exchange validation test domain');
  expect(createdDomain).toBeDefined();
  expect(createdDomain.id).toBeDefined();

  const startedDomain = await startDomain(createdDomain.id, accessToken);
  expect(startedDomain).toBeDefined();
  domain = startedDomain;
});

afterAll(async () => {
  if (domain?.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});

describe('Token exchange validation - reject empty options when enabled', () => {
  it('should reject token exchange enabled with empty allowedSubjectTokenTypes', async () => {
    await expect(
      patchDomain(domain.id, accessToken, {
        tokenExchangeSettings: {
          enabled: true,
          allowImpersonation: true,
          allowDelegation: false,
          allowedSubjectTokenTypes: [],
          allowedRequestedTokenTypes: ['urn:ietf:params:oauth:token-type:access_token'],
        },
      }),
    ).rejects.toThrow();
  });

  it('should reject token exchange enabled with empty allowedRequestedTokenTypes', async () => {
    await expect(
      patchDomain(domain.id, accessToken, {
        tokenExchangeSettings: {
          enabled: true,
          allowImpersonation: true,
          allowDelegation: false,
          allowedSubjectTokenTypes: ['urn:ietf:params:oauth:token-type:access_token'],
          allowedRequestedTokenTypes: [],
        },
      }),
    ).rejects.toThrow();
  });

  it('should reject delegation enabled with empty allowedActorTokenTypes', async () => {
    await expect(
      patchDomain(domain.id, accessToken, {
        tokenExchangeSettings: {
          enabled: true,
          allowImpersonation: false,
          allowDelegation: true,
          allowedSubjectTokenTypes: ['urn:ietf:params:oauth:token-type:access_token'],
          allowedRequestedTokenTypes: ['urn:ietf:params:oauth:token-type:access_token'],
          allowedActorTokenTypes: [],
        },
      }),
    ).rejects.toThrow();
  });
});

describe('Token exchange validation - accept valid configurations', () => {
  it('should accept valid token exchange config with all required fields populated', async () => {
    const patched: any = await patchDomain(domain.id, accessToken, {
      tokenExchangeSettings: {
        enabled: true,
        allowImpersonation: true,
        allowDelegation: true,
        allowedSubjectTokenTypes: [
          'urn:ietf:params:oauth:token-type:access_token',
          'urn:ietf:params:oauth:token-type:id_token',
        ],
        allowedRequestedTokenTypes: [
          'urn:ietf:params:oauth:token-type:access_token',
          'urn:ietf:params:oauth:token-type:id_token',
        ],
        allowedActorTokenTypes: [
          'urn:ietf:params:oauth:token-type:access_token',
        ],
        maxDelegationDepth: 3,
      },
    });

    expect(patched).toBeDefined();
    expect(patched.tokenExchangeSettings).toBeDefined();
    expect(patched.tokenExchangeSettings.enabled).toBe(true);
  });

  it('should accept token exchange disabled with empty lists', async () => {
    const patched: any = await patchDomain(domain.id, accessToken, {
      tokenExchangeSettings: {
        enabled: false,
        allowImpersonation: false,
        allowDelegation: false,
        allowedSubjectTokenTypes: [],
        allowedRequestedTokenTypes: [],
        allowedActorTokenTypes: [],
      },
    });

    expect(patched).toBeDefined();
    expect(patched.tokenExchangeSettings).toBeDefined();
    expect(patched.tokenExchangeSettings.enabled).toBe(false);
  });
});

describe('Token exchange validation - trusted issuer constraints', () => {
  it('should reject invalid PEM certificate', async () => {
    const response = await patchDomainRaw(domain.id, accessToken, {
      tokenExchangeSettings: {
        enabled: true,
        allowedSubjectTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
        allowedRequestedTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES,
        allowImpersonation: true,
        allowDelegation: false,
        trustedIssuers: [
          {
            issuer: 'https://bad-pem-issuer.example.com',
            keyResolutionMethod: 'PEM',
            certificate: 'not-a-valid-pem-certificate',
          },
        ],
      },
    }).expect(400);

    expect(response.body.message).toContain('Invalid PEM certificate for trusted issuer:');
  });

  it('should reject duplicate issuer URLs', async () => {
    const response = await patchDomainRaw(domain.id, accessToken, {
      tokenExchangeSettings: {
        enabled: true,
        allowedSubjectTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
        allowedRequestedTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES,
        allowImpersonation: true,
        allowDelegation: false,
        trustedIssuers: [
          { issuer: 'https://duplicate-issuer.example.com', keyResolutionMethod: 'PEM', certificate: trustedKey.certificatePem },
          { issuer: 'https://duplicate-issuer.example.com', keyResolutionMethod: 'PEM', certificate: trustedKey.certificatePem },
        ],
      },
    }).expect(400);

    expect(response.body.message).toContain('Duplicate trusted issuer URL:');
  });

  it('should reject exceeding max trusted issuer count', async () => {
    const issuers = Array.from({ length: 10 }, (_, i) => ({
      issuer: `https://issuer-${i}.example.com`,
      keyResolutionMethod: 'PEM' as const,
      certificate: trustedKey.certificatePem,
    }));

    const response = await patchDomainRaw(domain.id, accessToken, {
      tokenExchangeSettings: {
        enabled: true,
        allowedSubjectTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
        allowedRequestedTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES,
        allowImpersonation: true,
        allowDelegation: false,
        trustedIssuers: issuers,
      },
    }).expect(400);

    expect(response.body.message).toContain('Maximum number of trusted issuers exceeded');
  });

  it('should reject user binding enabled with empty criteria', async () => {
    const response = await patchDomainRaw(domain.id, accessToken, {
      tokenExchangeSettings: {
        enabled: true,
        allowedSubjectTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
        allowedRequestedTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES,
        allowImpersonation: true,
        allowDelegation: false,
        trustedIssuers: [
          {
            issuer: 'https://binding-test-issuer.example.com',
            keyResolutionMethod: 'PEM',
            certificate: trustedKey.certificatePem,
            userBindingEnabled: true,
            userBindingCriteria: [],
          },
        ],
      },
    }).expect(400);

    expect(response.body.message).toContain('User binding is enabled for trusted issuer');
  });

  it('should accept user binding enabled with valid criteria', async () => {
    const response = await patchDomainRaw(domain.id, accessToken, {
      tokenExchangeSettings: {
        enabled: true,
        allowedSubjectTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
        allowedRequestedTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES,
        allowImpersonation: true,
        allowDelegation: false,
        trustedIssuers: [
          {
            issuer: 'https://binding-valid-issuer.example.com',
            keyResolutionMethod: 'PEM',
            certificate: trustedKey.certificatePem,
            userBindingEnabled: true,
            userBindingCriteria: [{ attribute: 'emails.value', expression: "{#token['email']}" }],
          },
        ],
      },
    }).expect(200);

    expect(response.body.tokenExchangeSettings).toBeDefined();
    expect(response.body.tokenExchangeSettings.trustedIssuers).toHaveLength(1);
  });

  it('should persist trusted issuer configuration after save and reload', async () => {
    const externalIssuer = 'https://persist-test-issuer.example.com';

    const response = await patchDomainRaw(domain.id, accessToken, {
      tokenExchangeSettings: {
        enabled: true,
        allowedSubjectTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_SUBJECT_TOKEN_TYPES,
        allowedRequestedTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_REQUESTED_TOKEN_TYPES,
        allowImpersonation: true,
        allowDelegation: true,
        allowedActorTokenTypes: TOKEN_EXCHANGE_TEST.DEFAULT_ALLOWED_ACTOR_TOKEN_TYPES,
        maxDelegationDepth: 3,
        trustedIssuers: [
          {
            issuer: externalIssuer,
            keyResolutionMethod: 'PEM',
            certificate: trustedKey.certificatePem,
            scopeMappings: { 'external:read': 'openid', 'external:profile': 'profile' },
          },
        ],
      },
    }).expect(200);

    // Verify persisted values
    const body = response.body;
    expect(body.tokenExchangeSettings).toBeDefined();
    expect(body.tokenExchangeSettings.trustedIssuers).toHaveLength(1);
    expect(body.tokenExchangeSettings.trustedIssuers[0].issuer).toBe(externalIssuer);
    expect(body.tokenExchangeSettings.trustedIssuers[0].keyResolutionMethod).toBe('pem');
    expect(body.tokenExchangeSettings.trustedIssuers[0].scopeMappings).toEqual({
      'external:read': 'openid',
      'external:profile': 'profile',
    });
  });
});
