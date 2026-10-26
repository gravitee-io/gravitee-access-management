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

import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.model.oauth2.AuthorizationDetailType;
import io.gravitee.am.model.oauth2.AuthorizationDetailTypeCursorRequest;
import io.gravitee.am.repository.exceptions.UniqueConstraintViolationException;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
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
    }

    @Test
    public void shouldUpdate() {
        AuthorizationDetailType created = repository.create(build(randomUUID().toString(), "payment_initiation")).blockingGet();
        created.setName("Renamed");
        created.setDiscovery(false);

        AuthorizationDetailType updated = repository.update(created).blockingGet();

        assertEquals("Renamed", updated.getName());
        assertFalse(repository.findById(created.getId()).blockingGet().isDiscovery());
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

        TestObserver<AuthorizationDetailType> observer = repository.create(build(domain, "Payment_INITIATION")).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertError(UniqueConstraintViolationException.class);
        assertEquals(1L, (long) repository.findByDomain(domain).count().blockingGet());
    }

    @Test
    public void shouldDelete() {
        AuthorizationDetailType created = repository.create(build(randomUUID().toString(), "payment_initiation")).blockingGet();

        repository.delete(created.getId()).blockingAwait();

        assertTrue(repository.findById(created.getId()).isEmpty().blockingGet());
    }

    @Test
    public void shouldDeleteByDomain() {
        String domain = randomUUID().toString();
        repository.create(build(domain, "payment_initiation")).blockingGet();
        repository.create(build(domain, "account_information")).blockingGet();
        AuthorizationDetailType other = repository.create(build(randomUUID().toString(), "payment_initiation")).blockingGet();

        TestObserver<Void> observer = repository.deleteByDomain(domain).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();

        assertEquals(0L, (long) repository.findByDomain(domain).count().blockingGet());
        assertFalse(repository.findById(other.getId()).isEmpty().blockingGet());
    }

    @Test
    public void shouldFindFirstPageOrderedByTypeIgnoringCase() {
        String domain = randomUUID().toString();
        createAll(domain, "files_access", "Payment_Status", "account_information");
        repository.create(build(randomUUID().toString(), "other_domain")).blockingGet();

        CursorPage<AuthorizationDetailType, AuthorizationDetailTypeCursorRequest> page = first(domain, "type", "ASC", null, 2);

        assertEquals(List.of("account_information", "files_access"), types(page));
        assertEquals(3L, (long) page.getTotalCount());
        assertTrue(page.isHasNext());
        assertEquals("files_access", page.getNextCursor().getLastSortValue());
        assertEquals(1, page.getNextCursor().getPage());
    }

    @Test
    public void shouldContinueFromTheCursor() {
        String domain = randomUUID().toString();
        createAll(domain, "files_access", "Payment_Status", "account_information");

        CursorPage<AuthorizationDetailType, AuthorizationDetailTypeCursorRequest> second =
                repository.findByDomainCursor(domain, first(domain, "type", "ASC", null, 2).getNextCursor(), 2).blockingGet();

        assertEquals(List.of("Payment_Status"), types(second));
        assertEquals(3L, (long) second.getTotalCount());
        assertFalse(second.isHasNext());
    }

    @Test
    public void shouldWalkEveryTypeOnceInDescendingOrder() {
        String domain = randomUUID().toString();
        createAll(domain, "type_a", "TYPE_B", "type_c", "Type_D", "type_e");

        assertEquals(List.of("type_e", "Type_D", "type_c", "TYPE_B", "type_a"), walkTypes(domain, "type", "DESC", null, 2));
    }

    @Test
    public void shouldSortByNameIgnoringCaseThenById() {
        String domain = randomUUID().toString();
        repository.create(build(domain, "type_1", "id-3", "bravo")).blockingGet();
        repository.create(build(domain, "type_2", "id-2", "ALPHA")).blockingGet();
        repository.create(build(domain, "type_3", "id-1", "alpha")).blockingGet();
        repository.create(build(domain, "type_4", "id-4", "Charlie")).blockingGet();

        assertEquals(List.of("type_3", "type_2", "type_1", "type_4"), walkTypes(domain, "name", "ASC", null, 1));
        assertEquals(List.of("type_4", "type_1", "type_2", "type_3"), walkTypes(domain, "name", "DESC", null, 1));
    }

    @Test
    public void shouldPageNonAsciiNamesInTheSameOrderAsASinglePage() {
        String domain = randomUUID().toString();
        List<String> names = List.of("Émile", "emile", "Straße", "STRASSE", "straße2", "Ångström", "zoë", "Zoe", "İstanbul", "istanbul");
        for (int i = 0; i < names.size(); i++) {
            repository.create(build(domain, "type_" + i, null, names.get(i))).blockingGet();
        }

        for (String direction : List.of("ASC", "DESC")) {
            List<String> singlePage = types(first(domain, "name", direction, null, 50));
            assertEquals(names.size(), Set.copyOf(singlePage).size());
            assertEquals(singlePage, walkTypes(domain, "name", direction, null, 1));
        }
    }

    @Test
    public void shouldSkipWholePagesOnTheFirstRequest() {
        String domain = randomUUID().toString();
        createAll(domain, "type_a", "type_b", "type_c");

        CursorPage<AuthorizationDetailType, AuthorizationDetailTypeCursorRequest> page = repository.findByDomainCursor(domain,
                AuthorizationDetailTypeCursorRequest.initialCursor("type", "ASC", 1, null), 2).blockingGet();

        assertEquals(List.of("type_c"), types(page));
        assertFalse(page.isHasNext());
    }

    @Test
    public void shouldFindEmptyPageForDomainWithoutTypes() {
        CursorPage<AuthorizationDetailType, AuthorizationDetailTypeCursorRequest> page = first(randomUUID().toString(), "type", "ASC", null, 50);

        assertTrue(page.getData().isEmpty());
        assertEquals(0L, (long) page.getTotalCount());
        assertFalse(page.isHasNext());
    }

    @Test
    public void shouldSearchExactTypeIgnoringCase() {
        String domain = randomUUID().toString();
        createAll(domain, "payment_initiation", "payment_status");
        repository.create(build(randomUUID().toString(), "payment_initiation")).blockingGet();

        CursorPage<AuthorizationDetailType, AuthorizationDetailTypeCursorRequest> page = first(domain, "type", "ASC", "PAYMENT_Initiation", 50);

        assertEquals(List.of("payment_initiation"), types(page));
        assertEquals(1L, (long) page.getTotalCount());
    }

    @Test
    public void shouldSearchWithWildcard() {
        String domain = randomUUID().toString();
        createAll(domain, "payment_initiation", "Payment_Status", "files_access", "my_payment");

        CursorPage<AuthorizationDetailType, AuthorizationDetailTypeCursorRequest> page = first(domain, "type", "ASC", "payment*", 50);

        assertEquals(List.of("payment_initiation", "Payment_Status"), types(page));
        assertEquals(2L, (long) page.getTotalCount());
    }

    @Test
    public void shouldSearchWithWildcardInTheMiddle() {
        String domain = randomUUID().toString();
        createAll(domain, "payment_initiation", "payment_status", "payment_initiation_v2");

        assertEquals(List.of("payment_initiation"), types(first(domain, "type", "ASC", "pay*initiation", 50)));
    }

    @Test
    public void shouldWalkSearchResultsWithTheCursor() {
        String domain = randomUUID().toString();
        createAll(domain, "payment_c", "payment_a", "payment_b", "files_access");

        assertEquals(List.of("payment_a", "payment_b", "payment_c"), walkTypes(domain, "type", "ASC", "payment*", 2));
    }

    @Test
    public void shouldTakeLikeAndRegexCharactersLiterally() {
        String domain = randomUUID().toString();
        createAll(domain, "payment_initiation", "paymentXinitiation", "a%b", "aXb", "a[b]", "ab", "a.b", "a\\b");

        assertEquals(List.of("payment_initiation"), types(first(domain, "type", "ASC", "payment_initiation", 50)));
        assertEquals(List.of("payment_initiation"), types(first(domain, "type", "ASC", "payment_*", 50)));
        assertEquals(List.of("a%b"), types(first(domain, "type", "ASC", "a%*", 50)));
        assertEquals(List.of("a[b]"), types(first(domain, "type", "ASC", "a[b]*", 50)));
        assertEquals(List.of("a.b"), types(first(domain, "type", "ASC", "a.*", 50)));
        assertEquals(List.of("a\\b"), types(first(domain, "type", "ASC", "a\\*", 50)));
    }

    private CursorPage<AuthorizationDetailType, AuthorizationDetailTypeCursorRequest> first(String domain, String sort, String direction, String query, int limit) {
        return repository.findByDomainCursor(domain, AuthorizationDetailTypeCursorRequest.initialCursor(sort, direction, 0, query), limit).blockingGet();
    }

    private List<String> walkTypes(String domain, String sort, String direction, String query, int limit) {
        List<String> seen = new ArrayList<>();
        CursorPage<AuthorizationDetailType, AuthorizationDetailTypeCursorRequest> page = first(domain, sort, direction, query, limit);
        addOnce(seen, types(page));
        while (page.isHasNext()) {
            page = repository.findByDomainCursor(domain, page.getNextCursor(), limit).blockingGet();
            addOnce(seen, types(page));
        }
        return seen;
    }

    private static void addOnce(List<String> seen, List<String> types) {
        for (String type : types) {
            assertFalse("listed twice: " + type + " after " + seen, seen.contains(type));
            seen.add(type);
        }
    }

    private void createAll(String domain, String... types) {
        for (String type : types) {
            repository.create(build(domain, type)).blockingGet();
        }
    }

    private static List<String> types(CursorPage<AuthorizationDetailType, AuthorizationDetailTypeCursorRequest> page) {
        return page.getData().stream().map(AuthorizationDetailType::getType).toList();
    }

    private static AuthorizationDetailType build(String domain, String type, String id, String name) {
        return build(domain, type).toBuilder().id(id).name(name).build();
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
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
