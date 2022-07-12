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

import io.gravitee.am.model.I18nDictionary;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static java.util.UUID.randomUUID;

public class I18nDictionaryRepositoryTest extends AbstractManagementTest {

    private static final String NAME = "test";
    @Autowired
    protected I18nDictionaryRepository repository;

    protected I18nDictionary buildDictionary(String referenceId) {
        var dictionary = new I18nDictionary();
        dictionary.setReferenceId(referenceId);
        dictionary.setReferenceType(DOMAIN);
        dictionary.setLocale("fr");
        dictionary.setCreatedAt(new Date());
        dictionary.setUpdatedAt(dictionary.getCreatedAt());
        dictionary.setEntries(new TreeMap<>());
        dictionary.setName(NAME);
        return dictionary;
    }

    @Test
    public void shouldFindById() {
        var created = repository.create(buildDictionary(randomUUID().toString())).blockingGet();
        var observer = repository.findById(created.getId()).test();
        observer.awaitTerminalEvent();
        observer.assertComplete();
        observer.assertNoErrors();

        assertObservedValues(created, observer);
    }

    @Test
    public void shouldFindByReferenceAndId() {
        String referenceId = randomUUID().toString();
        var created = repository.create(buildDictionary(referenceId)).blockingGet();
        var observer = repository.findById(DOMAIN, referenceId, created.getId()).test();
        observer.awaitTerminalEvent();
        observer.assertComplete();
        observer.assertNoErrors();

        assertObservedValues(created, observer);
    }

    @Test
    public void shouldFindByName() {
        String referenceId = randomUUID().toString();
        var created = repository.create(buildDictionary(referenceId)).blockingGet();
        var observer = repository.findByName(DOMAIN, referenceId, created.getName()).test();
        observer.awaitTerminalEvent();
        observer.assertComplete();
        observer.assertNoErrors();

        assertObservedValues(created, observer);
    }

    @Test
    public void shouldNotFindById() {
        repository.findById("1234").test().assertEmpty();
    }

    @Test
    public void shouldNotFindByReferenceAndId() {
        repository.findById(DOMAIN, "1234", "5678").test().assertEmpty();
    }

    @Test
    public void shouldNotFindByName() {
        repository.findByName(DOMAIN, "1234", "no").test().assertEmpty();
    }

    @Test
    public void shouldFindAllByReferenceId() {
        String referenceId = randomUUID().toString();
        final int dictCount = 5;
        for (int i = 0; i < dictCount; i++) {
            //noinspection ResultOfMethodCallIgnored
            repository.create(buildDictionary(referenceId)).blockingGet().getId();
        }

        var observer = repository.findAll(DOMAIN, referenceId).toList().test();

        observer.awaitTerminalEvent();
        observer.assertNoErrors();
        observer.assertValue(l -> l.size() == dictCount);
    }

    @Test
    public void shouldUpdate() {
        String referenceId = randomUUID().toString();
        var created = repository.create(buildDictionary(referenceId)).blockingGet();

        var findObserver = repository.findById(created.getId()).test();
        findObserver.awaitTerminalEvent();
        findObserver.assertNoErrors();
        findObserver.assertComplete();
        findObserver.assertValue(i18nDictionary -> i18nDictionary.getId().equals(created.getId()));

        var toUpdate = buildDictionary(referenceId);
        toUpdate.setId(created.getId());
        String updated = "Updated";
        toUpdate.setName(updated);
        Date updatedAt = new Date();
        toUpdate.setUpdatedAt(updatedAt);
        String locale = "can";
        toUpdate.setLocale(locale);
        toUpdate.setEntries(Map.of("key1", "val1", "key2", "val2"));

        var updateObserver = repository.update(toUpdate).test();
        updateObserver.awaitTerminalEvent();
        updateObserver.assertComplete();
        assertObservedValues(toUpdate, updateObserver);
    }

    @Test
    public void shouldDelete() {
        String referenceId = randomUUID().toString();
        var created = repository.create(buildDictionary(referenceId)).blockingGet();

        var observer = repository.delete(created.getId()).test();
        observer.awaitTerminalEvent();
        observer.assertNoErrors();

        repository.findById(created.getId()).test().assertEmpty();
    }

    private void assertObservedValues(I18nDictionary created, TestObserver<I18nDictionary> observer) {
        observer.assertValue(found -> found.getId().equals(created.getId()));
        observer.assertValue(found -> found.getName().equals(created.getName()));
        observer.assertValue(found -> found.getLocale().equals(created.getLocale()));
        observer.assertValue(found -> found.getReferenceType().equals(created.getReferenceType()));
        observer.assertValue(found -> found.getEntries().equals(created.getEntries()));
    }
}
