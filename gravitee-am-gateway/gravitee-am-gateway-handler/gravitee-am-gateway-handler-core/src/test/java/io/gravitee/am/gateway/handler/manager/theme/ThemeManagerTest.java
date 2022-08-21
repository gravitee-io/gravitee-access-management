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
package io.gravitee.am.gateway.handler.manager.theme;

import io.gravitee.am.common.event.EventManager;
import io.gravitee.am.common.event.ThemeEvent;
import io.gravitee.am.gateway.handler.vertx.view.thymeleaf.DomainBasedThemeResolver;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Theme;
import io.gravitee.am.model.common.event.Payload;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.ThemeRepository;
import io.gravitee.common.event.Event;
import io.reactivex.Maybe;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ThemeManagerTest {

    @Mock
    private EventManager eventManager;

    @Mock
    private ThemeRepository themeRepository;

    @Mock
    private DomainBasedThemeResolver domainBasedThemeResolver;

    @Mock
    private Domain domain;

    @Mock
    private Payload payload;

    @InjectMocks
    private ThemeManager themeManager = new ThemeManager();

    @Before
    public void setUp() {
        when(domain.getId()).thenReturn("domain-id");
        when(domain.getName()).thenReturn("domain-name");

        when(payload.getReferenceType()).thenReturn(ReferenceType.DOMAIN);
        when(payload.getReferenceId()).thenReturn("domain-id");
        when(payload.getId()).thenReturn("theme-id");
    }

    @Test
    public void shouldLoadThemes_after_properties_set() throws Exception {
        Theme theme = mock(Theme.class);
        when(theme.getId()).thenReturn("theme-id");

        when(themeRepository.findByReference(ReferenceType.DOMAIN, domain.getId())).thenReturn(Maybe.just(theme));

        themeManager.afterPropertiesSet();

        verify(themeRepository, times(1)).findByReference(ReferenceType.DOMAIN, domain.getId());
        verify(domainBasedThemeResolver, times(1)).updateTheme(any());
    }

    @Test
    public void shouldNotLoadThemes_after_properties_set_exception() throws Exception {
        when(themeRepository.findByReference(ReferenceType.DOMAIN, domain.getId())).thenReturn(Maybe.error(TechnicalException::new));

        themeManager.afterPropertiesSet();

        verify(themeRepository, times(1)).findByReference(ReferenceType.DOMAIN, domain.getId());
        verify(domainBasedThemeResolver, never()).updateTheme(any());
    }

    @Test
    public void shouldSubscribeToEvents() throws Exception {
        themeManager.doStart();

        verify(eventManager, times(1)).subscribeForEvents(themeManager, ThemeEvent.class, domain.getId());
    }

    @Test
    public void shouldUnSubscribeToEvents() throws Exception {
        themeManager.doStop();

        verify(eventManager, times(1)).unsubscribeForEvents(themeManager, ThemeEvent.class, domain.getId());
    }

    @Test
    public void shouldNotDeploy_wrong_domain() {
        Payload payload = mock(Payload.class);
        when(payload.getReferenceType()).thenReturn(ReferenceType.DOMAIN);
        when(payload.getReferenceId()).thenReturn("wrong-domain-id");

        Event<ThemeEvent, Payload> event = mock(Event.class);
        when(event.content()).thenReturn(payload);

        themeManager.onEvent(event);

        verify(themeRepository, never()).findById(anyString());
        verify(domainBasedThemeResolver, never()).updateTheme(any());
    }

    @Test
    public void shouldDeploy() {
        shouldDeploy(ThemeEvent.DEPLOY);
    }

    @Test
    public void shouldUpdate() {
        shouldDeploy(ThemeEvent.UPDATE);
    }

    @Test
    public void shouldNotDeploy_exception() {
        Event<ThemeEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(ThemeEvent.DEPLOY);
        when(event.content()).thenReturn(payload);

        when(themeRepository.findById("theme-id")).thenReturn(Maybe.error(TechnicalException::new));

        themeManager.onEvent(event);

        verify(themeRepository, times(1)).findById(anyString());
        verify(domainBasedThemeResolver, never()).updateTheme(any());
    }

    @Test
    public void shouldNotDeploy_not_found() {
        Event<ThemeEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(ThemeEvent.DEPLOY);
        when(event.content()).thenReturn(payload);

        when(themeRepository.findById("theme-id")).thenReturn(Maybe.empty());

        themeManager.onEvent(event);

        verify(themeRepository, times(1)).findById(anyString());
        verify(domainBasedThemeResolver, never()).updateTheme(any());
    }

    @Test
    public void shouldUndeploy() {
        Event<ThemeEvent, Payload> deployEvent = mock(Event.class);
        when(deployEvent.type()).thenReturn(ThemeEvent.DEPLOY);
        when(deployEvent.content()).thenReturn(payload);

        Event<ThemeEvent, Payload> undeployEvent = mock(Event.class);
        when(undeployEvent.type()).thenReturn(ThemeEvent.UNDEPLOY);
        when(undeployEvent.content()).thenReturn(payload);

        Theme theme = mock(Theme.class);
        when(theme.getId()).thenReturn("theme-id");
        when(theme.getReferenceId()).thenReturn("domain-id");
        when(themeRepository.findById("theme-id")).thenReturn(Maybe.just(theme));

        themeManager.onEvent(deployEvent);
        themeManager.onEvent(undeployEvent);

        verify(domainBasedThemeResolver, times(1)).removeTheme("domain-id");
    }

    private void shouldDeploy(ThemeEvent themeEvent) {
        Event<ThemeEvent, Payload> event = mock(Event.class);
        when(event.type()).thenReturn(themeEvent);
        when(event.content()).thenReturn(payload);

        Theme theme = mock(Theme.class);
        when(theme.getId()).thenReturn("theme-id");
        when(themeRepository.findById("theme-id")).thenReturn(Maybe.just(theme));

        themeManager.onEvent(event);

        verify(themeRepository, times(1)).findById(anyString());
    }
}
