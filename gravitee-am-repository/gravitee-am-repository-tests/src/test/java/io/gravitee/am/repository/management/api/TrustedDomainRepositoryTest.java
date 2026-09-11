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
package io.gravitee.am.repository.management.api;

import io.gravitee.am.model.Reference;
import io.gravitee.am.model.UserBindingCriterion;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.model.oidc.CrossAppAccessResourceServer;
import io.gravitee.am.model.oidc.CrossAppAccessResourceServerView;
import io.gravitee.am.model.oidc.CrossAppAccessSettings;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.KeyMaterialSource;
import io.gravitee.am.model.oidc.TrustedDomain;
import io.gravitee.am.model.oidc.SpiffeTrustSettings;
import io.gravitee.am.model.oidc.TokenExchangeTrustSettings;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.gravitee.am.repository.management.AbstractManagementTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static java.util.UUID.randomUUID;

public class TrustedDomainRepositoryTest extends AbstractManagementTest {

    private static final String PEM_CERTIFICATE = """
            -----BEGIN CERTIFICATE-----
            MIIBkTCB+wIJAKHHIG8lF4LlMA0GCSqGSIb3DQEBCwUAMBAxDjAMBgNVBAMMBXRl
            -----END CERTIFICATE-----""";

    @Autowired
    protected TrustedDomainRepository repository;

    private TrustedDomain buildTrustDomain(String referenceId, String name) {
        TrustedDomain td = new TrustedDomain();
        td.setReferenceId(referenceId);
        td.setReferenceType(DOMAIN);
        td.setName(name);
        td.setDescription("desc-" + name);
        td.setKeyMaterial(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWKS_URL)
                .jwksUrl("https://example.com/" + name + "/keys")
                .refreshIntervalSeconds(120)
                .build());
        td.setSpiffe(SpiffeTrustSettings.builder()
                .spiffeTrustDomain(name)
                .allowedAlgorithms(List.of("RS256", "ES256"))
                .build());
        Date now = new Date();
        td.setCreatedAt(now);
        td.setUpdatedAt(now);
        return td;
    }

    private static JWKSet inlineJwkSet() {
        RSAKey key = new RSAKey();
        key.setKid("key-1");
        key.setAlg("RS256");
        key.setUse("sig");
        key.setE("AQAB");
        key.setN("0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86z");
        JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(List.of(key));
        return jwkSet;
    }

    @Test
    public void shouldFindById() {
        var created = repository.create(buildTrustDomain(randomUUID().toString(), "example.org")).blockingGet();

        var observer = repository.findById(created.getId()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(found -> found.getId().equals(created.getId()));
        observer.assertValue(found -> found.getName().equals("example.org"));
        observer.assertValue(found -> "example.org".equals(found.getSpiffeTrustDomain()));
        observer.assertValue(found -> found.getKeyMaterial().getSource() == KeyMaterialSource.JWKS_URL);
        observer.assertValue(found -> found.getKeyMaterial().getJwksUrl().equals(created.getKeyMaterial().getJwksUrl()));
        observer.assertValue(found -> found.getRefreshIntervalSeconds() == 120);
        observer.assertValue(found -> found.getAllowedAlgorithms() != null && found.getAllowedAlgorithms().contains("RS256"));
    }

    @Test
    public void shouldNotFindById() {
        var observer = repository.findById("missing").test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void shouldFindByReference() {
        String referenceId = randomUUID().toString();
        repository.create(buildTrustDomain(referenceId, "first.example")).blockingGet();
        repository.create(buildTrustDomain(referenceId, "second.example")).blockingGet();
        // unrelated reference — must not leak into results
        repository.create(buildTrustDomain(randomUUID().toString(), "other.example")).blockingGet();

        var observer = repository.findByReference(DOMAIN, referenceId).toList().test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValue(list -> list.size() == 2);
    }

    @Test
    public void shouldFindByName() {
        String referenceId = randomUUID().toString();
        var created = repository.create(buildTrustDomain(referenceId, "example.org")).blockingGet();

        var observer = repository.findByName(DOMAIN, referenceId, "example.org").test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(found -> found.getId().equals(created.getId()));
    }

    @Test
    public void shouldNotFindByName_whenNameDiffers() {
        String referenceId = randomUUID().toString();
        repository.create(buildTrustDomain(referenceId, "example.org")).blockingGet();

        var observer = repository.findByName(DOMAIN, referenceId, "other.example").test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void shouldNotFindByName_whenReferenceDiffers() {
        String referenceId = randomUUID().toString();
        repository.create(buildTrustDomain(referenceId, "example.org")).blockingGet();

        var observer = repository.findByName(DOMAIN, randomUUID().toString(), "example.org").test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void shouldFindBySpiffeTrustDomain() {
        String referenceId = randomUUID().toString();
        var created = repository.create(buildTrustDomain(referenceId, "example.org")).blockingGet();

        var observer = repository.findBySpiffeTrustDomain(DOMAIN, referenceId, "example.org").test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(found -> found.getId().equals(created.getId()));
    }

    @Test
    public void shouldNotFindBySpiffeTrustDomain_whenTrustedForTokenExchangeOnly() {
        String referenceId = randomUUID().toString();
        repository.create(buildTokenExchangeTrustDomain(referenceId, "https://issuer.example/realm")).blockingGet();

        var observer = repository.findBySpiffeTrustDomain(DOMAIN, referenceId, "issuer.example").test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void shouldServeBothUsagesFromOneTrustedDomain() {
        String referenceId = randomUUID().toString();
        TrustedDomain both = buildTrustDomain(referenceId, "acme-corp");
        both.setSpiffe(SpiffeTrustSettings.builder().spiffeTrustDomain("acme.org").build());
        both.setDomainIdentifier("https://sso.acme.com");
        var created = repository.create(both).blockingGet();

        var bySpiffe = repository.findBySpiffeTrustDomain(DOMAIN, referenceId, "acme.org").test();
        bySpiffe.awaitDone(10, TimeUnit.SECONDS);
        bySpiffe.assertValue(found -> found.getId().equals(created.getId()));

        var byIssuer = repository.findByIssuer(DOMAIN, referenceId, "https://sso.acme.com").test();
        byIssuer.awaitDone(10, TimeUnit.SECONDS);
        byIssuer.assertValue(found -> found.getId().equals(created.getId()));
    }

    @Test
    public void shouldRejectDuplicateSpiffeTrustDomain() {
        String referenceId = randomUUID().toString();
        repository.create(buildTrustDomain(referenceId, "example.org")).blockingGet();

        TrustedDomain duplicate = buildTrustDomain(referenceId, "other-label");
        duplicate.setSpiffe(SpiffeTrustSettings.builder().spiffeTrustDomain("example.org").build());

        var observer = repository.create(duplicate).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(Throwable.class);

        var stored = repository.findByReference(DOMAIN, referenceId).toList().test();
        stored.awaitDone(10, TimeUnit.SECONDS);
        stored.assertValue(list -> list.size() == 1);
    }

    @Test
    public void shouldRejectDuplicateName() {
        String referenceId = randomUUID().toString();
        repository.create(buildTrustDomain(referenceId, "example.org")).blockingGet();

        var observer = repository.create(buildTrustDomain(referenceId, "example.org")).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(Throwable.class);

        var stored = repository.findByReference(DOMAIN, referenceId).toList().test();
        stored.awaitDone(10, TimeUnit.SECONDS);
        stored.assertValue(list -> list.size() == 1);
    }

    @Test
    public void shouldRoundTripPemKeyMaterial() {
        String referenceId = randomUUID().toString();
        TrustedDomain td = buildTrustDomain(referenceId, "pem.example");
        td.setKeyMaterial(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.PEM)
                .certificate(PEM_CERTIFICATE)
                .build());
        var created = repository.create(td).blockingGet();

        var observer = repository.findById(created.getId()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValue(found -> found.getKeyMaterial().getSource() == KeyMaterialSource.PEM);
        observer.assertValue(found -> PEM_CERTIFICATE.equals(found.getKeyMaterial().getCertificate()));
        observer.assertValue(found -> found.getKeyMaterial().getJwksUrl() == null);
    }

    @Test
    public void shouldRoundTripInlineJwkSet() {
        String referenceId = randomUUID().toString();
        TrustedDomain td = buildTrustDomain(referenceId, "jwks.example");
        td.setKeyMaterial(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWK_SET)
                .jwkSet(inlineJwkSet())
                .build());
        var created = repository.create(td).blockingGet();

        var observer = repository.findById(created.getId()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValue(found -> found.getKeyMaterial().getSource() == KeyMaterialSource.JWK_SET);
        observer.assertValue(found -> found.getKeyMaterial().getJwkSet().getKeys().size() == 1);
        observer.assertValue(found -> {
            RSAKey key = (RSAKey) found.getKeyMaterial().getJwkSet().getKeys().get(0);
            return "key-1".equals(key.getKid()) && "AQAB".equals(key.getE()) && "RSA".equals(key.getKty());
        });
    }

    @Test
    public void shouldUpdate() {
        String referenceId = randomUUID().toString();
        var created = repository.create(buildTrustDomain(referenceId, "example.org")).blockingGet();

        TrustedDomain toUpdate = new TrustedDomain(created);
        toUpdate.setDescription("updated-description");
        toUpdate.getKeyMaterial().setJwksUrl("https://example.com/v2/keys");
        toUpdate.getKeyMaterial().setRefreshIntervalSeconds(600);
        toUpdate.getSpiffe().setAllowedAlgorithms(List.of("RS512"));
        toUpdate.setUpdatedAt(new Date());

        var observer = repository.update(toUpdate).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValue(found -> found.getDescription().equals("updated-description"));
        observer.assertValue(found -> found.getKeyMaterial().getJwksUrl().equals("https://example.com/v2/keys"));
        observer.assertValue(found -> found.getRefreshIntervalSeconds() == 600);
        observer.assertValue(found -> found.getAllowedAlgorithms().equals(List.of("RS512")));
    }

    @Test
    public void shouldDelete() {
        String referenceId = randomUUID().toString();
        var created = repository.create(buildTrustDomain(referenceId, "example.org")).blockingGet();

        var deleteObserver = repository.delete(created.getId()).test();
        deleteObserver.awaitDone(10, TimeUnit.SECONDS);
        deleteObserver.assertNoErrors();

        var findObserver = repository.findById(created.getId()).test();
        findObserver.awaitDone(5, TimeUnit.SECONDS);
        findObserver.assertComplete();
        findObserver.assertNoValues();
        findObserver.assertNoErrors();
    }

    private TrustedDomain buildTokenExchangeTrustDomain(String referenceId, String issuer) {
        TrustedDomain td = buildTrustDomain(referenceId, "issuer.example");
        td.setSpiffe(null);
        UserBindingCriterion criterion = new UserBindingCriterion();
        criterion.setAttribute("emails.value");
        criterion.setExpression("{#token['email']}");
        td.setDomainIdentifier(issuer);
        td.setTokenExchange(TokenExchangeTrustSettings.builder()
                .enabled(true)
                .scopeMappings(Map.of("read", "domain:read"))
                .userBindingEnabled(true)
                .userBindingCriteria(List.of(criterion))
                .build());
        return td;
    }

    @Test
    public void shouldRoundTripTokenExchangeSettings() {
        String referenceId = randomUUID().toString();
        var created = repository.create(buildTokenExchangeTrustDomain(referenceId, "https://issuer.example/realm")).blockingGet();

        var observer = repository.findById(created.getId()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValue(found -> found.getSpiffeTrustDomain() == null);
        observer.assertValue(found -> "https://issuer.example/realm".equals(found.getDomainIdentifier()));
        observer.assertValue(found -> Map.of("read", "domain:read").equals(found.getScopeMappings()));
        observer.assertValue(found -> found.isUserBindingEnabled());
        observer.assertValue(found -> found.getUserBindingCriteria().size() == 1);
        observer.assertValue(found -> "emails.value".equals(found.getUserBindingCriteria().get(0).getAttribute()));
        observer.assertValue(found -> "{#token['email']}".equals(found.getUserBindingCriteria().get(0).getExpression()));
    }

    @Test
    public void shouldRoundTripCrossAppAccessSettings() {
        String referenceId = randomUUID().toString();
        String resourceServerId = randomUUID().toString();
        TrustedDomain td = buildTokenExchangeTrustDomain(referenceId, "https://issuer.example/realm");
        td.setCrossAppAccess(CrossAppAccessSettings.builder()
                .enabled(true)
                .resourceServers(List.of(CrossAppAccessResourceServer.builder()
                        .id(resourceServerId)
                        .name("Calendar")
                        .resource("https://calendar.acme.com")
                        .build()))
                .audSubMapping("{#user.email}")
                .scopeMappings(Map.of("domain:read", "calendar.read"))
                .build());
        var created = repository.create(td).blockingGet();

        var observer = repository.findById(created.getId()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValue(found -> found.getCrossAppAccess().isEnabled());
        observer.assertValue(found -> found.getCrossAppAccess().getResourceServers().size() == 1);
        observer.assertValue(found -> resourceServerId.equals(found.getCrossAppAccess().getResourceServers().get(0).getId()));
        observer.assertValue(found -> "Calendar".equals(found.getCrossAppAccess().getResourceServers().get(0).getName()));
        observer.assertValue(found -> "https://calendar.acme.com".equals(found.getCrossAppAccess().getResourceServers().get(0).getResource()));
        observer.assertValue(found -> "{#user.email}".equals(found.getCrossAppAccess().getAudSubMapping()));
        observer.assertValue(found -> Map.of("domain:read", "calendar.read").equals(found.getCrossAppAccess().getScopeMappings()));
    }

    @Test
    public void shouldReplaceResourceServersOnUpdate() {
        String referenceId = randomUUID().toString();
        String calendarId = randomUUID().toString();
        String mailId = randomUUID().toString();
        TrustedDomain td = buildTokenExchangeTrustDomain(referenceId, "https://issuer.example/resource-servers");
        td.setCrossAppAccess(CrossAppAccessSettings.builder()
                .enabled(true)
                .resourceServers(List.of(
                        CrossAppAccessResourceServer.builder().id(calendarId).name("Calendar").resource("https://calendar.acme.com").build(),
                        CrossAppAccessResourceServer.builder().id(mailId).name("Mail").resource("https://mail.acme.com").build()))
                .build());
        var created = repository.create(td).blockingGet();

        TrustedDomain toUpdate = new TrustedDomain(created);
        toUpdate.getCrossAppAccess().setResourceServers(List.of(
                CrossAppAccessResourceServer.builder().id(mailId).name("Mail").resource("https://mail.acme.com/v2").build()));
        toUpdate.setUpdatedAt(new Date());
        repository.update(toUpdate).blockingGet();

        var observer = repository.findById(created.getId()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValue(found -> found.crossAppAccessResourceServers().size() == 1);
        observer.assertValue(found -> mailId.equals(found.crossAppAccessResourceServers().get(0).getId()));
        observer.assertValue(found -> "https://mail.acme.com/v2".equals(found.crossAppAccessResourceServers().get(0).getResource()));
    }

    @Test
    public void shouldReadBackADisabledCrossAppAccessBlock() {
        String referenceId = randomUUID().toString();
        TrustedDomain td = buildTokenExchangeTrustDomain(referenceId, "https://issuer.example/xaa-off");
        td.setCrossAppAccess(CrossAppAccessSettings.builder().enabled(false).build());
        var created = repository.create(td).blockingGet();

        var observer = repository.findById(created.getId()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValue(found -> found.getCrossAppAccess() != null);
        observer.assertValue(found -> !found.trustsCrossAppAccess());
        observer.assertValue(found -> found.crossAppAccessResourceServers().isEmpty());
    }

    @Test
    public void shouldDeleteResourceServersAlongTheTrustedDomain() {
        String referenceId = randomUUID().toString();
        String recreatedResourceServerId = randomUUID().toString();
        TrustedDomain td = buildTokenExchangeTrustDomain(referenceId, "https://issuer.example/deleted");
        td.setCrossAppAccess(CrossAppAccessSettings.builder()
                .enabled(true)
                .resourceServers(List.of(CrossAppAccessResourceServer.builder()
                        .id(randomUUID().toString())
                        .name("Calendar")
                        .resource("https://calendar.acme.com")
                        .build()))
                .build());
        var created = repository.create(td).blockingGet();
        repository.delete(created.getId()).blockingAwait();

        TrustedDomain recreated = buildTokenExchangeTrustDomain(referenceId, "https://issuer.example/deleted");
        recreated.setId(created.getId());
        recreated.setCrossAppAccess(CrossAppAccessSettings.builder()
                .enabled(true)
                .resourceServers(List.of(CrossAppAccessResourceServer.builder()
                        .id(recreatedResourceServerId)
                        .name("Mail")
                        .resource("https://mail.acme.com")
                        .build()))
                .build());
        repository.create(recreated).blockingGet();

        var observer = repository.findById(created.getId()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValue(found -> found.crossAppAccessResourceServers().size() == 1);
        observer.assertValue(found -> recreatedResourceServerId.equals(found.crossAppAccessResourceServers().get(0).getId()));
    }

    @Test
    public void shouldNotReadACrossAppAccessOnlyTrustedDomainBackAsSpiffeOrTokenExchange() {
        String referenceId = randomUUID().toString();
        TrustedDomain td = buildTrustDomain(referenceId, "acme-corp");
        td.setSpiffe(null);
        td.setDomainIdentifier("https://auth.acme.com");
        td.setKeyMaterial(null);
        td.setCrossAppAccess(CrossAppAccessSettings.builder()
                .enabled(true)
                .resourceServers(List.of(CrossAppAccessResourceServer.builder()
                        .id(randomUUID().toString())
                        .name("Calendar")
                        .resource("https://calendar.acme.com")
                        .build()))
                .build());
        var created = repository.create(td).blockingGet();

        var observer = repository.findById(created.getId()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValue(found -> found.getSpiffeTrustDomain() == null);
        observer.assertValue(found -> "https://auth.acme.com".equals(found.getDomainIdentifier()));
        observer.assertValue(found -> found.getTokenExchange() == null);
        observer.assertValue(found -> !found.trustsTokenExchange());
        observer.assertValue(found -> found.trustsCrossAppAccess());
    }

    @Test
    public void shouldReadBackAnExistingTrustedDomainWithoutACrossAppAccessBlock() {
        String referenceId = randomUUID().toString();
        var created = repository.create(buildTokenExchangeTrustDomain(referenceId, "https://issuer.example/realm")).blockingGet();

        var observer = repository.findById(created.getId()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        observer.assertValue(found -> found.getCrossAppAccess() == null);
        observer.assertValue(found -> !found.trustsCrossAppAccess());
    }

    @Test
    public void shouldFindByIssuer() {
        String referenceId = randomUUID().toString();
        var created = repository.create(buildTokenExchangeTrustDomain(referenceId, "https://issuer.example/realm")).blockingGet();

        var observer = repository.findByIssuer(DOMAIN, referenceId, "https://issuer.example/realm").test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(found -> found.getId().equals(created.getId()));
    }

    @Test
    public void shouldNotFindByIssuer_whenReferenceDiffers() {
        String referenceId = randomUUID().toString();
        repository.create(buildTokenExchangeTrustDomain(referenceId, "https://issuer.example/realm")).blockingGet();

        var observer = repository.findByIssuer(DOMAIN, randomUUID().toString(), "https://issuer.example/realm").test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void shouldRejectDuplicateIssuerWithinReference() {
        String referenceId = randomUUID().toString();
        repository.create(buildTokenExchangeTrustDomain(referenceId, "https://issuer.example/realm")).blockingGet();

        TrustedDomain duplicate = buildTokenExchangeTrustDomain(referenceId, "https://issuer.example/realm");
        duplicate.setName("other.example");
        var observer = repository.create(duplicate).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(Throwable.class);

        var stored = repository.findByReference(DOMAIN, referenceId).toList().test();
        stored.awaitDone(10, TimeUnit.SECONDS);
        stored.assertValue(list -> list.size() == 1);
    }

    @Test
    public void shouldAllowManySpiffeTrustDomainsWithoutIssuer() {
        String referenceId = randomUUID().toString();
        repository.create(buildTrustDomain(referenceId, "first.example")).blockingGet();
        repository.create(buildTrustDomain(referenceId, "second.example")).blockingGet();

        var stored = repository.findByReference(DOMAIN, referenceId).toList().test();
        stored.awaitDone(10, TimeUnit.SECONDS);
        stored.assertNoErrors();
        stored.assertValue(list -> list.size() == 2);
    }

    @Test
    public void shouldUpdateIssuer() {
        String referenceId = randomUUID().toString();
        var created = repository.create(buildTokenExchangeTrustDomain(referenceId, "https://issuer.example/realm")).blockingGet();

        TrustedDomain toUpdate = new TrustedDomain(created);
        toUpdate.setDomainIdentifier("https://issuer.example/realm-v2");
        toUpdate.setUpdatedAt(new Date());
        repository.update(toUpdate).blockingGet();

        var observer = repository.findByIssuer(DOMAIN, referenceId, "https://issuer.example/realm-v2").test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertValue(found -> found.getId().equals(created.getId()));

        var stale = repository.findByIssuer(DOMAIN, referenceId, "https://issuer.example/realm").test();
        stale.awaitDone(5, TimeUnit.SECONDS);
        stale.assertNoValues();
    }

    private TrustedDomain buildCrossAppAccessTrustDomain(String referenceId, String name, boolean enabled,
                                                         CrossAppAccessResourceServer... resourceServers) {
        TrustedDomain td = buildTrustDomain(referenceId, name);
        td.setSpiffe(null);
        td.setDomainIdentifier("https://" + name + "/");
        td.setCrossAppAccess(CrossAppAccessSettings.builder()
                .enabled(enabled)
                .resourceServers(List.of(resourceServers))
                .build());
        return td;
    }

    private static CrossAppAccessResourceServer resourceServer(String name, String resource) {
        return CrossAppAccessResourceServer.builder()
                .id(randomUUID().toString())
                .name(name)
                .resource(resource)
                .build();
    }

    private String seedCrossAppAccessResourceServers(String referenceId) {
        repository.create(buildCrossAppAccessTrustDomain(referenceId, "acme", true,
                resourceServer("Calendar", "https://calendar.acme.com"),
                resourceServer("Files", "https://files.acme.com"))).blockingGet();
        repository.create(buildCrossAppAccessTrustDomain(referenceId, "globex", true,
                resourceServer("Mail", "https://mail.globex.com"))).blockingGet();
        return referenceId;
    }

    private List<String> searchResourceServerNames(String referenceId, String query, int limit) {
        var observer = repository.searchCrossAppAccessResourceServers(Reference.domain(referenceId), query, limit).toList().test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        return observer.values().get(0).stream().map(CrossAppAccessResourceServerView::name).toList();
    }

    @Test
    public void shouldSearchResourceServersAcrossTrustedDomainsOrderedByName() {
        String referenceId = seedCrossAppAccessResourceServers(randomUUID().toString());

        assertEquals(List.of("Calendar", "Files", "Mail"), searchResourceServerNames(referenceId, "", 10));
    }

    @Test
    public void shouldFlattenTheTrustedDomainOntoEveryResourceServer() {
        String referenceId = seedCrossAppAccessResourceServers(randomUUID().toString());

        var observer = repository.searchCrossAppAccessResourceServers(Reference.domain(referenceId), "Mail", 10).toList().test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();
        CrossAppAccessResourceServerView found = observer.values().get(0).get(0);
        assertEquals("globex", found.trustedDomainName());
        assertEquals("https://mail.globex.com", found.resource());
        assertNotNull(found.trustedDomainId());
        assertNotNull(found.id());
    }

    @Test
    public void shouldMatchTheSearchTermAnywhereAndIgnoreCase() {
        String referenceId = seedCrossAppAccessResourceServers(randomUUID().toString());

        assertEquals(List.of("Mail"), searchResourceServerNames(referenceId, "mAiL", 10));
        assertEquals(List.of("Files"), searchResourceServerNames(referenceId, "//FILES", 10));
        assertEquals(List.of("Calendar", "Files"), searchResourceServerNames(referenceId, "ACME", 10));
    }

    @Test
    public void shouldSkipResourceServersOfTrustedDomainsWithCrossAppAccessDisabled() {
        String referenceId = randomUUID().toString();
        repository.create(buildCrossAppAccessTrustDomain(referenceId, "acme", false,
                resourceServer("Calendar", "https://calendar.acme.com"))).blockingGet();
        repository.create(buildCrossAppAccessTrustDomain(referenceId, "globex", true,
                resourceServer("Mail", "https://mail.globex.com"))).blockingGet();

        assertEquals(List.of("Mail"), searchResourceServerNames(referenceId, "", 10));
    }

    @Test
    public void shouldSkipTrustedDomainsWithoutCrossAppAccess() {
        String referenceId = randomUUID().toString();
        repository.create(buildTrustDomain(referenceId, "acme")).blockingGet();

        assertEquals(List.of(), searchResourceServerNames(referenceId, "", 10));
    }

    @Test
    public void shouldReturnAtMostTheRequestedLimit() {
        String referenceId = seedCrossAppAccessResourceServers(randomUUID().toString());

        assertEquals(List.of("Calendar", "Files"), searchResourceServerNames(referenceId, "", 2));
    }

    @Test
    public void shouldIgnoreResourceServersOfAnotherReference() {
        String referenceId = seedCrossAppAccessResourceServers(randomUUID().toString());
        seedCrossAppAccessResourceServers(randomUUID().toString());

        assertEquals(3, searchResourceServerNames(referenceId, "", 10).size());
    }
}
