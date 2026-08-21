/**
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
package io.gravitee.am.service.impl;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.KeyMaterialSource;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.oidc.SpiffeBundleSource;
import io.gravitee.am.model.KeyRetrievalSettings;
import io.gravitee.am.model.oidc.SpiffeDomainSettings;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.gravitee.am.repository.management.api.TrustDomainRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.InvalidTrustDomainException;
import io.gravitee.am.service.exception.TrustDomainAlreadyExistsException;
import io.gravitee.am.service.exception.TrustDomainNotFoundException;
import io.gravitee.am.service.exception.TrustDomainSpiffeAlreadyExistsException;
import io.gravitee.am.service.model.NewTrustDomain;
import io.gravitee.am.service.model.UpdateTrustDomain;
import io.gravitee.am.service.reporter.builder.management.TrustDomainAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TrustDomainServiceImplTest {

    private static final String DOMAIN_ID = "domain-1";

    /** Self-signed throwaway certificate; only its parseability matters here. */
    private static final String PEM_CERTIFICATE = """
            -----BEGIN CERTIFICATE-----
            MIIDGzCCAgOgAwIBAgIUTjn3isOFd6UKhH07F2M9E0+1naMwDQYJKoZIhvcNAQEL
            BQAwHDEaMBgGA1UEAwwRdHJ1c3QtZG9tYWluLXRlc3QwIBcNMjYwODE5MDk0NTU3
            WhgPMjEyNjA3MjYwOTQ1NTdaMBwxGjAYBgNVBAMMEXRydXN0LWRvbWFpbi10ZXN0
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA1R3nx/2UCXBoJKsq2K3Z
            POGsH8Djxj2nnSZ081MVRQL3YgvrSmgzmRSwr6sYx9oJ8Kn32IINUi/aLiwzxlFL
            ACK3D/vjWmjP+kwIep64LCzHdD7PaP0ePgN/0jme1pOWVQgyHVs+0q0w6vepHpKu
            l9NcAkMPd7Pq0fUTPg9ebW2sGko7EKZvcsE3/nOA7fStycEtmscLr38wZFGIZSIw
            6QJ8Eww9Rn7CFhTXnE88/LGJ104YQ9taDR7uDQdB42dss2YQ/tWF82LL/PS/9zFW
            zdlF11dI1Xyo3LTTt584PQkDXp0B+wrT4YoOskTK2aMO1IIkV/iNMUDs6fzbtJvY
            AQIDAQABo1MwUTAdBgNVHQ4EFgQUmh/tKVGDTOrkfdEJ2J2+ZfA0ufswHwYDVR0j
            BBgwFoAUmh/tKVGDTOrkfdEJ2J2+ZfA0ufswDwYDVR0TAQH/BAUwAwEB/zANBgkq
            hkiG9w0BAQsFAAOCAQEAn84I8ZiCavT+RBk4f0eHd5pnT9Scj0zcEElwgDw+8nNM
            eyTSuPdngL9pQCSBbZbo7ZNCe0WFOf2W/SLIzRx6E3+z1ffAmLr9/LTC1+UNuh1n
            feEljw97Q0sJKd/EyyUg+ZwUwQ5yiTT8hAQdai1kWhRLbalI2cYYJ+1HlXD73eKM
            9WrINYHmLrXclebEtB3SPTTeGtMZ/ajKlcBV1fu/+OoN5e09hvvEEJZ48aNYZrTt
            2An+LQABjo4LNsjP1prK4PtQl1/PI/jHUiRkHfWZx3y2Rakw5bu+bWMMn3vL4Dsk
            rXiZ9rmdTvbQ38TVI3+G5aBLVP9DS+9cXdHfSlyVPg==
            -----END CERTIFICATE-----""";

    @InjectMocks
    private TrustDomainServiceImpl service = new TrustDomainServiceImpl();

    @Mock
    private TrustDomainRepository repository;

    @Mock
    private EventService eventService;

    @Mock
    private AuditService auditService;

    private Domain domain;
    private SpiffeDomainSettings spiffeSettings;
    private KeyRetrievalSettings keyRetrievalSettings;

    @Before
    public void setUp() {
        // @InjectMocks targets the @Autowired fields, but Spring's @Lazy/@Autowired makes
        // some fields not visible via constructor injection. Use reflection as a safety net.
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "eventService", eventService);
        ReflectionTestUtils.setField(service, "auditService", auditService);

        domain = new Domain();
        domain.setId(DOMAIN_ID);
        spiffeSettings = new SpiffeDomainSettings();
        spiffeSettings.setEnabled(true);
        OIDCSettings oidc = new OIDCSettings();
        keyRetrievalSettings = new KeyRetrievalSettings();
        oidc.setWorkloadIdentitySettings(spiffeSettings);
        domain.setOidc(oidc);
        domain.setKeyRetrievalSettings(keyRetrievalSettings);

        // create() builds the post-validate chain eagerly via andThen(...); the repository
        // call inside that chain therefore runs even on validation-failure paths. Default
        // to an empty result so the call doesn't NPE before validate's error propagates.
        lenient().when(repository.findByName(any(), any(), any())).thenReturn(Maybe.empty());
        lenient().when(repository.findBySpiffeTrustDomain(any(), any(), any())).thenReturn(Maybe.empty());
    }

    @Test
    public void create_rejects_whenSpiffeDisabled() {
        spiffeSettings.setEnabled(false);

        service.create(domain, validInput(), null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("SPIFFE workload identity is disabled"));
        verify(repository, never()).create(any());
    }

    @Test
    public void create_rejects_whenNameInvalid() {
        NewTrustDomain input = validInput();
        input.setName("INVALID NAME");

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("DNS-style label"));
    }

    @Test
    public void create_rejects_whenNameMissing() {
        NewTrustDomain input = validInput();
        input.setName(null);

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class);
    }

    @Test
    public void create_rejects_whenKeyMaterialMissing() {
        NewTrustDomain input = validInput();
        input.setKeyMaterial(null);

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("keyMaterial.source is required"));
    }

    @Test
    public void create_rejects_whenKeyMaterialSourceMissing() {
        NewTrustDomain input = validInput();
        input.setKeyMaterial(TrustDomainKeyMaterial.builder().jwksUrl("https://example.com/keys").build());

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("keyMaterial.source is required"));
    }

    @Test
    public void create_rejects_whenJwksUrlMissing() {
        NewTrustDomain input = validInput();
        input.setKeyMaterial(TrustDomainKeyMaterial.builder().source(KeyMaterialSource.JWKS_URL).build());

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("keyMaterial.jwksUrl is required"));
    }

    @Test
    public void create_rejects_whenJwksUrlBlank() {
        NewTrustDomain input = jwksUrlInput("   ");

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class);
    }

    @Test
    public void create_rejects_whenJwksUrlResolvesToPrivateAddress() {
        NewTrustDomain input = jwksUrlInput("https://10.0.0.1/keys");

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("private/loopback"));
    }

    @Test
    public void create_allowsPrivateAddress_whenPolicyPermits() {
        keyRetrievalSettings.setAllowPrivateIpAddress(true);
        NewTrustDomain input = jwksUrlInput("https://10.0.0.1/keys");
        stubRepoForCreate();

        service.create(domain, input, null).test()
                .assertNoErrors();
    }

    @Test
    public void create_rejectsHttp_whenPolicyDisallows() {
        NewTrustDomain input = jwksUrlInput("http://example.org/keys");

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("http"));
    }

    @Test
    public void shouldDefaultSpiffeTrustDomainToName_whenNotProvided() {
        NewTrustDomain input = validInput();
        stubRepoForCreate();

        service.create(domain, input, null).test()
                .assertNoErrors()
                .assertValue(created -> "example.org".equals(created.getSpiffeTrustDomain()));
    }

    @Test
    public void shouldLowercaseTheSpiffeTrustDomainButKeepTheNameAsTyped() {
        NewTrustDomain input = validInput();
        input.setName("Acme Corp");
        input.setSpiffeTrustDomain("ACME.ORG");
        stubRepoForCreate();

        service.create(domain, input, null).test()
                .assertNoErrors()
                .assertValue(created -> "Acme Corp".equals(created.getName()))
                .assertValue(created -> "acme.org".equals(created.getSpiffeTrustDomain()));
    }

    @Test
    public void shouldCheckDuplicateNameAcrossEveryTrustedDomain() {
        stubRepoForCreate();

        service.create(domain, validInput(), null).test().assertNoErrors();

        verify(repository).findByName(ReferenceType.DOMAIN, DOMAIN_ID, "example.org");
    }

    @Test
    public void shouldRejectDuplicateSpiffeTrustDomain() {
        when(repository.findBySpiffeTrustDomain(ReferenceType.DOMAIN, DOMAIN_ID, "example.org"))
                .thenReturn(Maybe.just(new TrustDomain()));

        service.create(domain, validInput(), null).test()
                .assertError(TrustDomainSpiffeAlreadyExistsException.class);
        verify(repository, never()).create(any());
    }

    @Test
    public void shouldRejectASpiffeTrustDomainThatIsNotADnsStyleLabel() {
        NewTrustDomain input = validInput();
        input.setSpiffeTrustDomain("-nope-");

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("spiffeTrustDomain must be a DNS-style label"));
    }

    @Test
    public void shouldRejectANameLongerThanTheColumn() {
        NewTrustDomain input = validInput();
        input.setName("a".repeat(TrustDomain.NAME_MAX_LENGTH + 1));

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("name must be at most 255 characters"));
    }

    @Test
    public void shouldRejectASpiffeTrustDomainLongerThanTheColumn() {
        NewTrustDomain input = validInput();
        input.setSpiffeTrustDomain("a".repeat(TrustDomain.SPIFFE_TRUST_DOMAIN_MAX_LENGTH + 1));

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("spiffeTrustDomain must be at most 255 characters"));
    }

    @Test
    public void shouldAcceptAFreeFormNameNowThatItIsOnlyALabel() {
        NewTrustDomain input = validInput();
        input.setName("Acme Corp (prod)");
        input.setSpiffeTrustDomain("acme.org");
        stubRepoForCreate();

        service.create(domain, input, null).test().assertNoErrors();
    }

    @Test
    public void shouldAcceptPemKeyMaterial() {
        NewTrustDomain input = validInput();
        input.setKeyMaterial(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.PEM)
                .certificate(PEM_CERTIFICATE)
                .build());
        stubRepoForCreate();

        service.create(domain, input, null).test()
                .assertNoErrors()
                .assertValue(created -> created.getKeyMaterial().getSource() == KeyMaterialSource.PEM);
    }

    @Test
    public void shouldRejectUnparseablePemKeyMaterial() {
        NewTrustDomain input = validInput();
        input.setKeyMaterial(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.PEM)
                .certificate("not-a-certificate")
                .build());

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("not a valid PEM-encoded X.509 certificate"));
    }

    @Test
    public void shouldRejectPemKeyMaterialWithoutCertificate() {
        NewTrustDomain input = validInput();
        input.setKeyMaterial(TrustDomainKeyMaterial.builder().source(KeyMaterialSource.PEM).build());

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("keyMaterial.certificate is required"));
    }

    @Test
    public void shouldAcceptInlineJwkSetKeyMaterial() {
        NewTrustDomain input = validInput();
        input.setKeyMaterial(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWK_SET)
                .jwkSet(inlineJwkSet())
                .build());
        stubRepoForCreate();

        service.create(domain, input, null).test()
                .assertNoErrors()
                .assertValue(created -> created.getKeyMaterial().getJwkSet().getKeys().size() == 1);
    }

    @Test
    public void shouldRejectEmptyInlineJwkSet() {
        NewTrustDomain input = validInput();
        input.setKeyMaterial(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWK_SET)
                .jwkSet(new JWKSet())
                .build());

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("keyMaterial.jwkSet must contain at least one key"));
    }

    @Test
    public void shouldAcceptDeprecatedBundleSourceInput() {
        NewTrustDomain input = new NewTrustDomain();
        input.setName("example.org");
        input.setBundleSource(SpiffeBundleSource.JWKS_URL);
        input.setJwksUrl("https://example.com/keys");
        stubRepoForCreate();

        service.create(domain, input, null).test()
                .assertNoErrors()
                .assertValue(created -> created.getKeyMaterial().getSource() == KeyMaterialSource.JWKS_URL)
                .assertValue(created -> "https://example.com/keys".equals(created.getKeyMaterial().getJwksUrl()));
    }

    @Test
    public void shouldIgnoreDeprecatedBundleSourceInput_whenKeyMaterialSupplied() {
        NewTrustDomain input = validInput();
        input.setBundleSource(SpiffeBundleSource.JWKS_URL);
        input.setJwksUrl("https://deprecated.example.com/keys");
        stubRepoForCreate();

        service.create(domain, input, null).test()
                .assertNoErrors()
                .assertValue(created -> "https://example.com/keys".equals(created.getKeyMaterial().getJwksUrl()));
    }

    @Test
    public void shouldUpdateJwksUrlFromDeprecatedInputWithoutBundleSource() {
        stubExistingTrustDomainForUpdate();

        UpdateTrustDomain input = new UpdateTrustDomain();
        input.setJwksUrl("https://example.com/v2/keys");

        service.update(domain, "td-1", input, null).test()
                .assertNoErrors()
                .assertValue(saved -> saved.getKeyMaterial().getSource() == KeyMaterialSource.JWKS_URL)
                .assertValue(saved -> "https://example.com/v2/keys".equals(saved.getKeyMaterial().getJwksUrl()));
    }

    @Test
    public void shouldLeaveKeyMaterialUntouched_whenUpdateCarriesNone() {
        stubExistingTrustDomainForUpdate();

        service.update(domain, "td-1", new UpdateTrustDomain(), null).test()
                .assertNoErrors()
                .assertValue(saved -> "https://example.com/keys".equals(saved.getKeyMaterial().getJwksUrl()));
    }

    @Test
    public void shouldUpdateKeyMaterialFromDeprecatedInput() {
        stubExistingTrustDomainForUpdate();

        UpdateTrustDomain input = new UpdateTrustDomain();
        input.setBundleSource(SpiffeBundleSource.JWKS_URL);
        input.setJwksUrl("https://example.com/v2/keys");

        service.update(domain, "td-1", input, null).test()
                .assertNoErrors()
                .assertValue(saved -> "https://example.com/v2/keys".equals(saved.getKeyMaterial().getJwksUrl()));
    }

    @Test
    public void create_rejects_whenRefreshIntervalZero() {
        NewTrustDomain input = validInput();
        input.setRefreshIntervalSeconds(0);

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("refreshIntervalSeconds must be positive"));
    }

    @Test
    public void create_rejects_whenRefreshIntervalNegative() {
        NewTrustDomain input = validInput();
        input.setRefreshIntervalSeconds(-1);

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class);
    }

    @Test
    public void create_usesDefault_whenRefreshIntervalNotProvided() {
        NewTrustDomain input = validInput();
        input.setRefreshIntervalSeconds(null);
        stubRepoForCreate();

        service.create(domain, input, null).test()
                .assertNoErrors();
    }

    @Test
    public void create_rejects_whenAllowedAlgorithmsContainsNone() {
        NewTrustDomain input = validInput();
        input.setAllowedAlgorithms(List.of("RS256", "none"));

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("HMAC"));
    }

    @Test
    public void create_rejects_whenAllowedAlgorithmsContainsHs256() {
        NewTrustDomain input = validInput();
        input.setAllowedAlgorithms(List.of("HS256"));

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class);
    }

    @Test
    public void create_rejects_whenAllowedAlgorithmsContainsHs512() {
        NewTrustDomain input = validInput();
        input.setAllowedAlgorithms(List.of("hs512"));

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class);
    }

    @Test
    public void create_rejects_whenAllowedAlgorithmsContainsBlank() {
        NewTrustDomain input = validInput();
        input.setAllowedAlgorithms(List.of("RS256", "  "));

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class);
    }

    @Test
    public void create_acceptsValidAllowedAlgorithms() {
        NewTrustDomain input = validInput();
        input.setAllowedAlgorithms(List.of("RS256", "ES256", "EdDSA"));
        stubRepoForCreate();

        service.create(domain, input, null).test().assertNoErrors();
    }

    private NewTrustDomain validInput() {
        return jwksUrlInput("https://example.com/keys");
    }

    private NewTrustDomain jwksUrlInput(String jwksUrl) {
        NewTrustDomain input = new NewTrustDomain();
        input.setName("example.org");
        input.setKeyMaterial(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWKS_URL)
                .jwksUrl(jwksUrl)
                .build());
        input.setRefreshIntervalSeconds(60);
        return input;
    }

    private void stubExistingTrustDomainForUpdate() {
        TrustDomain existing = new TrustDomain();
        existing.setId("td-1");
        existing.setReferenceType(ReferenceType.DOMAIN);
        existing.setReferenceId(DOMAIN_ID);
        existing.setName("example.org");
        existing.setSpiffeTrustDomain("example.org");
        existing.setRefreshIntervalSeconds(60);
        existing.setKeyMaterial(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWKS_URL)
                .jwksUrl("https://example.com/keys")
                .build());
        when(repository.findById("td-1")).thenReturn(Maybe.just(existing));
        when(repository.update(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));
        when(eventService.create(any(), any())).thenReturn(Single.just(new io.gravitee.am.model.common.event.Event()));
    }

    private static JWKSet inlineJwkSet() {
        RSAKey key = new RSAKey();
        key.setKid("key-1");
        key.setE("AQAB");
        key.setN("0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86z");
        JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(List.of(key));
        return jwkSet;
    }

    @SuppressWarnings("unchecked")
    private void stubRepoForCreate() {
        // repository.findByName is already stubbed in @Before; override is unnecessary.
        when(repository.create(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));
        when(eventService.create(any(), any())).thenReturn(Single.just(new io.gravitee.am.model.common.event.Event()));
    }

    // ---- audit-on-failure tests ----------------------------------------------------
    // Verify that error paths outside the persistence chain (validation, duplicate name,
    // not-found, wrong-domain) still emit an audit event. Prior to the audit refactor
    // these paths exited without being recorded.

    @Test
    public void create_audits_whenValidationFails() {
        spiffeSettings.setEnabled(false);

        service.create(domain, validInput(), null).test().assertError(InvalidTrustDomainException.class);

        verify(auditService).report(any(TrustDomainAuditBuilder.class));
    }

    @Test
    public void create_audits_whenDuplicateName() {
        TrustDomain existing = new TrustDomain();
        existing.setId("existing-1");
        existing.setName("example.org");
        when(repository.findByName(any(), any(), any())).thenReturn(Maybe.just(existing));

        service.create(domain, validInput(), null).test().assertError(TrustDomainAlreadyExistsException.class);

        verify(auditService).report(any(TrustDomainAuditBuilder.class));
    }

    @Test
    public void update_audits_whenNotFound() {
        when(repository.findById("missing")).thenReturn(Maybe.empty());

        service.update(domain, "missing", new UpdateTrustDomain(), null).test()
                .assertError(TrustDomainNotFoundException.class);

        verify(auditService).report(any(TrustDomainAuditBuilder.class));
    }

    @Test
    public void update_audits_whenLinkedToWrongDomain() {
        TrustDomain other = new TrustDomain();
        other.setId("td-1");
        other.setReferenceType(ReferenceType.DOMAIN);
        other.setReferenceId("some-other-domain");
        when(repository.findById("td-1")).thenReturn(Maybe.just(other));

        service.update(domain, "td-1", new UpdateTrustDomain(), null).test()
                .assertError(InvalidTrustDomainException.class);

        verify(auditService).report(any(TrustDomainAuditBuilder.class));
    }

    @Test
    public void delete_audits_whenNotFound() {
        when(repository.findById("missing")).thenReturn(Maybe.empty());

        service.delete(domain, "missing", null).test().assertError(TrustDomainNotFoundException.class);

        verify(auditService).report(any(TrustDomainAuditBuilder.class));
    }

    @Test
    public void delete_audits_whenLinkedToWrongDomain() {
        TrustDomain other = new TrustDomain();
        other.setId("td-1");
        other.setReferenceType(ReferenceType.DOMAIN);
        other.setReferenceId("some-other-domain");
        when(repository.findById("td-1")).thenReturn(Maybe.just(other));

        service.delete(domain, "td-1", null).test().assertError(InvalidTrustDomainException.class);

        verify(auditService).report(any(TrustDomainAuditBuilder.class));
    }

    @Test
    public void delete_audits_onSuccess() {
        TrustDomain td = new TrustDomain();
        td.setId("td-1");
        td.setReferenceType(ReferenceType.DOMAIN);
        td.setReferenceId(DOMAIN_ID);
        when(repository.findById("td-1")).thenReturn(Maybe.just(td));
        when(repository.delete("td-1")).thenReturn(Completable.complete());
        when(eventService.create(any(), any())).thenReturn(Single.just(new io.gravitee.am.model.common.event.Event()));

        service.delete(domain, "td-1", null).test().assertNoErrors();

        verify(auditService).report(any(TrustDomainAuditBuilder.class));
    }
}
