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
  normalizeTrustDomain,
  isValidTrustDomainName,
  keyMaterialErrors,
  keyMaterialSourceLabel,
  trustDomainKindLabel,
} from './trust-domain.types';

describe('trust domain types', () => {
  it('shouldLabelBothKinds', () => {
    expect(trustDomainKindLabel('SPIFFE')).toBe('SPIFFE');
    expect(trustDomainKindLabel('TOKEN_EXCHANGE')).toBe('Token Exchange');
  });

  it('shouldLabelKnownValuesWhateverTheirCase', () => {
    expect(trustDomainKindLabel('token_exchange')).toBe('Token Exchange');
    expect(keyMaterialSourceLabel('jwks_url')).toBe('JWKS URL');
  });

  it('shouldHumanizeValuesTheOptionListsDoNotKnow', () => {
    expect(trustDomainKindLabel('MUTUAL_TLS')).toBe('Mutual Tls');
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
    expect(isValidTrustDomainName(deriveNameFromIssuer('https://issuer.example.com/'))).toBe(true);
  });

  it('shouldRejectNamesThatAreNotDnsStyleLabels', () => {
    expect(isValidTrustDomainName('Prod.Example')).toBe(false);
    expect(isValidTrustDomainName('-prod.example')).toBe(false);
    expect(isValidTrustDomainName('prod.example')).toBe(true);
  });

  describe('normalizeTrustDomain', () => {
    it('shouldUppercaseTheEnumsTheApiLowercases', () => {
      const fromApi = {
        id: 'td-1',
        kind: 'token_exchange',
        name: 'issuer.example',
        keyMaterial: { source: 'pem', certificate: '-----BEGIN CERTIFICATE-----' },
        tokenExchange: { issuer: 'https://issuer.example' },
      } as any;

      expect(normalizeTrustDomain(fromApi)).toEqual({
        id: 'td-1',
        kind: 'TOKEN_EXCHANGE',
        name: 'issuer.example',
        keyMaterial: { source: 'PEM', certificate: '-----BEGIN CERTIFICATE-----' },
        tokenExchange: { issuer: 'https://issuer.example' },
      });
    });

    it('shouldLeaveCanonicalValuesAlone', () => {
      const canonical = { kind: 'SPIFFE', name: 'am.local', keyMaterial: { source: 'JWKS_URL', jwksUrl: 'https://x/keys' } } as any;
      expect(normalizeTrustDomain(canonical)).toEqual(canonical);
    });

    it('shouldTolerateMissingKindAndKeyMaterial', () => {
      expect(normalizeTrustDomain({ name: 'am.local' } as any)).toEqual({ name: 'am.local', kind: undefined, keyMaterial: undefined });
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
