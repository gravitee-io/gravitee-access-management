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
package io.gravitee.am.management.service.impl.upgrades;

import io.gravitee.am.management.service.DomainService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.KeyResolutionMethod;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.SystemTask;
import io.gravitee.am.model.SystemTaskStatus;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.model.TrustedIssuer;
import io.gravitee.am.model.UserBindingCriterion;
import io.gravitee.am.model.oidc.KeyMaterialSource;
import io.gravitee.am.model.oidc.SpiffeTrustSettings;
import io.gravitee.am.model.oidc.TokenExchangeTrustSettings;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.gravitee.am.repository.management.api.SystemTaskRepository;
import io.gravitee.am.repository.management.api.TrustDomainRepository;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainTrustedIssuerUpgraderTest {

    private static final String DOMAIN_ID = "domain-id";

    @Mock
    private SystemTaskRepository systemTaskRepository;

    @Mock
    private DomainService domainService;

    @Mock
    private TrustDomainRepository trustDomainRepository;

    @InjectMocks
    private DomainTrustedIssuerUpgrader upgrader;

    @Test
    void shouldMigrateTrustedIssuerToTokenExchangeTrustedDomain() {
        TrustedIssuer issuer = jwksIssuer("https://issuer.example.com", "https://issuer.example.com/jwks");

        TrustDomain migrated = migrateOne(domainWith(issuer));

        assertEquals(ReferenceType.DOMAIN, migrated.getReferenceType());
        assertEquals(DOMAIN_ID, migrated.getReferenceId());
        assertEquals("https://issuer.example.com", migrated.getIssuer());
        assertEquals(KeyMaterialSource.JWKS_URL, migrated.getKeyMaterial().getSource());
        assertEquals("https://issuer.example.com/jwks", migrated.getKeyMaterial().getJwksUrl());
        assertEquals(TrustDomain.DEFAULT_REFRESH_INTERVAL_SECONDS, migrated.getRefreshIntervalSeconds());
        assertNull(migrated.getAllowedAlgorithms());
    }

    @Test
    void shouldCarryScopeMappingsAndUserBindingConfiguration() {
        TrustedIssuer issuer = jwksIssuer("https://issuer.example.com", "https://issuer.example.com/jwks");
        issuer.setScopeMappings(Map.of("ext:read", "read"));
        issuer.setUserBindingEnabled(true);
        issuer.setUserBindingCriteria(List.of(criterion("email", "{#token.email}")));

        TrustDomain migrated = migrateOne(domainWith(issuer));

        assertEquals(Map.of("ext:read", "read"), migrated.getScopeMappings());
        assertTrue(migrated.isUserBindingEnabled());
        assertEquals(1, migrated.getUserBindingCriteria().size());
        assertEquals("email", migrated.getUserBindingCriteria().get(0).getAttribute());
        assertEquals("{#token.email}", migrated.getUserBindingCriteria().get(0).getExpression());
    }

    @Test
    void shouldCarryPemCertificate() {
        TrustedIssuer issuer = new TrustedIssuer();
        issuer.setIssuer("https://issuer.example.com");
        issuer.setKeyResolutionMethod(KeyResolutionMethod.PEM);
        issuer.setCertificate("-----BEGIN CERTIFICATE-----\nMII\n-----END CERTIFICATE-----");

        TrustDomainKeyMaterial keyMaterial = migrateOne(domainWith(issuer)).getKeyMaterial();

        assertEquals(KeyMaterialSource.PEM, keyMaterial.getSource());
        assertEquals("-----BEGIN CERTIFICATE-----\nMII\n-----END CERTIFICATE-----", keyMaterial.getCertificate());
        assertNull(keyMaterial.getJwksUrl());
    }

    @Test
    void shouldDeriveNameFromSlugifiedIssuerUrl() {
        TrustedIssuer issuer = jwksIssuer("https://Issuer.Example.com/realms/Trusted_One", "https://issuer.example.com/jwks");

        assertEquals("https-issuer.example.com-realms-trusted-one", migrateOne(domainWith(issuer)).getName());
    }

    @Test
    void shouldDeriveDistinctNamesForIssuersSharingHostButDifferentPath() {
        List<TrustDomain> migrated = migrate(domainWith(
                jwksIssuer("https://issuer.example.com/realms/one", "https://issuer.example.com/one/jwks"),
                jwksIssuer("https://issuer.example.com/realms/two", "https://issuer.example.com/two/jwks")));

        assertEquals("https-issuer.example.com-realms-one", migrated.get(0).getName());
        assertEquals("https-issuer.example.com-realms-two", migrated.get(1).getName());
    }

    @Test
    void shouldDeriveSameNamesWhateverTheDeclarationOrder() {
        TrustedIssuer one = jwksIssuer("https://issuer.example.com/a-b", "https://issuer.example.com/jwks");
        TrustedIssuer two = jwksIssuer("https://issuer.example.com/a/b", "https://issuer.example.com/jwks");
        Domain forward = domainWith(one, two);
        Domain backward = domainWith(two, one);
        backward.setId("other-domain-id");

        List<TrustDomain> migrated = migrateAll(forward, backward);

        assertEquals(namesOf(migrated, DOMAIN_ID), namesOf(migrated, "other-domain-id"));
    }

    @Test
    void shouldDisambiguateIssuersSlugifyingToTheSameName() {
        List<TrustDomain> migrated = migrate(domainWith(
                jwksIssuer("https://issuer.example.com/a-b", "https://issuer.example.com/jwks"),
                jwksIssuer("https://issuer.example.com/a/b", "https://issuer.example.com/jwks")));

        assertEquals("https-issuer.example.com-a-b-22504d54", migrated.get(0).getName());
        assertEquals("https-issuer.example.com-a-b-9d44d822", migrated.get(1).getName());
    }

    @Test
    void shouldTruncateDerivedNameToStorageLimit() {
        String longPath = "p".repeat(400);
        TrustedIssuer issuer = jwksIssuer("https://issuer.example.com/" + longPath, "https://issuer.example.com/jwks");

        assertEquals(255, migrateOne(domainWith(issuer)).getName().length());
    }

    @Test
    void shouldSkipTrustedIssuerWithoutIssuer() {
        TrustedIssuer blank = jwksIssuer(" ", "https://issuer.example.com/jwks");
        TrustedIssuer valid = jwksIssuer("https://issuer.example.com", "https://issuer.example.com/jwks");

        List<TrustDomain> migrated = migrate(domainWith(blank, valid));

        assertEquals(1, migrated.size());
        assertEquals("https://issuer.example.com", migrated.get(0).getIssuer());
    }

    @Test
    void shouldLeaveIssuerInlineWhenItIsTooLongToStore() {
        TrustedIssuer tooLong = jwksIssuer("https://issuer.example.com/" + "a".repeat(TrustDomain.ISSUER_MAX_LENGTH), "https://issuer.example.com/jwks");
        TrustedIssuer valid = jwksIssuer("https://issuer.example.com", "https://issuer.example.com/jwks");

        List<TrustDomain> migrated = migrate(domainWith(tooLong, valid));

        assertEquals(1, migrated.size());
        assertEquals("https://issuer.example.com", migrated.get(0).getIssuer());
    }

    @Test
    void shouldMigrateIssuerSittingExactlyOnTheStorageLimit() {
        String issuer = "https://issuer.example.com/" + "a".repeat(TrustDomain.ISSUER_MAX_LENGTH - "https://issuer.example.com/".length());

        assertEquals(issuer, migrateOne(domainWith(jwksIssuer(issuer, "https://issuer.example.com/jwks"))).getIssuer());
    }

    @Test
    void shouldLeaveSpiffeTrustDomainsUntouched() {
        Domain domain = domainWith(jwksIssuer("https://issuer.example.com", "https://issuer.example.com/jwks"));
        TrustDomain spiffe = TrustDomain.builder()
                .id("spiffe-id")
                .referenceType(ReferenceType.DOMAIN)
                .referenceId(DOMAIN_ID)
                .name("spiffe-label")
                .spiffe(SpiffeTrustSettings.builder().spiffeTrustDomain("issuer.example.com").build())
                .build();

        List<TrustDomain> migrated = migrate(domain, spiffe);

        assertEquals(1, migrated.size());
        assertEquals("https://issuer.example.com", migrated.get(0).getIssuer());
        verify(trustDomainRepository, never()).update(any());
        verify(trustDomainRepository, never()).delete(any());
    }

    @Test
    void shouldNotMigrateIssuerAlreadyCarriedByTrustedDomain() {
        Domain domain = domainWith(jwksIssuer("https://issuer.example.com", "https://issuer.example.com/jwks"));

        initializeSystemTask();
        stubDomainUpdate();
        when(domainService.listAll()).thenReturn(Flowable.just(domain));
        when(trustDomainRepository.findByReference(ReferenceType.DOMAIN, DOMAIN_ID))
                .thenReturn(Flowable.just(alreadyMigrated("https://issuer.example.com")));

        assertTrue(upgrader.upgrade());

        verify(trustDomainRepository, never()).create(any());
        verify(trustDomainRepository, never()).update(any());
    }

    @Test
    void shouldMigrateOnlyIssuersMissingFromStorage() {
        Domain domain = domainWith(
                jwksIssuer("https://one.example.com", "https://one.example.com/jwks"),
                jwksIssuer("https://two.example.com", "https://two.example.com/jwks"));

        List<TrustDomain> migrated = migrate(domain, alreadyMigrated("https://one.example.com"));

        assertEquals(1, migrated.size());
        assertEquals("https://two.example.com", migrated.get(0).getIssuer());
    }

    @Test
    void shouldDisambiguateWhenTheDerivedNameIsAlreadyHeld() {
        Domain domain = domainWith(jwksIssuer("https://issuer.example.com", "https://issuer.example.com/jwks"));

        TrustDomain migrated = migrateOne(domain, squatter("https-issuer.example.com"));

        assertEquals("https-issuer.example.com-605ec2f1", migrated.getName());
    }

    @Test
    void shouldLeaveIssuerInlineWhenEveryDerivedNameIsAlreadyHeld() {
        TrustedIssuer issuer = jwksIssuer("https://issuer.example.com", "https://issuer.example.com/jwks");
        Domain domain = domainWith(issuer);

        initializeSystemTask();
        when(domainService.listAll()).thenReturn(Flowable.just(domain));
        when(trustDomainRepository.findByReference(ReferenceType.DOMAIN, DOMAIN_ID)).thenReturn(Flowable.just(
                squatter("https-issuer.example.com"), squatter("https-issuer.example.com-605ec2f1")));

        assertTrue(upgrader.upgrade());

        verify(trustDomainRepository, never()).create(any());
        verify(domainService, never()).update(any(), any());
        assertSame(issuer, domain.getTokenExchangeSettings().getTrustedIssuers().get(0));
    }

    @Test
    void shouldNotRunTwiceOnceTheMigrationHasSucceeded() {
        SystemTask done = new SystemTask();
        done.setStatus(SystemTaskStatus.SUCCESS.name());
        when(systemTaskRepository.findById(anyString())).thenReturn(Maybe.just(done));

        assertTrue(upgrader.upgrade());

        verify(systemTaskRepository, times(1)).findById(anyString());
        verify(domainService, never()).listAll();
    }

    @Test
    void shouldMigrateIssuerDeclaringNoKeyResolutionMethod() {
        TrustedIssuer issuer = new TrustedIssuer();
        issuer.setIssuer("https://issuer.example.com");

        TrustDomain migrated = migrateOne(domainWith(issuer));

        assertEquals("https://issuer.example.com", migrated.getIssuer());
        assertNull(migrated.getKeyMaterial());
    }

    @Test
    void shouldRunBeforeEveryUpgraderRewritingADomain() {
        assertTrue(upgrader.getOrder() < UpgraderOrder.DOMAIN_DATA_PLANE_UPGRADER);
        assertTrue(upgrader.getOrder() < UpgraderOrder.DOMAIN_KEY_RETRIEVAL_SETTINGS_UPGRADER);
    }

    @Test
    void shouldDropInlineTrustedIssuersOnceMigrated() {
        Domain domain = domainWith(jwksIssuer("https://issuer.example.com", "https://issuer.example.com/jwks"));

        migrate(domain);

        verify(domainService).update(DOMAIN_ID, domain);
    }

    @Test
    void shouldHandleDomainWithoutTrustedIssuers() {
        Domain domain = domainWith();
        initializeSystemTask();
        when(domainService.listAll()).thenReturn(Flowable.just(domain));

        assertTrue(upgrader.upgrade());

        verify(trustDomainRepository, never()).create(any());
    }

    @Test
    void shouldHandleDomainWithoutTokenExchangeSettings() {
        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        initializeSystemTask();
        when(domainService.listAll()).thenReturn(Flowable.just(domain));

        assertTrue(upgrader.upgrade());

        verify(trustDomainRepository, never()).create(any());
    }

    private TrustDomain migrateOne(Domain domain, TrustDomain... existing) {
        List<TrustDomain> migrated = migrate(domain, existing);
        assertEquals(1, migrated.size());
        return migrated.get(0);
    }

    private List<TrustDomain> migrateAll(Domain... domains) {
        initializeSystemTask();
        stubDomainUpdate();
        when(domainService.listAll()).thenReturn(Flowable.fromArray(domains));
        when(trustDomainRepository.findByReference(any(), any())).thenReturn(Flowable.empty());
        return captureCreated();
    }

    private List<TrustDomain> migrate(Domain domain, TrustDomain... existing) {
        initializeSystemTask();
        stubDomainUpdate();
        when(domainService.listAll()).thenReturn(Flowable.just(domain));
        when(trustDomainRepository.findByReference(ReferenceType.DOMAIN, DOMAIN_ID))
                .thenReturn(Flowable.fromArray(existing));
        return captureCreated();
    }

    private List<TrustDomain> captureCreated() {
        ArgumentCaptor<TrustDomain> captor = ArgumentCaptor.forClass(TrustDomain.class);
        when(trustDomainRepository.create(captor.capture())).thenAnswer(i -> Single.just(i.getArgument(0)));

        assertTrue(upgrader.upgrade());

        return captor.getAllValues();
    }

    private void stubDomainUpdate() {
        lenient().when(domainService.update(anyString(), any(Domain.class))).thenAnswer(i -> Single.just(i.getArgument(1)));
    }

    private void initializeSystemTask() {
        when(systemTaskRepository.findById(anyString())).thenReturn(Maybe.empty());
        SystemTask task = new SystemTask();
        task.setStatus(SystemTaskStatus.INITIALIZED.name());
        when(systemTaskRepository.create(any())).thenReturn(Single.just(task));
        when(systemTaskRepository.updateIf(any(), anyString())).thenAnswer(args -> {
            SystemTask updated = args.getArgument(0);
            updated.setOperationId(args.getArgument(1));
            return Single.just(updated);
        });
    }

    private static List<String> namesOf(List<TrustDomain> migrated, String referenceId) {
        return migrated.stream()
                .filter(trustDomain -> referenceId.equals(trustDomain.getReferenceId()))
                .map(TrustDomain::getName)
                .sorted()
                .toList();
    }

    private static TrustDomain squatter(String name) {
        return TrustDomain.builder()
                .id("squatter-" + name)
                .referenceType(ReferenceType.DOMAIN)
                .referenceId(DOMAIN_ID)
                .name(name)
                .domainIdentifier("https://other.example.com")
                .tokenExchange(new TokenExchangeTrustSettings())
                .build();
    }

    private static TrustDomain alreadyMigrated(String issuer) {
        return TrustDomain.builder()
                .id("existing-" + issuer)
                .referenceType(ReferenceType.DOMAIN)
                .referenceId(DOMAIN_ID)
                .name("already-migrated")
                .domainIdentifier(issuer)
                .tokenExchange(new TokenExchangeTrustSettings())
                .build();
    }

    private static TrustedIssuer jwksIssuer(String issuer, String jwksUri) {
        TrustedIssuer trustedIssuer = new TrustedIssuer();
        trustedIssuer.setIssuer(issuer);
        trustedIssuer.setKeyResolutionMethod(KeyResolutionMethod.JWKS_URL);
        trustedIssuer.setJwksUri(jwksUri);
        return trustedIssuer;
    }

    private static UserBindingCriterion criterion(String attribute, String expression) {
        UserBindingCriterion criterion = new UserBindingCriterion();
        criterion.setAttribute(attribute);
        criterion.setExpression(expression);
        return criterion;
    }

    private static Domain domainWith(TrustedIssuer... issuers) {
        TokenExchangeSettings settings = new TokenExchangeSettings();
        settings.setTrustedIssuers(List.of(issuers));
        Domain domain = new Domain();
        domain.setId(DOMAIN_ID);
        domain.setTokenExchangeSettings(settings);
        return domain;
    }
}
