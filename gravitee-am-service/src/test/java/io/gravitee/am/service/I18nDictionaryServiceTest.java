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
package io.gravitee.am.service;

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.I18nDictionary;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.management.api.I18nDictionaryRepository;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.I18nDictionaryService;
import io.gravitee.am.service.model.NewDictionary;
import io.gravitee.am.service.model.UpdateI18nDictionary;
import io.gravitee.am.service.reporter.builder.DictionaryAuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.model.ReferenceType.DOMAIN;
import static io.reactivex.rxjava3.core.Maybe.just;
import static java.util.Locale.ENGLISH;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class I18nDictionaryServiceTest {

    private static final String REFERENCE_ID = UUID.randomUUID().toString();
    private static final String ID = UUID.randomUUID().toString();
    @Mock
    private I18nDictionaryRepository repository;
    @Mock
    private EventService eventService;
    @Mock
    private AuditService auditService;
    @InjectMocks
    private I18nDictionaryService service;

    @Test
    public void shouldCreateEmptyDictionary() {
        var newDictionary = new NewDictionary();
        newDictionary.setName("Français");
        newDictionary.setLocale(Locale.FRENCH.getLanguage());

        given(repository.findByLocale(DOMAIN, REFERENCE_ID, Locale.FRENCH.getLanguage())).willReturn(Maybe.empty());
        given(repository.create(any(I18nDictionary.class))).will(invocation -> Single.just(invocation.getArgument(0)));
        given(eventService.create(any())).willReturn(Single.just(new Event()));

        var observer = service
                .create(DOMAIN, REFERENCE_ID, newDictionary, new DefaultUser()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();

        var dictionaryArgCaptor = ArgumentCaptor.forClass(I18nDictionary.class);
        verify(repository).create(dictionaryArgCaptor.capture());
        var capturedDictionary = dictionaryArgCaptor.getValue();
        assertEquals(newDictionary.getName(), capturedDictionary.getName());
        assertEquals(newDictionary.getLocale(), capturedDictionary.getLocale());
        assertEquals(DOMAIN, capturedDictionary.getReferenceType());
        assertEquals(REFERENCE_ID, capturedDictionary.getReferenceId());

        verify(auditService).report(isA(DictionaryAuditBuilder.class));
    }

    @Test
    public void shouldUpdateDictionary() {
        UpdateI18nDictionary updateDict = new UpdateI18nDictionary();
        String expectedName = "Updated";
        updateDict.setName(expectedName);
        String expectedLocale = "pl";
        updateDict.setLocale(expectedLocale);
        final String translation = "Username";
        final String key = "login.username";
        updateDict.setEntries(Map.of(key, translation));
        var dictionary = new I18nDictionary();
        dictionary.setReferenceType(DOMAIN);
        dictionary.setReferenceId(REFERENCE_ID);

        when(repository.findById(DOMAIN, REFERENCE_ID, ID)).thenReturn(just(dictionary));
        when(repository.findByLocale(DOMAIN, REFERENCE_ID, expectedLocale)).thenReturn(Maybe.empty());
        when(repository.update(any(I18nDictionary.class))).thenReturn(Single.just(dictionary));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));


        var testObserver = service.update(DOMAIN, REFERENCE_ID, ID, updateDict, new DefaultUser()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        var dictionaryArgCaptor = ArgumentCaptor.forClass(I18nDictionary.class);
        verify(repository).findById(DOMAIN, REFERENCE_ID, ID);
        verify(repository).update(dictionaryArgCaptor.capture());
        var capturedDictionary = dictionaryArgCaptor.getValue();
        String value = capturedDictionary.getEntries().get(key);
        assertEquals(translation, value);
        assertEquals(expectedName, capturedDictionary.getName());
        assertEquals(expectedLocale, capturedDictionary.getLocale());
    }

    @Test
    public void shouldUpdateDictionaryEntries() {
        final String translation = "Username";
        final String key = "login.username";
        var entries = Map.of(key, translation);
        var dictionary = new I18nDictionary();
        dictionary.setReferenceType(DOMAIN);
        dictionary.setReferenceId(REFERENCE_ID);

        when(repository.findById(DOMAIN, REFERENCE_ID, ID)).thenReturn(just(dictionary));
        when(repository.update(any(I18nDictionary.class))).thenReturn(Single.just(dictionary));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));


        var testObserver = service.updateEntries(DOMAIN, REFERENCE_ID, ID, entries, new DefaultUser()).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();

        var dictionaryArgCaptor = ArgumentCaptor.forClass(I18nDictionary.class);
        verify(repository).findById(DOMAIN, REFERENCE_ID, ID);
        verify(repository).update(dictionaryArgCaptor.capture());
        var capturedDictionary = dictionaryArgCaptor.getValue();
        String value = capturedDictionary.getEntries().get(key);
        assertEquals(translation, value);
    }


    @Test
    public void shouldFindByName() {
        var dictionary = new I18nDictionary();
        dictionary.setLocale(ENGLISH.getLanguage());
        given(repository.findByLocale(eq(DOMAIN), any(), eq(ENGLISH.getLanguage()))).willReturn(just(dictionary));
        var observer = service.findByLocale(DOMAIN, "", ENGLISH.getLanguage()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValueCount(1);
    }

    @Test
    public void shouldDeleteDictionary() {
        I18nDictionary i18nDictionary = new I18nDictionary();
        i18nDictionary.setReferenceType(DOMAIN);
        i18nDictionary.setReferenceId(REFERENCE_ID);
        when(repository.findById(DOMAIN, REFERENCE_ID, ID)).thenReturn(just(i18nDictionary));
        when(repository.delete(ID)).thenReturn(Completable.complete());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        var observer = service.delete(DOMAIN, REFERENCE_ID, ID, new DefaultUser()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();

        verify(repository).delete(ID);
    }

    @Test
    public void shouldReturnTechnicalManagementExceptionWhenErrorThrownDuringFindByName() {
        given(repository.findByLocale(eq(DOMAIN), any(), eq(ENGLISH.getLanguage()))).willReturn(Maybe.error(Exception::new));
        var observer = service.findByLocale(DOMAIN, REFERENCE_ID, ENGLISH.getLanguage()).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(TechnicalManagementException.class);
    }

    @Test
    public void shouldReturnTechnicalManagementExceptionWhenErrorThrownDuringFindAll() {
        given(repository.findAll(any(), any())).willReturn(Flowable.error(Exception::new));
        var observer = service.findAll(DOMAIN, REFERENCE_ID).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(TechnicalManagementException.class);
    }

    @Test
    public void shouldReturnDictionaryNotFoundExceptionWhenDictionaryIdNotFound() {
        given(repository.findById(eq(DOMAIN), any(), any())).willReturn(Maybe.error(Exception::new));
        var observer = service.findById(DOMAIN, REFERENCE_ID, ID).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(TechnicalManagementException.class);
    }
}
