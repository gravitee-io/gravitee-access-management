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
import {
  deriveNameFromIssuer,
  fromTrustedDomain,
  isAbsoluteUri,
  isValidSpiffeTrustDomain,
  keyMaterialErrors,
  keyMaterialSourceLabel,
  trustDomainUsageLabel,
  trustDomainUsages,
  trustDomainUsagesLabel,
  toTrustedDomainRequest,
} from './trust-domain.types';

describe('trust domain types', () => {
  it('shouldLabelEveryUsage', () => {
    expect(trustDomainUsageLabel('SPIFFE')).toBe('SPIFFE');
    expect(trustDomainUsageLabel('ISSUER')).toBe('OIDC - Trusted Issuer');
    expect(trustDomainUsageLabel('CROSS_APP_ACCESS')).toBe('Cross App Access');
  });

  it('shouldReadUsagesOffTheMatchers', () => {
    expect(trustDomainUsages({ name: 'a', spiffeTrustDomain: 'am.local' })).toEqual(['SPIFFE']);
    expect(trustDomainUsages({ name: 'a', issuer: 'https://issuer.example', tokenExchangeEnabled: true })).toEqual(['ISSUER']);
    expect(
      trustDomainUsages({ name: 'a', spiffeTrustDomain: 'am.local', issuer: 'https://issuer.example', tokenExchangeEnabled: true }),
    ).toEqual(['SPIFFE', 'ISSUER']);
    expect(trustDomainUsages({ name: 'a' })).toEqual([]);
  });

  it('shouldNotReadTheIssuerAloneAsATokenExchangeUsage', () => {
    expect(trustDomainUsages({ name: 'a', issuer: 'https://auth.acme.com', crossAppAccess: { enabled: true } })).toEqual([
      'CROSS_APP_ACCESS',
    ]);
  });

  it('shouldReadCrossAppAccessOffTheEnabledFlag', () => {
    expect(trustDomainUsages({ name: 'a', crossAppAccess: { enabled: true } })).toEqual(['CROSS_APP_ACCESS']);
    expect(trustDomainUsages({ name: 'a', crossAppAccess: { enabled: false } })).toEqual([]);
    expect(trustDomainUsages({ name: 'a', crossAppAccess: {} })).toEqual([]);
  });

  it('shouldDistinguishACrossAppAccessOnlyTrustedDomainFromATokenExchangeOne', () => {
    expect(trustDomainUsagesLabel({ name: 'a', crossAppAccess: { enabled: true } })).toBe('Cross App Access');
    expect(trustDomainUsagesLabel({ name: 'a', issuer: 'https://sso.acme.com', tokenExchangeEnabled: true })).toBe('OIDC - Trusted Issuer');
    expect(
      trustDomainUsagesLabel({
        name: 'a',
        issuer: 'https://sso.acme.com',
        tokenExchangeEnabled: true,
        crossAppAccess: { enabled: true },
      }),
    ).toBe('OIDC - Trusted Issuer, Cross App Access');
  });

  it('shouldLabelATrustedDomainServingBothUsages', () => {
    expect(
      trustDomainUsagesLabel({
        name: 'acme-corp',
        spiffeTrustDomain: 'acme.org',
        issuer: 'https://sso.acme.com',
        tokenExchangeEnabled: true,
      }),
    ).toBe('SPIFFE, OIDC - Trusted Issuer');
  });

  it('shouldHumanizeValuesTheOptionListsDoNotKnow', () => {
    expect(keyMaterialSourceLabel('jwks_url')).toBe('JWKS URL');
    expect(keyMaterialSourceLabel('STATIC_JWK_SET')).toBe('Static JWK Set');
    expect(keyMaterialSourceLabel('X509_CERTIFICATE')).toBe('X509 Certificate');
  });

  it('shouldLabelNothingWhenThereIsNoValue', () => {
    expect(keyMaterialSourceLabel(undefined)).toBe('');
  });

  it('shouldLabelEveryKeySource', () => {
    expect(keyMaterialSourceLabel('JWKS_URL')).toBe('JWKS URL');
    expect(keyMaterialSourceLabel('JWK_SET')).toBe('JWK Set');
    expect(keyMaterialSourceLabel('PEM')).toBe('PEM Certificate');
  });

  it('shouldDeriveDnsStyleNameFromIssuerUrl', () => {
    expect(deriveNameFromIssuer('https://issuer.example.com')).toBe('https-issuer.example.com');
    expect(deriveNameFromIssuer('https://Issuer.Example.com/auth/realms/x')).toBe('https-issuer.example.com-auth-realms-x');
  });

  it('shouldDeriveNameAcceptedByTheServerNamePattern', () => {
    expect(isValidSpiffeTrustDomain(deriveNameFromIssuer('https://issuer.example.com/'))).toBe(true);
  });

  it('shouldRejectSpiffeTrustDomainsThatAreNotDnsStyleLabels', () => {
    expect(isValidSpiffeTrustDomain('Prod.Example')).toBe(false);
    expect(isValidSpiffeTrustDomain('-prod.example')).toBe(false);
    expect(isValidSpiffeTrustDomain('prod.example')).toBe(true);
  });

  describe('isAbsoluteUri', () => {
    it('shouldAcceptAnAbsoluteUri', () => {
      expect(isAbsoluteUri('https://calendar.acme.com')).toBe(true);
      expect(isAbsoluteUri('urn:acme:calendar')).toBe(true);
    });

    it('shouldRejectAnythingWithoutAScheme', () => {
      expect(isAbsoluteUri('calendar.acme.com')).toBe(false);
      expect(isAbsoluteUri('/calendar')).toBe(false);
      expect(isAbsoluteUri('')).toBe(false);
      expect(isAbsoluteUri(undefined)).toBe(false);
    });
  });

  describe('fromTrustedDomain', () => {
    it('shouldFlattenTheNestedBlocksOntoTheFormModel', () => {
      const response = {
        id: 'td-1',
        name: 'acme-corp',
        domainIdentifier: 'https://sso.acme.com',
        keyMaterial: { source: 'jwks_url', jwksUrl: 'https://sso.acme.com/keys', refreshIntervalSeconds: 600 },
        spiffe: { spiffeTrustDomain: 'acme.org', allowedAlgorithms: ['RS256'] },
        tokenExchange: {
          enabled: true,
          scopeMappings: { 'external:read': 'openid' },
          userBindingEnabled: true,
          userBindingCriteria: [{ attribute: 'email', expression: 'email' }],
        },
      };

      expect(fromTrustedDomain(response)).toEqual({
        id: 'td-1',
        name: 'acme-corp',
        description: undefined,
        spiffeTrustDomain: 'acme.org',
        issuer: 'https://sso.acme.com',
        tokenExchangeEnabled: true,
        keyMaterial: { source: 'JWKS_URL', jwksUrl: 'https://sso.acme.com/keys', refreshIntervalSeconds: 600 },
        refreshIntervalSeconds: 600,
        allowedAlgorithms: ['RS256'],
        scopeMappings: { 'external:read': 'openid' },
        userBindingEnabled: true,
        userBindingCriteria: [{ attribute: 'email', expression: 'email' }],
        crossAppAccess: undefined,
      });
    });

    it('shouldReadTheCrossAppAccessBlockAsItIs', () => {
      const flat = fromTrustedDomain({
        id: 'td-1',
        name: 'acme-corp',
        crossAppAccess: { enabled: true, resourceServers: [] },
      }) as any;

      expect(flat.crossAppAccess).toEqual({ enabled: true, resourceServers: [] });
    });

    it('shouldDefaultTheAbsentBlocks', () => {
      const flat = fromTrustedDomain({ id: 'td-1', name: 'spire-prod' }) as any;

      expect(flat.spiffeTrustDomain).toBeUndefined();
      expect(flat.issuer).toBeUndefined();
      expect(flat.tokenExchangeEnabled).toBe(false);
      expect(flat.allowedAlgorithms).toEqual([]);
      expect(flat.userBindingEnabled).toBe(false);
      expect(flat.refreshIntervalSeconds).toBe(300);
    });

    it('shouldPassThroughNothing', () => {
      expect(fromTrustedDomain(undefined)).toBeUndefined();
    });
  });

  describe('keyMaterialErrors', () => {
    it('shouldRequireASource', () => {
      expect(keyMaterialErrors(undefined)).toHaveLength(1);
    });

    it('shouldRequireJwksUrlWhenSourceIsJwksUrl', () => {
      expect(keyMaterialErrors({ source: 'JWKS_URL' })).toHaveLength(1);
      expect(keyMaterialErrors({ source: 'JWKS_URL', jwksUrl: 'https://issuer.example/keys' })).toHaveLength(0);
    });

    it('shouldRequireAtLeastOneKeyWhenSourceIsJwkSet', () => {
      expect(keyMaterialErrors({ source: 'JWK_SET', jwkSet: { keys: [] } })).toHaveLength(1);
      expect(keyMaterialErrors({ source: 'JWK_SET', jwkSet: { keys: [{ kty: 'RSA' }] } })).toHaveLength(0);
    });

    it('shouldRequireCertificateWhenSourceIsPem', () => {
      expect(keyMaterialErrors({ source: 'PEM' })).toHaveLength(1);
      expect(keyMaterialErrors({ source: 'PEM', certificate: '-----BEGIN CERTIFICATE-----' })).toHaveLength(0);
    });
  });

  describe('toTrustedDomainRequest', () => {
    it('shouldFoldTheRefreshIntervalOntoTheKeyMaterial', () => {
      const request = toTrustedDomainRequest({
        name: 'acme',
        issuer: 'https://sso.acme.com',
        keyMaterial: { source: 'JWKS_URL', jwksUrl: 'https://sso.acme.com/keys' },
        refreshIntervalSeconds: 600,
      });

      expect(request.keyMaterial).toEqual({
        source: 'JWKS_URL',
        jwksUrl: 'https://sso.acme.com/keys',
        refreshIntervalSeconds: 600,
      });
    });

    it('shouldCarryScopeMappingsAndUserBindingUnderTokenExchange', () => {
      const request = toTrustedDomainRequest({
        name: 'acme',
        issuer: 'https://sso.acme.com',
        tokenExchangeEnabled: true,
        scopeMappings: { 'external:read': 'openid' },
        userBindingEnabled: true,
        userBindingCriteria: [{ attribute: 'email', expression: 'email' }],
      });

      expect(request.tokenExchange).toEqual({
        enabled: true,
        scopeMappings: { 'external:read': 'openid' },
        userBindingEnabled: true,
        userBindingCriteria: [{ attribute: 'email', expression: 'email' }],
      });
    });

    it('shouldSendTheIssuerWithoutTokenExchangeWhenAmOnlyIssuesTowardsIt', () => {
      const request = toTrustedDomainRequest({
        name: 'acme-suite',
        issuer: 'https://auth.acme.com',
        crossAppAccess: { enabled: true },
      });

      expect(request.domainIdentifier).toBe('https://auth.acme.com');
      expect(request.tokenExchange.enabled).toBe(false);
    });

    it('shouldPutTheSpiffeMatcherAndAlgorithmsUnderSpiffe', () => {
      const request = toTrustedDomainRequest({ name: 'spire-prod', spiffeTrustDomain: 'spire.example', allowedAlgorithms: ['RS256'] });

      expect(request.spiffe).toEqual({ spiffeTrustDomain: 'spire.example', allowedAlgorithms: ['RS256'] });
    });

    it('shouldDeclareBothBlocksWhenOneAuthorityServesBothUsages', () => {
      const request = toTrustedDomainRequest({ name: 'acme-corp', spiffeTrustDomain: 'acme.org', issuer: 'https://sso.acme.com' });

      expect(request.spiffe.spiffeTrustDomain).toBe('acme.org');
      expect(request.domainIdentifier).toBe('https://sso.acme.com');
    });

    it('shouldBlankTheIssuerWhenTheUsageIsNotDeclared', () => {
      const request = toTrustedDomainRequest({ name: 'acme', spiffeTrustDomain: 'acme.org' });

      expect(request.domainIdentifier).toBe('');
      expect(request.tokenExchange).toEqual({ enabled: false, scopeMappings: {}, userBindingEnabled: false, userBindingCriteria: [] });
      expect(request.spiffe.spiffeTrustDomain).toBe('acme.org');
    });

    it('shouldBlankTheSpiffeMatcherWhenTheUsageIsNotDeclared', () => {
      const request = toTrustedDomainRequest({ name: 'acme', issuer: 'https://sso.acme.com' });

      expect(request.spiffe.spiffeTrustDomain).toBe('');
      expect(request.domainIdentifier).toBe('https://sso.acme.com');
    });

    it('shouldCarryTheCrossAppAccessBlockAsItIs', () => {
      const crossAppAccess = {
        enabled: true,
        resourceServers: [{ name: 'Calendar', resource: 'https://calendar.acme.com' }],
      };
      const request = toTrustedDomainRequest({ name: 'acme', issuer: 'https://sso.acme.com', crossAppAccess });

      expect(request.crossAppAccess).toEqual(crossAppAccess);
    });

    it('shouldDisableCrossAppAccessWhenTheFormDeclaresNone', () => {
      const request = toTrustedDomainRequest({ name: 'acme', issuer: 'https://sso.acme.com' });

      expect(request.crossAppAccess).toEqual({ enabled: false });
    });
  });
});
