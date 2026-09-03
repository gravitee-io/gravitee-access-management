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
  isAbsoluteUri,
  normalizeTrustDomain,
  isValidSpiffeTrustDomain,
  keyMaterialErrors,
  keyMaterialSourceLabel,
  trustDomainUsageLabel,
  trustDomainUsages,
  trustDomainUsagesLabel,
} from './trust-domain.types';

describe('trust domain types', () => {
  it('shouldLabelEveryUsage', () => {
    expect(trustDomainUsageLabel('SPIFFE')).toBe('SPIFFE');
    expect(trustDomainUsageLabel('ISSUER')).toBe('OIDC - Trusted Issuer');
    expect(trustDomainUsageLabel('CROSS_APP_ACCESS')).toBe('Cross App Access');
  });

  it('shouldReadUsagesOffTheMatchers', () => {
    expect(trustDomainUsages({ name: 'a', spiffeTrustDomain: 'am.local' })).toEqual(['SPIFFE']);
    expect(trustDomainUsages({ name: 'a', issuer: 'https://issuer.example' })).toEqual(['ISSUER']);
    expect(trustDomainUsages({ name: 'a', spiffeTrustDomain: 'am.local', issuer: 'https://issuer.example' })).toEqual(['SPIFFE', 'ISSUER']);
    expect(trustDomainUsages({ name: 'a' })).toEqual([]);
  });

  it('shouldReadCrossAppAccessOffTheEnabledFlag', () => {
    expect(trustDomainUsages({ name: 'a', crossAppAccess: { enabled: true } })).toEqual(['CROSS_APP_ACCESS']);
    expect(trustDomainUsages({ name: 'a', crossAppAccess: { enabled: false } })).toEqual([]);
    expect(trustDomainUsages({ name: 'a', crossAppAccess: {} })).toEqual([]);
  });

  it('shouldDistinguishACrossAppAccessOnlyTrustedDomainFromATokenExchangeOne', () => {
    expect(trustDomainUsagesLabel({ name: 'a', crossAppAccess: { enabled: true } })).toBe('Cross App Access');
    expect(trustDomainUsagesLabel({ name: 'a', issuer: 'https://sso.acme.com' })).toBe('OIDC - Trusted Issuer');
    expect(trustDomainUsagesLabel({ name: 'a', issuer: 'https://sso.acme.com', crossAppAccess: { enabled: true } })).toBe(
      'OIDC - Trusted Issuer, Cross App Access',
    );
  });

  it('shouldLabelATrustedDomainServingBothUsages', () => {
    expect(trustDomainUsagesLabel({ name: 'acme-corp', spiffeTrustDomain: 'acme.org', issuer: 'https://sso.acme.com' })).toBe(
      'SPIFFE, OIDC - Trusted Issuer',
    );
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

  describe('normalizeTrustDomain', () => {
    it('shouldUppercaseTheEnumsTheApiLowercases', () => {
      const fromApi = {
        id: 'td-1',
        name: 'issuer.example',
        issuer: 'https://issuer.example',
        keyMaterial: { source: 'pem', certificate: '-----BEGIN CERTIFICATE-----' },
      } as any;

      expect(normalizeTrustDomain(fromApi)).toEqual({
        id: 'td-1',
        name: 'issuer.example',
        issuer: 'https://issuer.example',
        keyMaterial: { source: 'PEM', certificate: '-----BEGIN CERTIFICATE-----' },
      });
    });

    it('shouldLeaveCanonicalValuesAlone', () => {
      const canonical = {
        name: 'am.local',
        spiffeTrustDomain: 'am.local',
        keyMaterial: { source: 'JWKS_URL', jwksUrl: 'https://x/keys' },
      } as any;
      expect(normalizeTrustDomain(canonical)).toEqual(canonical);
    });

    it('shouldTolerateMissingKeyMaterial', () => {
      expect(normalizeTrustDomain({ name: 'am.local' } as any)).toEqual({ name: 'am.local', keyMaterial: undefined });
      expect(normalizeTrustDomain(undefined)).toBeUndefined();
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
});
