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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Domain;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.UserBindingCriterion;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.model.oidc.CrossAppAccessResourceServer;
import io.gravitee.am.model.oidc.CrossAppAccessSettings;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.KeyMaterialSource;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.oidc.SpiffeBundleSource;
import io.gravitee.am.model.KeyRetrievalSettings;
import io.gravitee.am.model.oidc.SpiffeDomainSettings;
import io.gravitee.am.model.oidc.TrustedDomain;
import io.gravitee.am.model.oidc.SpiffeTrustSettings;
import io.gravitee.am.model.oidc.TokenExchangeTrustSettings;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.gravitee.am.repository.management.api.TrustedDomainRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.InvalidTrustDomainException;
import io.gravitee.am.service.exception.TrustDomainAlreadyExistsException;
import io.gravitee.am.service.exception.TrustDomainIssuerAlreadyExistsException;
import io.gravitee.am.service.exception.TrustDomainNotFoundException;
import io.gravitee.am.service.exception.TrustDomainSpiffeAlreadyExistsException;
import io.gravitee.am.service.model.NewTrustDomain;
import io.gravitee.am.service.model.NewTrustedDomain;
import io.gravitee.am.service.model.UpdateTrustDomain;
import io.gravitee.am.service.model.UpdateTrustedDomain;
import io.gravitee.am.service.reporter.builder.management.TrustDomainAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
    private TrustedDomainRepository repository;

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

        lenient().when(repository.findByName(any(), any(), any())).thenReturn(Maybe.empty());
        lenient().when(repository.findBySpiffeTrustDomain(any(), any(), any())).thenReturn(Maybe.empty());
        lenient().when(repository.findByIssuer(any(), any(), any())).thenReturn(Maybe.empty());
    }

    @Test
    public void shouldCreateSpiffeTrustedDomain_whenSpiffeDisabled() {
        spiffeSettings.setEnabled(false);
        stubRepoForCreate();

        service.create(domain, validInput(), null).test()
                .assertNoErrors()
                .assertValue(created -> "example.org".equals(created.getSpiffeTrustDomain()));
        verify(repository).create(any());
    }

    @Test
    public void shouldUpdateSpiffeTrustedDomain_whenSpiffeDisabled() {
        spiffeSettings.setEnabled(false);
        stubExistingSpiffeTrustDomainForUpdate();
        UpdateTrustedDomain input = new UpdateTrustedDomain();
        input.setDescription("edited");

        service.update(domain, "td-1", input, null).test()
                .assertNoErrors()
                .assertValue(saved -> "edited".equals(saved.getDescription()))
                .assertValue(saved -> "example.org".equals(saved.getSpiffeTrustDomain()));
        verify(repository).update(any());
    }

    @Test
    public void create_rejects_whenNameInvalid() {
        NewTrustDomain input = new NewTrustDomain();
        input.setName("INVALID NAME");
        input.setJwksUrl("https://example.com/keys");

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("DNS-style label"));
    }

    @Test
    public void create_rejects_whenNameMissing() {
        NewTrustedDomain input = validInput();
        input.setName(null);

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class);
    }

    @Test
    public void create_rejects_whenKeyMaterialMissing() {
        NewTrustedDomain input = validInput();
        input.setKeyMaterial(null);

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("keyMaterial.source is required"));
    }

    @Test
    public void create_rejects_whenKeyMaterialSourceMissing() {
        NewTrustedDomain input = validInput();
        input.setKeyMaterial(TrustDomainKeyMaterial.builder().jwksUrl("https://example.com/keys").build());

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("keyMaterial.source is required"));
    }

    @Test
    public void create_rejects_whenJwksUrlMissing() {
        NewTrustedDomain input = validInput();
        input.setKeyMaterial(TrustDomainKeyMaterial.builder().source(KeyMaterialSource.JWKS_URL).build());

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("keyMaterial.jwksUrl is required"));
    }

    @Test
    public void create_rejects_whenJwksUrlBlank() {
        NewTrustedDomain input = jwksUrlInput("   ");

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class);
    }

    @Test
    public void create_rejects_whenJwksUrlResolvesToPrivateAddress() {
        NewTrustedDomain input = jwksUrlInput("https://10.0.0.1/keys");

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("private/loopback"));
    }

    @Test
    public void create_allowsPrivateAddress_whenPolicyPermits() {
        keyRetrievalSettings.setAllowPrivateIpAddress(true);
        NewTrustedDomain input = jwksUrlInput("https://10.0.0.1/keys");
        stubRepoForCreate();

        service.create(domain, input, null).test()
                .assertNoErrors();
    }

    @Test
    public void create_rejectsHttp_whenPolicyDisallows() {
        NewTrustedDomain input = jwksUrlInput("http://example.org/keys");

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("http"));
    }

    @Test
    public void shouldDefaultSpiffeTrustDomainToName_whenNoMatcherProvided() {
        NewTrustedDomain input = validInput();
        stubRepoForCreate();

        service.create(domain, input, null).test()
                .assertNoErrors()
                .assertValue(created -> "example.org".equals(created.getSpiffeTrustDomain()))
                .assertValue(created -> created.getDomainIdentifier() == null);
    }

    @Test
    public void shouldLowercaseTheSpiffeTrustDomainButKeepTheNameAsTyped() {
        NewTrustedDomain input = validInput();
        input.setName("Acme Corp");
        spiffeOf(input).setSpiffeTrustDomain("ACME.ORG");
        stubRepoForCreate();

        service.create(domain, input, null).test()
                .assertNoErrors()
                .assertValue(created -> "Acme Corp".equals(created.getName()))
                .assertValue(created -> "acme.org".equals(created.getSpiffeTrustDomain()));
    }

    @Test
    public void shouldCreateTokenExchangeTrustedDomain_whenSpiffeDisabled() {
        spiffeSettings.setEnabled(false);
        stubRepoForCreate();
        stubNoIssuerConflict();

        service.create(domain, tokenExchangeInput(), null).test()
                .assertNoErrors()
                .assertValue(created -> "https://issuer.example.com".equals(created.getDomainIdentifier()))
                .assertValue(created -> created.getSpiffeTrustDomain() == null);
    }

    @Test
    public void shouldServeBothUsagesFromOneTrustedDomain() {
        stubRepoForCreate();
        stubNoIssuerConflict();
        NewTrustedDomain input = tokenExchangeInput();
        input.setName("acme-corp");
        spiffeOf(input).setSpiffeTrustDomain("acme.org");

        service.create(domain, input, null).test()
                .assertNoErrors()
                .assertValue(created -> "acme.org".equals(created.getSpiffeTrustDomain()))
                .assertValue(created -> "https://issuer.example.com".equals(created.getDomainIdentifier()));
    }

    @Test
    public void shouldCheckDuplicateNameAcrossEveryTrustedDomain() {
        stubRepoForCreate();
        stubNoIssuerConflict();

        service.create(domain, tokenExchangeInput(), null).test().assertNoErrors();

        verify(repository).findByName(ReferenceType.DOMAIN, DOMAIN_ID, "issuer.example.com");
    }

    @Test
    public void shouldRejectDuplicateSpiffeTrustDomain() {
        when(repository.findBySpiffeTrustDomain(ReferenceType.DOMAIN, DOMAIN_ID, "example.org"))
                .thenReturn(Maybe.just(new TrustedDomain()));

        service.create(domain, validInput(), null).test()
                .assertError(TrustDomainSpiffeAlreadyExistsException.class);
        verify(repository, never()).create(any());
    }

    @Test
    public void shouldRejectASpiffeTrustDomainThatIsNotADnsStyleLabel() {
        NewTrustedDomain input = validInput();
        spiffeOf(input).setSpiffeTrustDomain("-nope-");

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("spiffeTrustDomain must be a DNS-style label"));
    }

    @Test
    public void shouldRejectANameLongerThanTheColumn() {
        NewTrustedDomain input = validInput();
        input.setName("a".repeat(TrustedDomain.NAME_MAX_LENGTH + 1));

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("name must be at most 255 characters"));
    }

    @Test
    public void shouldRejectASpiffeTrustDomainLongerThanTheColumn() {
        NewTrustedDomain input = validInput();
        spiffeOf(input).setSpiffeTrustDomain("a".repeat(TrustedDomain.SPIFFE_TRUST_DOMAIN_MAX_LENGTH + 1));

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("spiffeTrustDomain must be at most 255 characters"));
    }

    @Test
    public void shouldAcceptAFreeFormNameNowThatItIsOnlyALabel() {
        NewTrustedDomain input = validInput();
        input.setName("Acme Corp (prod)");
        spiffeOf(input).setSpiffeTrustDomain("acme.org");
        stubRepoForCreate();

        service.create(domain, input, null).test().assertNoErrors();
    }

    @Test
    public void shouldAcceptPemKeyMaterial() {
        NewTrustedDomain input = validInput();
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
        NewTrustedDomain input = validInput();
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
        NewTrustedDomain input = validInput();
        input.setKeyMaterial(TrustDomainKeyMaterial.builder().source(KeyMaterialSource.PEM).build());

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("keyMaterial.certificate is required"));
    }

    @Test
    public void shouldAcceptInlineJwkSetKeyMaterial() {
        NewTrustedDomain input = validInput();
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
        NewTrustedDomain input = validInput();
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
        NewTrustedDomain input = validInput();
        input.getKeyMaterial().setRefreshIntervalSeconds(0);

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("refreshIntervalSeconds must be positive"));
    }

    @Test
    public void create_rejects_whenRefreshIntervalNegative() {
        NewTrustedDomain input = validInput();
        input.getKeyMaterial().setRefreshIntervalSeconds(-1);

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class);
    }

    @Test
    public void create_usesDefault_whenRefreshIntervalNotProvided() {
        NewTrustedDomain input = validInput();
        input.getKeyMaterial().setRefreshIntervalSeconds(null);
        stubRepoForCreate();

        service.create(domain, input, null).test()
                .assertNoErrors();
    }

    @Test
    public void create_rejects_whenAllowedAlgorithmsContainsNone() {
        NewTrustedDomain input = validInput();
        spiffeOf(input).setAllowedAlgorithms(List.of("RS256", "none"));

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("HMAC"));
    }

    @Test
    public void create_rejects_whenAllowedAlgorithmsContainsHs256() {
        NewTrustedDomain input = validInput();
        spiffeOf(input).setAllowedAlgorithms(List.of("HS256"));

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class);
    }

    @Test
    public void create_rejects_whenAllowedAlgorithmsContainsHs512() {
        NewTrustedDomain input = validInput();
        spiffeOf(input).setAllowedAlgorithms(List.of("hs512"));

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class);
    }

    @Test
    public void create_rejects_whenAllowedAlgorithmsContainsBlank() {
        NewTrustedDomain input = validInput();
        spiffeOf(input).setAllowedAlgorithms(List.of("RS256", "  "));

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class);
    }

    @Test
    public void create_acceptsValidAllowedAlgorithms() {
        NewTrustedDomain input = validInput();
        spiffeOf(input).setAllowedAlgorithms(List.of("RS256", "ES256", "EdDSA"));
        stubRepoForCreate();

        service.create(domain, input, null).test().assertNoErrors();
    }

    private NewTrustedDomain validInput() {
        return jwksUrlInput("https://example.com/keys");
    }

    private NewTrustedDomain jwksUrlInput(String jwksUrl) {
        NewTrustedDomain input = new NewTrustedDomain();
        input.setName("example.org");
        input.setKeyMaterial(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWKS_URL)
                .jwksUrl(jwksUrl)
                .refreshIntervalSeconds(60)
                .build());
        input.setSpiffe(SpiffeTrustSettings.builder().spiffeTrustDomain("example.org").build());
        return input;
    }

    private static SpiffeTrustSettings spiffeOf(NewTrustedDomain input) {
        if (input.getSpiffe() == null) {
            input.setSpiffe(new SpiffeTrustSettings());
        }
        return input.getSpiffe();
    }

    private static SpiffeTrustSettings spiffeOf(UpdateTrustedDomain input) {
        if (input.getSpiffe() == null) {
            input.setSpiffe(new SpiffeTrustSettings());
        }
        return input.getSpiffe();
    }

    private static TokenExchangeTrustSettings tokenExchangeOf(NewTrustedDomain input) {
        if (input.getTokenExchange() == null) {
            input.setTokenExchange(new TokenExchangeTrustSettings());
        }
        return input.getTokenExchange();
    }

    private static TokenExchangeTrustSettings tokenExchangeOf(UpdateTrustedDomain input) {
        if (input.getTokenExchange() == null) {
            input.setTokenExchange(new TokenExchangeTrustSettings());
        }
        return input.getTokenExchange();
    }

    private static void tokenExchangeIssuer(NewTrustedDomain input, String issuer) {
        input.setDomainIdentifier(issuer);
        tokenExchangeOf(input).setEnabled(true);
    }

    private static void tokenExchangeIssuer(UpdateTrustedDomain input, String issuer) {
        input.setDomainIdentifier(issuer);
        tokenExchangeOf(input).setEnabled(true);
    }

    private void stubExistingTrustDomainForUpdate() {
        stubExistingSpiffeTrustDomain();
        when(repository.update(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));
        when(eventService.create(any(), any())).thenReturn(Single.just(new io.gravitee.am.model.common.event.Event()));
    }

    private void stubExistingSpiffeTrustDomain() {
        when(repository.findById("td-1")).thenReturn(Maybe.just(spiffeEntity()));
    }

    private void stubExistingSpiffeTrustDomainForUpdate() {
        when(repository.findById("td-1")).thenReturn(Maybe.just(spiffeEntity()));
        when(repository.update(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));
        when(eventService.create(any(), any())).thenReturn(Single.just(new io.gravitee.am.model.common.event.Event()));
    }

    private static TrustedDomain spiffeEntity() {
        TrustedDomain existing = new TrustedDomain();
        existing.setId("td-1");
        existing.setReferenceType(ReferenceType.DOMAIN);
        existing.setReferenceId(DOMAIN_ID);
        existing.setName("example.org");
        existing.setSpiffe(SpiffeTrustSettings.builder().spiffeTrustDomain("example.org").build());
        existing.setKeyMaterial(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWKS_URL)
                .jwksUrl("https://example.com/keys")
                .refreshIntervalSeconds(60)
                .build());
        return existing;
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
        NewTrustedDomain input = validInput();
        input.setKeyMaterial(null);

        service.create(domain, input, null).test().assertError(InvalidTrustDomainException.class);

        verify(auditService).report(any(TrustDomainAuditBuilder.class));
    }

    @Test
    public void create_audits_whenDuplicateName() {
        TrustedDomain existing = new TrustedDomain();
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
        TrustedDomain other = new TrustedDomain();
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
        TrustedDomain other = new TrustedDomain();
        other.setId("td-1");
        other.setReferenceType(ReferenceType.DOMAIN);
        other.setReferenceId("some-other-domain");
        when(repository.findById("td-1")).thenReturn(Maybe.just(other));

        service.delete(domain, "td-1", null).test().assertError(InvalidTrustDomainException.class);

        verify(auditService).report(any(TrustDomainAuditBuilder.class));
    }

    @Test
    public void delete_audits_onSuccess() {
        TrustedDomain td = new TrustedDomain();
        td.setId("td-1");
        td.setReferenceType(ReferenceType.DOMAIN);
        td.setReferenceId(DOMAIN_ID);
        when(repository.findById("td-1")).thenReturn(Maybe.just(td));
        when(repository.delete("td-1")).thenReturn(Completable.complete());
        when(eventService.create(any(), any())).thenReturn(Single.just(new io.gravitee.am.model.common.event.Event()));

        service.delete(domain, "td-1", null).test().assertNoErrors();

        verify(auditService).report(any(TrustDomainAuditBuilder.class));
    }

    @Test
    public void shouldCreateTokenExchangeTrustedDomain() {
        stubRepoForCreate();
        stubNoIssuerConflict();

        service.create(domain, tokenExchangeInput(), null).test()
                .assertNoErrors()
                .assertValue(created -> "https://issuer.example.com".equals(created.getDomainIdentifier()));
    }

    @Test
    public void shouldRoundTripScopeMappingsAndUserBinding() {
        stubRepoForCreate();
        stubNoIssuerConflict();
        NewTrustedDomain input = tokenExchangeInput();
        tokenExchangeOf(input).setUserBindingEnabled(true);
        tokenExchangeOf(input).setUserBindingCriteria(List.of(criterion("emails.value", "{#token['email']}")));

        service.create(domain, input, null).test()
                .assertNoErrors()
                .assertValue(created -> Map.of("read", "domain:read").equals(created.getScopeMappings()))
                .assertValue(created -> created.isUserBindingEnabled())
                .assertValue(created -> created.getUserBindingCriteria().size() == 1)
                .assertValue(created -> "emails.value".equals(created.getUserBindingCriteria().get(0).getAttribute()));
    }

    @Test
    public void shouldAuditTokenExchangeCreateAgainstThePrincipal() {
        stubRepoForCreate();
        stubNoIssuerConflict();

        service.create(domain, tokenExchangeInput(), principal()).test().assertNoErrors();

        verifyAudit(EventType.TRUST_DOMAIN_CREATED);
    }

    @Test
    public void shouldAuditTokenExchangeUpdateAgainstThePrincipal() {
        stubExistingTokenExchangeForUpdate();

        UpdateTrustDomain input = new UpdateTrustDomain();
        input.setDescription("amended");

        service.update(domain, "td-1", input, principal()).test().assertNoErrors();

        verifyAudit(EventType.TRUST_DOMAIN_UPDATED);
    }

    @Test
    public void shouldDropAlgorithmsOnATokenExchangeOnlyTrustedDomain() {
        stubRepoForCreate();
        stubNoIssuerConflict();
        NewTrustedDomain input = tokenExchangeInput();
        spiffeOf(input).setAllowedAlgorithms(List.of("RS256"));

        service.create(domain, input, null).test()
                .assertNoErrors()
                .assertValue(created -> created.getSpiffe() == null)
                .assertValue(created -> created.getAllowedAlgorithms() == null);
    }

    @Test
    public void shouldRejectTrustedDomainWithoutAnyMatcher() {
        NewTrustedDomain input = tokenExchangeInput();
        input.setDomainIdentifier("  ");
        input.getTokenExchange().setEnabled(false);

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("must declare spiffe, tokenExchange, or crossAppAccess"));
    }

    @Test
    public void shouldRejectAnIssuerLongerThanTheColumn() {
        NewTrustedDomain input = tokenExchangeInput();
        tokenExchangeIssuer(input, "https://issuer.example.com/" + "a".repeat(TrustedDomain.ISSUER_MAX_LENGTH));

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("domainIdentifier must be at most 512 characters"));
    }

    @Test
    public void shouldAcceptAnIssuerOnlyTrustedDomainWithoutASpiffeTrustDomain() {
        NewTrustedDomain input = tokenExchangeInput();
        stubRepoForCreate();

        service.create(domain, input, null).test().assertNoErrors();
    }

    @Test
    public void shouldRejectTokenExchangeEnabledWithoutADomainIdentifier() {
        NewTrustedDomain input = tokenExchangeInput();
        input.setDomainIdentifier("  ");

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("domainIdentifier is required when token exchange is enabled"));
    }

    @Test
    public void shouldRejectScopeMappingsWithoutTokenExchange() {
        NewTrustedDomain input = validInput();
        tokenExchangeOf(input).setScopeMappings(Map.of("read", "domain:read"));

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("scopeMappings requires token exchange to be enabled"));
    }

    @Test
    public void shouldRejectUserBindingWithoutTokenExchange() {
        NewTrustedDomain input = validInput();
        tokenExchangeOf(input).setUserBindingEnabled(true);

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("userBindingEnabled requires token exchange to be enabled"));
    }

    @Test
    public void shouldRejectUserBindingWithoutCriteria() {
        NewTrustedDomain input = tokenExchangeInput();
        tokenExchangeOf(input).setUserBindingEnabled(true);

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("userBindingCriteria"));
    }

    @Test
    public void shouldRejectUserBindingCriterionWithBlankAttribute() {
        NewTrustedDomain input = tokenExchangeInput();
        tokenExchangeOf(input).setUserBindingEnabled(true);
        tokenExchangeOf(input).setUserBindingCriteria(List.of(criterion(" ", "{#token['email']}")));

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("non-blank attribute and expression"));
    }

    @Test
    public void shouldRejectDuplicateIssuerInSameDomain() {
        when(repository.findByIssuer(ReferenceType.DOMAIN, DOMAIN_ID, "https://issuer.example.com"))
                .thenReturn(Maybe.just(new TrustedDomain()));

        service.create(domain, tokenExchangeInput(), null).test()
                .assertError(TrustDomainIssuerAlreadyExistsException.class);
        verify(repository, never()).create(any());
    }

    @Test
    public void shouldAuditDuplicateIssuerRejection() {
        when(repository.findByIssuer(any(), any(), any())).thenReturn(Maybe.just(new TrustedDomain()));

        service.create(domain, tokenExchangeInput(), null).test()
                .assertError(TrustDomainIssuerAlreadyExistsException.class);

        verify(auditService).report(any(TrustDomainAuditBuilder.class));
    }

    @Test
    public void shouldNotCheckIssuerUniquenessForSpiffeTrustedDomain() {
        stubRepoForCreate();

        service.create(domain, validInput(), null).test().assertNoErrors();

        verify(repository, never()).findByIssuer(any(), any(), any());
    }

    @Test
    public void shouldCollapseTheSpiffeBlockWhenCreateCarriesNoMatcher() {
        stubRepoForCreate();
        stubNoIssuerConflict();
        NewTrustedDomain input = tokenExchangeInput();
        spiffeOf(input).setSpiffeTrustDomain("");
        spiffeOf(input).setAllowedAlgorithms(List.of("RS256"));

        service.create(domain, input, null).test()
                .assertNoErrors()
                .assertValue(created -> created.getSpiffe() == null);
    }

    @Test
    public void shouldCollapseTheTokenExchangeBlockWhenCreateCarriesNoIssuer() {
        stubRepoForCreate();
        NewTrustedDomain input = validInput();
        input.setDomainIdentifier("");
        tokenExchangeOf(input);

        service.create(domain, input, null).test()
                .assertNoErrors()
                .assertValue(created -> created.getTokenExchange() == null);
    }

    @Test
    public void shouldUpdateTokenExchangeSettings() {
        stubExistingTokenExchangeForUpdate();
        stubNoIssuerConflict();

        UpdateTrustedDomain input = new UpdateTrustedDomain();
        tokenExchangeIssuer(input, "https://issuer.example.com/v2");
        tokenExchangeOf(input).setScopeMappings(Map.of("write", "domain:write"));

        service.update(domain, "td-1", input, null).test()
                .assertNoErrors()
                .assertValue(saved -> "https://issuer.example.com/v2".equals(saved.getDomainIdentifier()))
                .assertValue(saved -> Map.of("write", "domain:write").equals(saved.getScopeMappings()));
    }

    @Test
    public void shouldClearTheIssuerWhenUpdateSendsABlankDomainIdentifier() {
        stubExistingTokenExchangeForUpdate();
        UpdateTrustedDomain input = new UpdateTrustedDomain();
        input.setDomainIdentifier("");
        tokenExchangeOf(input).setScopeMappings(Map.of());
        spiffeOf(input).setSpiffeTrustDomain("issuer.example.com");

        service.update(domain, "td-1", input, null).test()
                .assertNoErrors()
                .assertValue(saved -> saved.getDomainIdentifier() == null)
                .assertValue(saved -> saved.getTokenExchange() == null)
                .assertValue(saved -> "issuer.example.com".equals(saved.getSpiffeTrustDomain()));
    }

    @Test
    public void shouldClearTheSpiffeMatcherWhenUpdateSendsABlankOne() {
        stubExistingTrustDomainForUpdate();
        stubNoIssuerConflict();
        UpdateTrustedDomain input = new UpdateTrustedDomain();
        spiffeOf(input).setSpiffeTrustDomain("");
        tokenExchangeIssuer(input, "https://issuer.example.com");

        service.update(domain, "td-1", input, null).test()
                .assertNoErrors()
                .assertValue(saved -> saved.getSpiffeTrustDomain() == null)
                .assertValue(saved -> saved.getSpiffe() == null)
                .assertValue(saved -> "https://issuer.example.com".equals(saved.getDomainIdentifier()));
    }

    @Test
    public void shouldKeepTokenExchangeSettingsWhenUpdateCarriesNone() {
        stubExistingTokenExchangeForUpdate();

        service.update(domain, "td-1", new UpdateTrustDomain(), null).test()
                .assertNoErrors()
                .assertValue(saved -> "https://issuer.example.com".equals(saved.getDomainIdentifier()));
        verify(repository, never()).findByIssuer(any(), any(), any());
    }

    @Test
    public void shouldRejectUpdateIntroducingDuplicateIssuer() {
        stubExistingTokenExchange();
        TrustedDomain other = new TrustedDomain();
        other.setId("td-2");
        when(repository.findByIssuer(ReferenceType.DOMAIN, DOMAIN_ID, "https://issuer.example.com/v2"))
                .thenReturn(Maybe.just(other));

        UpdateTrustedDomain input = new UpdateTrustedDomain();
        tokenExchangeIssuer(input, "https://issuer.example.com/v2");

        service.update(domain, "td-1", input, null).test()
                .assertError(TrustDomainIssuerAlreadyExistsException.class);
        verify(repository, never()).update(any());
    }

    @Test
    public void shouldAllowUpdateKeepingItsOwnIssuer() {
        stubExistingTokenExchangeForUpdate();

        UpdateTrustedDomain input = new UpdateTrustedDomain();
        tokenExchangeIssuer(input, "https://issuer.example.com");
        tokenExchangeOf(input).setScopeMappings(Map.of("write", "domain:write"));

        service.update(domain, "td-1", input, null).test().assertNoErrors();
        verify(repository, never()).findByIssuer(any(), any(), any());
    }

    @Test
    public void shouldAddATokenExchangeBindingToASpiffeTrustedDomain() {
        stubExistingSpiffeTrustDomainForUpdate();
        stubNoIssuerConflict();

        UpdateTrustedDomain input = new UpdateTrustedDomain();
        spiffeOf(input).setSpiffeTrustDomain("example.org");
        tokenExchangeIssuer(input, "https://issuer.example.com");

        service.update(domain, "td-1", input, null).test()
                .assertNoErrors()
                .assertValue(saved -> "example.org".equals(saved.getSpiffeTrustDomain()))
                .assertValue(saved -> "https://issuer.example.com".equals(saved.getDomainIdentifier()));
    }

    @Test
    public void shouldDeleteTokenExchangeTrustedDomain() {
        when(repository.findById("td-1")).thenReturn(Maybe.just(tokenExchangeEntity()));
        when(repository.delete("td-1")).thenReturn(Completable.complete());
        when(eventService.create(any(), any())).thenReturn(Single.just(new io.gravitee.am.model.common.event.Event()));

        service.delete(domain, "td-1", principal()).test().assertNoErrors();

        verifyAudit(EventType.TRUST_DOMAIN_DELETED);
    }

    private static User principal() {
        DefaultUser user = new DefaultUser("admin");
        user.setId("principal-1");
        return user;
    }

    private void verifyAudit(String eventType) {
        verify(auditService).report(argThat(builder -> {
            Audit audit = builder.build(new ObjectMapper());
            assertEquals(ReferenceType.DOMAIN, audit.getReferenceType());
            assertEquals(DOMAIN_ID, audit.getReferenceId());
            assertEquals("principal-1", audit.getActor().getId());
            assertEquals(eventType, audit.getType());
            assertEquals(Status.SUCCESS, audit.getOutcome().getStatus());
            return true;
        }));
    }

    @Test
    public void shouldNotInventASpiffeMatcherWhenCrossAppAccessIsTheOnlyUsage() {
        stubRepoForCreate();
        stubNoIssuerConflict();

        service.create(domain, crossAppAccessInput(), null).test()
                .assertNoErrors()
                .assertValue(created -> created.getSpiffeTrustDomain() == null)
                .assertValue(created -> "https://auth.acme.com".equals(created.getDomainIdentifier()))
                .assertValue(TrustedDomain::trustsCrossAppAccess);
    }

    @Test
    public void shouldNotTrustTokenExchangeOnACrossAppAccessOnlyTrustedDomain() {
        stubRepoForCreate();
        stubNoIssuerConflict();

        service.create(domain, crossAppAccessInput(), null).test()
                .assertNoErrors()
                .assertValue(created -> created.getSpiffeTrustDomain() == null)
                .assertValue(created -> created.getTokenExchange() == null)
                .assertValue(created -> !created.trustsTokenExchange());
    }

    @Test
    public void shouldRejectATrustedDomainWhoseCrossAppAccessBlockIsDisabled() {
        NewTrustedDomain input = crossAppAccessInput();
        input.getCrossAppAccess().setEnabled(false);

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("must declare spiffe, tokenExchange, or crossAppAccess"));
    }

    @Test
    public void shouldRejectATrustedDomainWithNoMatcherAndADisabledCrossAppAccessBlock() {
        NewTrustedDomain input = crossAppAccessInput();
        input.getCrossAppAccess().setEnabled(false);

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("must declare spiffe, tokenExchange, or crossAppAccess"));
    }

    @Test
    public void shouldRejectAUserBindingExpressionThatDoesNotParse() {
        NewTrustedDomain input = tokenExchangeInput();
        tokenExchangeOf(input).setUserBindingEnabled(true);
        tokenExchangeOf(input).setUserBindingCriteria(List.of(criterion("email", "{#token['email']")));

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("userBindingCriteria expression is not a valid expression"));
    }

    @Test
    public void shouldAcceptAPlainClaimNameAsAUserBindingExpression() {
        stubRepoForCreate();
        stubNoIssuerConflict();
        NewTrustedDomain input = tokenExchangeInput();
        tokenExchangeOf(input).setUserBindingEnabled(true);
        tokenExchangeOf(input).setUserBindingCriteria(List.of(criterion("email", "email")));

        service.create(domain, input, null).test()
                .assertNoErrors();
    }

    @Test
    public void shouldRejectCrossAppAccessEnabledWithoutADomainIdentifier() {
        NewTrustedDomain input = crossAppAccessInput();
        input.setDomainIdentifier("  ");

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("domainIdentifier is required when Cross App Access is enabled"));
    }

    @Test
    public void shouldRejectADomainIdentifierThatIsNotAnAbsoluteUriWhenCrossAppAccessIsEnabled() {
        NewTrustedDomain input = crossAppAccessInput();
        input.setDomainIdentifier("/auth");

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("domainIdentifier must be an absolute URI"));
    }

    @Test
    public void shouldRejectAnIdentifierAlreadyHeldByAnotherTrustedDomain() {
        stubRepoForCreate();
        TrustedDomain sibling = new TrustedDomain();
        sibling.setId("td-sibling");
        when(repository.findByIssuer(ReferenceType.DOMAIN, DOMAIN_ID, "https://auth.acme.com"))
                .thenReturn(Maybe.just(sibling));

        service.create(domain, crossAppAccessInput(), null).test()
                .assertError(TrustDomainIssuerAlreadyExistsException.class);
    }

    @Test
    public void shouldRejectTheSameResourceTwiceWithinOneTrustedDomain() {
        NewTrustedDomain input = crossAppAccessInput();
        input.getCrossAppAccess().setResourceServers(List.of(
                CrossAppAccessResourceServer.builder().name("Calendar").resource("https://calendar.acme.com").build(),
                CrossAppAccessResourceServer.builder().name("Calendar again").resource("https://calendar.acme.com").build()));

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("must not repeat resource"));
    }

    @Test
    public void shouldRejectANullResourceServerEntry() {
        NewTrustedDomain input = crossAppAccessInput();
        input.getCrossAppAccess().setResourceServers(Collections.singletonList(null));

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("must not contain a null entry"));
    }

    @Test
    public void shouldRejectCrossAppAccessEnabledWithoutAnyResourceServer() {
        NewTrustedDomain input = crossAppAccessInput();
        input.getCrossAppAccess().setResourceServers(List.of());

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("must declare at least one resource server"));
    }

    @Test
    public void shouldNotRequireKeyMaterialOnACrossAppAccessOnlyTrustedDomain() {
        stubRepoForCreate();
        stubNoIssuerConflict();
        NewTrustedDomain input = crossAppAccessInput();
        input.setKeyMaterial(null);

        service.create(domain, input, null).test()
                .assertNoErrors()
                .assertValue(created -> created.getKeyMaterial() == null);
    }

    @Test
    public void shouldKeepKeyMaterialWhenATrustedDomainIsNarrowedToCrossAppAccessOnly() {
        stubExistingTokenExchangeForUpdate();
        stubNoIssuerConflict();
        UpdateTrustedDomain input = new UpdateTrustedDomain();
        input.setTokenExchange(new TokenExchangeTrustSettings());
        input.setCrossAppAccess(crossAppAccessSettings());

        service.update(domain, "td-1", input, null).test()
                .assertNoErrors()
                .assertValue(saved -> "https://issuer.example.com".equals(saved.getDomainIdentifier()))
                .assertValue(saved -> saved.getTokenExchange() == null)
                .assertValue(saved -> "https://example.com/issuer/keys".equals(saved.getKeyMaterial().getJwksUrl()));
    }

    @Test
    public void shouldKeepAStoredResourceServerIdAcrossARenameAndANewResource() {
        stubExistingCrossAppAccessForUpdate();
        UpdateTrustedDomain input = new UpdateTrustedDomain();
        input.setDomainIdentifier("https://issuer.example.com");
        input.setCrossAppAccess(writtenResourceServer("rs-1", "Renamed", "https://calendar.acme.com/moved"));

        service.update(domain, "td-1", input, null).test()
                .assertNoErrors()
                .assertValue(saved -> "rs-1".equals(resourceServer(saved).getId()))
                .assertValue(saved -> "Renamed".equals(resourceServer(saved).getName()))
                .assertValue(saved -> "https://calendar.acme.com/moved".equals(resourceServer(saved).getResource()));
    }

    @Test
    public void shouldGiveAFreshIdToAResourceServerThatIsNotStored() {
        stubExistingCrossAppAccessForUpdate();
        UpdateTrustedDomain input = new UpdateTrustedDomain();
        input.setDomainIdentifier("https://issuer.example.com");
        input.setCrossAppAccess(writtenResourceServer("invented", "Calendar", "https://calendar.acme.com"));

        service.update(domain, "td-1", input, null).test()
                .assertNoErrors()
                .assertValue(saved -> resourceServer(saved).getId() != null)
                .assertValue(saved -> !"invented".equals(resourceServer(saved).getId()));
    }

    @Test
    public void shouldGenerateAnIdForEveryResourceServerOnCreate() {
        stubRepoForCreate();
        stubNoIssuerConflict();

        service.create(domain, crossAppAccessInput(), null).test()
                .assertNoErrors()
                .assertValue(created -> resourceServer(created).getId() != null);
    }

    private static CrossAppAccessResourceServer resourceServer(TrustedDomain td) {
        return td.getCrossAppAccess().getResourceServers().get(0);
    }

    private static CrossAppAccessSettings writtenResourceServer(String id, String name, String resource) {
        CrossAppAccessSettings written = crossAppAccessSettings();
        written.setResourceServers(List.of(CrossAppAccessResourceServer.builder()
                .id(id)
                .name(name)
                .resource(resource)
                .build()));
        return written;
    }

    private void stubExistingCrossAppAccessForUpdate() {
        TrustedDomain existing = tokenExchangeEntity();
        CrossAppAccessSettings stored = crossAppAccessSettings();
        stored.getResourceServers().get(0).setId("rs-1");
        existing.setCrossAppAccess(stored);
        when(repository.findById("td-1")).thenReturn(Maybe.just(existing));
        when(repository.update(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));
        when(eventService.create(any(), any())).thenReturn(Single.just(new io.gravitee.am.model.common.event.Event()));
        stubNoIssuerConflict();
    }

    private static CrossAppAccessSettings crossAppAccessSettings() {
        CrossAppAccessSettings settings = new CrossAppAccessSettings();
        settings.setEnabled(true);
        settings.setResourceServers(List.of(CrossAppAccessResourceServer.builder()
                .name("Calendar")
                .resource("https://calendar.acme.com")
                .build()));
        settings.setAudSubMapping("{#user.email}");
        settings.setScopeMappings(Map.of("domain:read", "calendar.read"));
        return settings;
    }

    private NewTrustedDomain crossAppAccessInput() {
        NewTrustedDomain input = new NewTrustedDomain();
        input.setName("acme-corp");
        input.setDomainIdentifier("https://auth.acme.com");
        input.setKeyMaterial(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWKS_URL)
                .jwksUrl("https://example.com/issuer/keys")
                .refreshIntervalSeconds(60)
                .build());
        input.setCrossAppAccess(crossAppAccessSettings());
        return input;
    }

    private NewTrustedDomain tokenExchangeInput() {
        NewTrustedDomain input = new NewTrustedDomain();
        input.setName("issuer.example.com");
        input.setKeyMaterial(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWKS_URL)
                .jwksUrl("https://example.com/issuer/keys")
                .refreshIntervalSeconds(60)
                .build());
        input.setDomainIdentifier("https://issuer.example.com");
        input.setTokenExchange(TokenExchangeTrustSettings.builder()
                .enabled(true)
                .scopeMappings(Map.of("read", "domain:read"))
                .build());
        return input;
    }

    private static TrustedDomain tokenExchangeEntity() {
        TrustedDomain td = new TrustedDomain();
        td.setId("td-1");
        td.setReferenceType(ReferenceType.DOMAIN);
        td.setReferenceId(DOMAIN_ID);
        td.setName("issuer.example.com");
        td.setKeyMaterial(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWKS_URL)
                .jwksUrl("https://example.com/issuer/keys")
                .refreshIntervalSeconds(60)
                .build());
        td.setDomainIdentifier("https://issuer.example.com");
        td.setTokenExchange(TokenExchangeTrustSettings.builder()
                .enabled(true)
                .scopeMappings(Map.of("read", "domain:read"))
                .build());
        return td;
    }

    private void stubExistingTokenExchangeForUpdate() {
        when(repository.findById("td-1")).thenReturn(Maybe.just(tokenExchangeEntity()));
        when(repository.update(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));
        when(eventService.create(any(), any())).thenReturn(Single.just(new io.gravitee.am.model.common.event.Event()));
    }

    private void stubExistingTokenExchange() {
        when(repository.findById("td-1")).thenReturn(Maybe.just(tokenExchangeEntity()));
    }

    private void stubNoIssuerConflict() {
        lenient().when(repository.findByIssuer(any(), any(), any())).thenReturn(Maybe.empty());
    }

    private static UserBindingCriterion criterion(String attribute, String expression) {
        UserBindingCriterion criterion = new UserBindingCriterion();
        criterion.setAttribute(attribute);
        criterion.setExpression(expression);
        return criterion;
    }
}
