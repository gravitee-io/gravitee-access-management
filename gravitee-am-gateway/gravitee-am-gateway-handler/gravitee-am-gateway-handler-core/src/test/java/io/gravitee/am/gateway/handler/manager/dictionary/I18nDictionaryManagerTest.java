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
package io.gravitee.am.gateway.handler.manager.dictionary;

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.I18nDictionaryEvent;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.I18nDictionary;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.I18nDictionaryRepository;
import io.gravitee.am.service.i18n.GraviteeMessageResolver;
import io.gravitee.common.event.Event;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class I18nDictionaryManagerTest {

    @Mock
    private EventManager eventManager;

    @Mock
    private I18nDictionaryRepository i18nDictionaryRepository;

    @Mock
    private GraviteeMessageResolver graviteeMessageResolver;

    @Mock
    private Domain domain;

    @Mock
    private Payload payload;

    @InjectMocks
    private I18nDictionaryManager i18nDictionaryManager = new I18nDictionaryManager();

    @Before
    public void setUp() {
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("domain-name");

        when(payload.getReferenceType()).thenReturn(ReferenceType.DOMAIN);
        when(payload.getReferenceId()).thenReturn("domain-id");
        when(payload.getId()).thenReturn("dictionary-id");
    }

    @Test
    public void shouldLoadDictionaries_after_properties_set() throws Exception {
        I18nDictionary i18nDictionary = mock(I18nDictionary.class);
        when(i18nDictionary.getId()).thenReturn("dictionary-id");

        when(i18nDictionaryRepository.findAll(ReferenceType.DOMAIN, domain.getId())).thenReturn(Flowable.just(i18nDictionary));

        i18nDictionaryManager.afterPropertiesSet();

        verify(i18nDictionaryRepository, times(1)).findAll(ReferenceType.DOMAIN, domain.getId());
        verify(graviteeMessageResolver, times(1)).updateDictionary(any());
    }

    @Test
    public void shouldNotLoadDictionaries_after_properties_set_exception() throws Exception {
        when(i18nDictionaryRepository.findAll(ReferenceType.DOMAIN, domain.getId())).thenReturn(Flowable.error(TechnicalException::new));

        i18nDictionaryManager.afterPropertiesSet();

        verify(i18nDictionaryRepository, times(1)).findAll(ReferenceType.DOMAIN, domain.getId());
        verify(graviteeMessageResolver, never()).updateDictionary(any());
    }

    @Test
    public void shouldSubscribeToEvents() throws Exception {
        i18nDictionaryManager.doStart();

        verify(eventManager, times(1)).subscribeForEvents(i18nDictionaryManager, I18nDictionaryEvent.class, domain.getId());
    }

    @Test
    public void shouldUnSubscribeToEvents() throws Exception {
        i18nDictionaryManager.doStop();

        verify(eventManager, times(1)).unsubscribeForEvents(i18nDictionaryManager, I18nDictionaryEvent.class, domain.getId());
    }

    @Test
    public void shouldNotDeploy_wrong_domain() {
        Payload payload = mock(Payload.class);
        when(payload.getReferenceType()).thenReturn(ReferenceType.DOMAIN);
        when(payload.getReferenceId()).thenReturn("wrong-domain-id");

        Event<I18nDictionaryEvent, Payload> event = mock(Event.class);
        when(event.content()).thenReturn(payload);

        i18nDictionaryManager.onEvent(event);

        verify(i18nDictionaryRepository, never()).findById(anyString());
        verify(graviteeMessageResolver, never()).updateDictionary(any());
    }

    @Test
    public void shouldDeploy() {
        shouldDeploy(I18nDictionaryEvent.DEPLOY);
    }

    @Test
    public void shouldUpdate() {
       shouldDeploy(I18nDictionaryEvent.UPDATE);
    }

    @Test
    public void shouldNotDeploy_exception() {
        Event<I18nDictionaryEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(I18nDictionaryEvent.DEPLOY);
        when(event.content()).thenReturn(payload);

        when(i18nDictionaryRepository.findById("dictionary-id")).thenReturn(Maybe.error(TechnicalException::new));

        i18nDictionaryManager.onEvent(event);

        verify(i18nDictionaryRepository, times(1)).findById(anyString());
        verify(graviteeMessageResolver, never()).updateDictionary(any());
    }

    @Test
    public void shouldNotDeploy_not_found() {
        Event<I18nDictionaryEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(I18nDictionaryEvent.DEPLOY);
        when(event.content()).thenReturn(payload);

        when(i18nDictionaryRepository.findById("dictionary-id")).thenReturn(Maybe.empty());

        i18nDictionaryManager.onEvent(event);

        verify(i18nDictionaryRepository, times(1)).findById(anyString());
        verify(graviteeMessageResolver, never()).updateDictionary(any());
    }

    @Test
    public void shouldUndeploy() {
        Event<I18nDictionaryEvent, Payload> deployEvent = mock(Event.class);
        when(deployEvent.type()).thenReturn(I18nDictionaryEvent.DEPLOY);
        when(deployEvent.content()).thenReturn(payload);

        Event<I18nDictionaryEvent, Payload> undeployEvent = mock(Event.class);
        when(undeployEvent.type()).thenReturn(I18nDictionaryEvent.UNDEPLOY);
        when(undeployEvent.content()).thenReturn(payload);

        I18nDictionary i18nDictionary = mock(I18nDictionary.class);
        when(i18nDictionary.getId()).thenReturn("dictionary-id");
        when(i18nDictionary.getLocale()).thenReturn("dictionary-locale");
        when(i18nDictionaryRepository.findById("dictionary-id")).thenReturn(Maybe.just(i18nDictionary));

        i18nDictionaryManager.onEvent(deployEvent);
        i18nDictionaryManager.onEvent(undeployEvent);

        verify(graviteeMessageResolver, times(1)).removeDictionary(anyString());
    }

    private void shouldDeploy(I18nDictionaryEvent i18nDictionaryEvent) {
        Event<I18nDictionaryEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(i18nDictionaryEvent);
        when(event.content()).thenReturn(payload);

        I18nDictionary i18nDictionary = mock(I18nDictionary.class);
        when(i18nDictionary.getId()).thenReturn("dictionary-id");
        when(i18nDictionaryRepository.findById("dictionary-id")).thenReturn(Maybe.just(i18nDictionary));

        i18nDictionaryManager.onEvent(event);

        verify(i18nDictionaryRepository, times(1)).findById(anyString());
        verify(graviteeMessageResolver, times(1)).updateDictionary(any());
    }
}
