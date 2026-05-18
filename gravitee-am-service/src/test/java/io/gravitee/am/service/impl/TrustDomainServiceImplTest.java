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
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.model.oidc.SpiffeBundleSource;
import io.gravitee.am.model.oidc.SpiffeDomainSettings;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.repository.management.api.TrustDomainRepository;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.EventService;
import io.gravitee.am.service.exception.InvalidTrustDomainException;
import io.gravitee.am.service.exception.TrustDomainAlreadyExistsException;
import io.gravitee.am.service.exception.TrustDomainNotFoundException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TrustDomainServiceImplTest {

    private static final String DOMAIN_ID = "domain-1";

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
        oidc.setSpiffeSettings(spiffeSettings);
        domain.setOidc(oidc);

        // create() builds the post-validate chain eagerly via andThen(...); the repository
        // call inside that chain therefore runs even on validation-failure paths. Default
        // to an empty result so the call doesn't NPE before validate's error propagates.
        when(repository.findByName(any(), any(), any())).thenReturn(Maybe.empty());
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
    public void create_rejects_whenBundleSourceMissing() {
        NewTrustDomain input = validInput();
        input.setBundleSource(null);

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("bundleSource is required"));
    }

    @Test
    public void create_rejects_whenBundleSourceNotJwksUrl() {
        NewTrustDomain input = validInput();
        input.setBundleSource(SpiffeBundleSource.STATIC_JWKS);

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("Only bundleSource=JWKS_URL"));
    }

    @Test
    public void create_rejects_whenJwksUrlMissing() {
        NewTrustDomain input = validInput();
        input.setJwksUrl(null);

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("jwksUrl is required"));
    }

    @Test
    public void create_rejects_whenJwksUrlBlank() {
        NewTrustDomain input = validInput();
        input.setJwksUrl("   ");

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class);
    }

    @Test
    public void create_rejects_whenJwksUrlResolvesToPrivateAddress() {
        NewTrustDomain input = validInput();
        input.setJwksUrl("https://10.0.0.1/keys");

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("private/loopback"));
    }

    @Test
    public void create_allowsPrivateAddress_whenPolicyPermits() {
        spiffeSettings.setAllowPrivateIpAddress(true);
        NewTrustDomain input = validInput();
        input.setJwksUrl("https://10.0.0.1/keys");
        stubRepoForCreate();

        service.create(domain, input, null).test()
                .assertNoErrors();
    }

    @Test
    public void create_rejectsHttp_whenPolicyDisallows() {
        NewTrustDomain input = validInput();
        input.setJwksUrl("http://example.org/keys");

        service.create(domain, input, null).test()
                .assertError(InvalidTrustDomainException.class)
                .assertError(err -> err.getMessage().contains("http"));
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
        NewTrustDomain input = new NewTrustDomain();
        input.setName("example.org");
        input.setBundleSource(SpiffeBundleSource.JWKS_URL);
        input.setJwksUrl("https://example.com/keys");
        input.setRefreshIntervalSeconds(60);
        return input;
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
