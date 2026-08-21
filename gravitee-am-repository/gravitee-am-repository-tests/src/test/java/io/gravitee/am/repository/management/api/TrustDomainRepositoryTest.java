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

import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.model.oidc.KeyMaterialSource;
import io.gravitee.am.model.oidc.TrustDomain;
import io.gravitee.am.model.oidc.TrustDomainKeyMaterial;
import io.gravitee.am.model.oidc.TrustDomainKind;
import io.gravitee.am.repository.management.AbstractManagementTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static java.util.UUID.randomUUID;

public class TrustDomainRepositoryTest extends AbstractManagementTest {

    private static final String PEM_CERTIFICATE = """
            -----BEGIN CERTIFICATE-----
            MIIBkTCB+wIJAKHHIG8lF4LlMA0GCSqGSIb3DQEBCwUAMBAxDjAMBgNVBAMMBXRl
            -----END CERTIFICATE-----""";

    @Autowired
    protected TrustDomainRepository repository;

    private TrustDomain buildTrustDomain(String referenceId, String name) {
        return buildTrustDomain(referenceId, TrustDomainKind.SPIFFE, name);
    }

    private TrustDomain buildTrustDomain(String referenceId, TrustDomainKind kind, String name) {
        TrustDomain td = new TrustDomain();
        td.setReferenceId(referenceId);
        td.setReferenceType(DOMAIN);
        td.setKind(kind);
        td.setName(name);
        td.setDescription("desc-" + name);
        td.setKeyMaterial(TrustDomainKeyMaterial.builder()
                .source(KeyMaterialSource.JWKS_URL)
                .jwksUrl("https://example.com/" + name + "/keys")
                .build());
        td.setRefreshIntervalSeconds(120);
        td.setAllowedAlgorithms(List.of("RS256", "ES256"));
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
        observer.assertValue(found -> found.getKind() == TrustDomainKind.SPIFFE);
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

        var observer = repository.findByName(DOMAIN, referenceId, TrustDomainKind.SPIFFE, "example.org").test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(found -> found.getId().equals(created.getId()));
    }

    @Test
    public void shouldNotFindByName_whenNameDiffers() {
        String referenceId = randomUUID().toString();
        repository.create(buildTrustDomain(referenceId, "example.org")).blockingGet();

        var observer = repository.findByName(DOMAIN, referenceId, TrustDomainKind.SPIFFE, "other.example").test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void shouldNotFindByName_whenReferenceDiffers() {
        String referenceId = randomUUID().toString();
        repository.create(buildTrustDomain(referenceId, "example.org")).blockingGet();

        var observer = repository.findByName(DOMAIN, randomUUID().toString(), TrustDomainKind.SPIFFE, "example.org").test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void shouldNotFindByName_whenKindDiffers() {
        String referenceId = randomUUID().toString();
        repository.create(buildTrustDomain(referenceId, TrustDomainKind.SPIFFE, "example.org")).blockingGet();

        var observer = repository.findByName(DOMAIN, referenceId, TrustDomainKind.TOKEN_EXCHANGE, "example.org").test();
        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoValues();
        observer.assertNoErrors();
    }

    @Test
    public void shouldShareNameAcrossKinds() {
        String referenceId = randomUUID().toString();
        var spiffe = repository.create(buildTrustDomain(referenceId, TrustDomainKind.SPIFFE, "example.org")).blockingGet();
        var tokenExchange = repository.create(buildTrustDomain(referenceId, TrustDomainKind.TOKEN_EXCHANGE, "example.org")).blockingGet();

        var spiffeObserver = repository.findByName(DOMAIN, referenceId, TrustDomainKind.SPIFFE, "example.org").test();
        spiffeObserver.awaitDone(10, TimeUnit.SECONDS);
        spiffeObserver.assertValue(found -> found.getId().equals(spiffe.getId()));

        var tokenExchangeObserver = repository.findByName(DOMAIN, referenceId, TrustDomainKind.TOKEN_EXCHANGE, "example.org").test();
        tokenExchangeObserver.awaitDone(10, TimeUnit.SECONDS);
        tokenExchangeObserver.assertValue(found -> found.getId().equals(tokenExchange.getId()));
    }

    @Test
    public void shouldRejectDuplicateNameWithinSameKind() {
        String referenceId = randomUUID().toString();
        repository.create(buildTrustDomain(referenceId, TrustDomainKind.SPIFFE, "example.org")).blockingGet();

        var observer = repository.create(buildTrustDomain(referenceId, TrustDomainKind.SPIFFE, "example.org")).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(Throwable.class);

        var stored = repository.findByReference(DOMAIN, referenceId).toList().test();
        stored.awaitDone(10, TimeUnit.SECONDS);
        stored.assertValue(list -> list.size() == 1);
    }

    @Test
    public void shouldRoundTripPemKeyMaterial() {
        String referenceId = randomUUID().toString();
        TrustDomain td = buildTrustDomain(referenceId, "pem.example");
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
        TrustDomain td = buildTrustDomain(referenceId, "jwks.example");
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

        TrustDomain toUpdate = new TrustDomain(created);
        toUpdate.setDescription("updated-description");
        toUpdate.getKeyMaterial().setJwksUrl("https://example.com/v2/keys");
        toUpdate.setRefreshIntervalSeconds(600);
        toUpdate.setAllowedAlgorithms(List.of("RS512"));
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
}
