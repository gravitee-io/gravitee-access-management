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

import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.model.oauth2.AuthorizationDetailTypeVersion;
import io.gravitee.am.repository.exceptions.UniqueConstraintViolationException;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import static java.util.UUID.randomUUID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author GraviteeSource Team
 */
public class AuthorizationDetailTypeRepositoryTest extends AbstractManagementTest {

    private static final String SCHEMA = "{\"type\" : \"object\",\n  \"properties\":{\"type\":{\"const\":\"payment_initiation\"}},\"required\":[\"type\"], \"x-custom\": true}";

    @Autowired
    private AuthorizationDetailTypeRepository repository;

    @Test
    public void shouldCreateAndFindById() {
        String domain = randomUUID().toString();
        AuthorizationDetailType created = repository.create(build(domain, "payment_initiation")).blockingGet();

        assertNotNull(created.getId());
        TestObserver<AuthorizationDetailType> observer = repository.findById(created.getId()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(found -> found.getId().equals(created.getId())
                && found.getDomain().equals(domain)
                && found.getType().equals("payment_initiation")
                && found.getName().equals("Payment initiation")
                && found.getDescription().equals("Initiates a payment")
                && SCHEMA.equals(found.getSchema())
                && found.isDiscovery()
                && found.getExpiresIn() == 3600
                && found.getVersion() == 1
                && found.getCreatedAt() != null
                && found.getUpdatedAt() != null);
    }

    @Test
    public void shouldCreateWithoutOptionalFields() {
        AuthorizationDetailType type = build(randomUUID().toString(), "https://scheme.example.org/files");
        type.setDescription(null);
        type.setSchema(null);
        type.setDiscovery(false);
        type.setExpiresIn(null);
        AuthorizationDetailType created = repository.create(type).blockingGet();

        AuthorizationDetailType found = repository.findById(created.getId()).blockingGet();
        assertEquals("https://scheme.example.org/files", found.getType());
        assertEquals(null, found.getDescription());
        assertEquals(null, found.getSchema());
        assertFalse(found.isDiscovery());
        assertEquals(null, found.getExpiresIn());

        AuthorizationDetailTypeVersion version = repository.findVersion(created.getId(), 1).blockingGet();
        assertEquals(null, version.description());
        assertEquals(null, version.schema());
    }

    @Test
    public void shouldStoreTheCreatedContentAsItsVersion() {
        String domain = randomUUID().toString();
        AuthorizationDetailType created = repository.create(build(domain, "payment_initiation")).blockingGet();

        TestObserver<AuthorizationDetailTypeVersion> observer = repository.findVersion(created.getId(), 1).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(version -> version.authorizationDetailTypeId().equals(created.getId())
                && version.version() == 1
                && version.domain().equals(domain)
                && version.name().equals("Payment initiation")
                && version.description().equals("Initiates a payment")
                && SCHEMA.equals(version.schema())
                && version.createdAt() != null);
    }

    @Test
    public void shouldNotFindAVersionThatWasNeverStored() {
        AuthorizationDetailType created = repository.create(build(randomUUID().toString(), "payment_initiation")).blockingGet();

        TestObserver<AuthorizationDetailTypeVersion> observer = repository.findVersion(created.getId(), 2).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoValues();
    }

    @Test
    public void shouldRejectASecondCopyOfTheSameVersion() {
        String domain = randomUUID().toString();
        AuthorizationDetailType first = repository.create(build(domain, "payment_initiation")).blockingGet();
        AuthorizationDetailType second = build(domain, "payment_initiation");
        second.setId(first.getId());
        second.setName("Overwritten");

        TestObserver<AuthorizationDetailType> observer = repository.create(second).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertError(Exception.class);
        assertEquals("Payment initiation", repository.findVersion(first.getId(), 1).blockingGet().name());
    }

    @Test
    public void shouldUpdate() {
        AuthorizationDetailType created = repository.create(build(randomUUID().toString(), "payment_initiation")).blockingGet();
        created.setName("Renamed");
        created.setDiscovery(false);

        AuthorizationDetailType updated = repository.update(created).blockingGet();

        assertEquals("Renamed", updated.getName());
        assertFalse(repository.findById(created.getId()).blockingGet().isDiscovery());
        assertEquals("Payment initiation", repository.findVersion(created.getId(), 1).blockingGet().name());
    }

    @Test
    public void shouldFindByDomain() {
        String domain = randomUUID().toString();
        repository.create(build(domain, "payment_initiation")).blockingGet();
        repository.create(build(domain, "account_information")).blockingGet();
        repository.create(build(randomUUID().toString(), "payment_initiation")).blockingGet();

        TestSubscriber<AuthorizationDetailType> subscriber = repository.findByDomain(domain).test();
        subscriber.awaitDone(10, TimeUnit.SECONDS);

        subscriber.assertComplete();
        subscriber.assertNoErrors();
        subscriber.assertValueCount(2);
    }

    @Test
    public void shouldFindByDomainAndId_onlyInItsDomain() {
        String domain = randomUUID().toString();
        AuthorizationDetailType created = repository.create(build(domain, "payment_initiation")).blockingGet();

        TestObserver<AuthorizationDetailType> sameDomain = repository.findByDomainAndId(domain, created.getId()).test();
        sameDomain.awaitDone(10, TimeUnit.SECONDS);
        sameDomain.assertValue(found -> found.getId().equals(created.getId()));

        TestObserver<AuthorizationDetailType> otherDomain = repository.findByDomainAndId(randomUUID().toString(), created.getId()).test();
        otherDomain.awaitDone(10, TimeUnit.SECONDS);
        otherDomain.assertComplete();
        otherDomain.assertNoValues();
    }

    @Test
    public void shouldFindByDomainAndType_ignoringCase() {
        String domain = randomUUID().toString();
        AuthorizationDetailType created = repository.create(build(domain, "payment_initiation")).blockingGet();

        TestObserver<AuthorizationDetailType> observer = repository.findByDomainAndType(domain, "Payment_INITIATION").test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(found -> found.getId().equals(created.getId()));
    }

    @Test
    public void shouldNotFindByDomainAndType_inAnotherDomain() {
        repository.create(build(randomUUID().toString(), "payment_initiation")).blockingGet();

        TestObserver<AuthorizationDetailType> observer = repository.findByDomainAndType(randomUUID().toString(), "payment_initiation").test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoValues();
    }

    @Test
    public void shouldAllowSameTypeInTwoDomains() {
        repository.create(build(randomUUID().toString(), "payment_initiation")).blockingGet();

        TestObserver<AuthorizationDetailType> observer = repository.create(build(randomUUID().toString(), "payment_initiation")).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
    }

    @Test
    public void shouldRejectTheSameTypeInTheSameDomain() {
        String domain = randomUUID().toString();
        repository.create(build(domain, "payment_initiation")).blockingGet();

        TestObserver<AuthorizationDetailType> observer = repository.create(build(domain, "payment_initiation")).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertError(UniqueConstraintViolationException.class);
        assertEquals(1L, (long) repository.findByDomain(domain).count().blockingGet());
    }

    @Test
    public void shouldRejectTheSameTypeIgnoringCase() {
        String domain = randomUUID().toString();
        repository.create(build(domain, "payment_initiation")).blockingGet();
        AuthorizationDetailType variant = build(domain, "Payment_INITIATION");

        TestObserver<AuthorizationDetailType> observer = repository.create(variant).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertError(UniqueConstraintViolationException.class);
        assertEquals(1L, (long) repository.findByDomain(domain).count().blockingGet());
        assertTrue(repository.findVersion(variant.getId(), 1).isEmpty().blockingGet());
    }

    @Test
    public void shouldDelete() {
        AuthorizationDetailType created = repository.create(build(randomUUID().toString(), "payment_initiation")).blockingGet();

        repository.delete(created.getId()).blockingAwait();

        assertTrue(repository.findById(created.getId()).isEmpty().blockingGet());
        assertTrue(repository.findVersion(created.getId(), 1).isEmpty().blockingGet());
    }

    @Test
    public void shouldDeleteByDomain() {
        String domain = randomUUID().toString();
        AuthorizationDetailType deleted = repository.create(build(domain, "payment_initiation")).blockingGet();
        repository.create(build(domain, "account_information")).blockingGet();
        AuthorizationDetailType other = repository.create(build(randomUUID().toString(), "payment_initiation")).blockingGet();

        TestObserver<Void> observer = repository.deleteByDomain(domain).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();

        assertEquals(0L, (long) repository.findByDomain(domain).count().blockingGet());
        assertTrue(repository.findVersion(deleted.getId(), 1).isEmpty().blockingGet());
        assertFalse(repository.findById(other.getId()).isEmpty().blockingGet());
        assertFalse(repository.findVersion(other.getId(), 1).isEmpty().blockingGet());
    }

    private static AuthorizationDetailType build(String domain, String type) {
        Date now = new Date();
        return AuthorizationDetailType.builder()
                .domain(domain)
                .type(type)
                .name("Payment initiation")
                .description("Initiates a payment")
                .schema(SCHEMA)
                .discovery(true)
                .expiresIn(3600)
                .version(1)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
