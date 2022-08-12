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
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.Theme;
import io.gravitee.am.model.common.event.Event;
import io.gravitee.am.repository.management.api.ThemeRepository;
import io.gravitee.am.service.exception.InvalidThemeException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.exception.ThemeNotFoundException;
import io.gravitee.am.service.impl.ThemeServiceImpl;
import io.gravitee.am.service.model.NewTheme;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ThemeServiceTest {

    public static final String DOMAIN_ID_1 = "DomainID1";
    public static final String THEME_ID = "themeid";
    @InjectMocks
    private ThemeServiceImpl cut;

    @Mock
    private ThemeRepository repository;
    @Mock
    private EventService eventService;
    @Mock
    private AuditService auditService;

    @Test
    public void testFindByReference() {
        when(repository.findByReference(any(), any())).thenReturn(Maybe.just(new Theme()));
        final TestObserver<Theme> test = cut.findByReference(ReferenceType.DOMAIN, DOMAIN_ID_1).test();
        test.awaitTerminalEvent();
        test.assertValueCount(1);
    }

    @Test
    public void testFindByReference_Error() {
        when(repository.findByReference(any(), any())).thenReturn(Maybe.error(new RuntimeException()));
        final TestObserver<Theme> test = cut.findByReference(ReferenceType.DOMAIN, DOMAIN_ID_1).test();
        test.awaitTerminalEvent();
        test.assertError(TechnicalManagementException.class);
    }

    @Test
    public void testCreate() {
        Theme theme = new Theme();

        when(repository.create(any())).thenReturn(Single.just(theme));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));
        when(repository.findByReference(any(), any())).thenReturn(Maybe.empty());

        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID_1);

        final TestObserver<Theme> test = cut.create(domain, new NewTheme(), new DefaultUser()).test();

        test.awaitTerminalEvent();
        test.assertValueCount(1);

        verify(repository).create(argThat(input -> ReferenceType.DOMAIN.equals(input.getReferenceType()) && DOMAIN_ID_1.equals(input.getReferenceId())));
        verify(auditService).report(any());
        verify(eventService).create(any());
    }

    @Test
    public void testCreate_Error() {
        when(repository.findByReference(any(), any())).thenReturn(Maybe.empty());
        when(repository.create(any())).thenReturn(Single.error(new RuntimeException()));

        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID_1);

        final TestObserver<Theme> test = cut.create(domain, new NewTheme(), new DefaultUser()).test();

        test.awaitTerminalEvent();
        test.assertError(TechnicalManagementException.class);

        verify(repository).create(argThat(input -> ReferenceType.DOMAIN.equals(input.getReferenceType()) && DOMAIN_ID_1.equals(input.getReferenceId())));
        verify(auditService).report(any());
        verify(eventService, never()).create(any());
    }

    @Test(expected = NullPointerException.class)
    public void testUpdate_ThemeWithoutId() {
        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID_1);

        final Theme updatedTheme = new Theme();
        updatedTheme.setReferenceId(DOMAIN_ID_1);
        updatedTheme.setReferenceType(ReferenceType.DOMAIN);

        final TestObserver<Theme> test = cut.update(domain, updatedTheme, new DefaultUser()).test();
        test.awaitTerminalEvent();

        verify(repository, never()).update(any());
        verify(auditService, never()).report(any());
        verify(eventService, never()).create(any());
    }

    @Test
    public void testUpdate_ThemeWithoutReferenceId() {
        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID_1);

        final Theme updatedTheme = new Theme();
        updatedTheme.setId(THEME_ID);
        updatedTheme.setReferenceType(ReferenceType.DOMAIN);

        final Theme existingTheme = new Theme();
        existingTheme.setId(THEME_ID);
        existingTheme.setReferenceId(DOMAIN_ID_1);
        existingTheme.setReferenceType(ReferenceType.DOMAIN);
        existingTheme.setCreatedAt(new Date());
        when(repository.findById(eq(THEME_ID))).thenReturn(Maybe.just(existingTheme));

        final TestObserver<Theme> test = cut.update(domain, updatedTheme, new DefaultUser()).test();

        test.awaitTerminalEvent();
        test.assertError(InvalidThemeException.class);

        verify(repository, never()).update(any());
        verify(auditService, never()).report(any());
        verify(eventService, never()).create(any());
    }

    @Test
    public void testUpdate_ThemeWithoutReferenceType() {
        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID_1);

        final Theme updatedTheme = new Theme();
        updatedTheme.setId(THEME_ID);
        updatedTheme.setReferenceId(DOMAIN_ID_1);

        final Theme existingTheme = new Theme();
        existingTheme.setId(THEME_ID);
        existingTheme.setReferenceId(DOMAIN_ID_1);
        existingTheme.setReferenceType(ReferenceType.DOMAIN);
        existingTheme.setCreatedAt(new Date());
        when(repository.findById(eq(THEME_ID))).thenReturn(Maybe.just(existingTheme));

        final TestObserver<Theme> test = cut.update(domain, updatedTheme, new DefaultUser()).test();

        test.awaitTerminalEvent();
        test.assertError(InvalidThemeException.class);

        verify(repository, never()).update(any());
        verify(auditService, never()).report(any());
        verify(eventService, never()).create(any());
    }

    @Test
    public void testUpdate_ThemeNotFound() {
        when(repository.findById(any())).thenReturn(Maybe.empty());

        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID_1);

        final Theme updatedTheme = new Theme();
        updatedTheme.setId(THEME_ID);
        updatedTheme.setReferenceId(DOMAIN_ID_1);
        updatedTheme.setReferenceType(ReferenceType.DOMAIN);

        final TestObserver<Theme> test = cut.update(domain, updatedTheme, new DefaultUser()).test();

        test.awaitTerminalEvent();
        test.assertError(ThemeNotFoundException.class);

        verify(repository, never()).update(any());
        verify(auditService, never()).report(any());
        verify(eventService, never()).create(any());
    }

    @Test
    public void testUpdate() {
        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID_1);

        final Theme updatedTheme = new Theme();
        updatedTheme.setId(THEME_ID);
        updatedTheme.setReferenceId(DOMAIN_ID_1);
        updatedTheme.setReferenceType(ReferenceType.DOMAIN);

        final Theme existingTheme = new Theme();
        existingTheme.setId(THEME_ID);
        existingTheme.setReferenceId(DOMAIN_ID_1);
        existingTheme.setReferenceType(ReferenceType.DOMAIN);
        existingTheme.setCreatedAt(new Date());
        when(repository.findById(eq(THEME_ID))).thenReturn(Maybe.just(existingTheme));

        when(repository.update(any())).thenReturn(Single.just(new Theme()));
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        final TestObserver<Theme> test = cut.update(domain, updatedTheme, new DefaultUser()).test();

        test.awaitTerminalEvent();
        test.assertValueCount(1);

        verify(repository).update(any());
        verify(auditService).report(any());
        verify(eventService).create(any());
    }

    @Test
    public void testGetTheme() {
        final Theme theme = new Theme();
        theme.setReferenceId(DOMAIN_ID_1);
        theme.setReferenceType(ReferenceType.DOMAIN);

        when(repository.findById(any())).thenReturn(Maybe.just(theme));

        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID_1);
        final TestObserver<Theme> test = cut.getTheme(domain, "anyid").test();

        test.awaitTerminalEvent();
        test.assertValueCount(1);
    }

    @Test
    public void testGetTheme_DomainMismatch() {
        final Theme theme = new Theme();
        theme.setReferenceId("otherdomain");
        theme.setReferenceType(ReferenceType.DOMAIN);

        when(repository.findById(any())).thenReturn(Maybe.just(theme));

        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID_1);
        final TestObserver<Theme> test = cut.getTheme(domain, "anyid").test();

        test.awaitTerminalEvent();
        test.assertError(ThemeNotFoundException.class);
    }

    @Test
    public void testGetTheme_ReferenceMismatch() {
        final Theme theme = new Theme();
        theme.setReferenceId("otherdomain");
        theme.setReferenceType(ReferenceType.APPLICATION);

        when(repository.findById(any())).thenReturn(Maybe.just(theme));

        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID_1);
        final TestObserver<Theme> test = cut.getTheme(domain, "anyid").test();

        test.awaitTerminalEvent();
        test.assertError(ThemeNotFoundException.class);
    }

    @Test
    public void testDelete() {
        final Theme theme = new Theme();
        theme.setReferenceId(DOMAIN_ID_1);
        theme.setReferenceType(ReferenceType.DOMAIN);

        when(repository.findById(any())).thenReturn(Maybe.just(theme));

        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID_1);

        when(repository.delete(any())).thenReturn(Completable.complete());
        when(eventService.create(any())).thenReturn(Single.just(new Event()));

        final TestObserver<Void> test = cut.delete(domain, "themeid", new DefaultUser()).test();

        test.awaitTerminalEvent();
        test.assertNoErrors();

        verify(repository).delete(any());
        verify(auditService).report(any());
        verify(eventService).create(any());
    }

    @Test
    public void testDelete_DomainMismatch() {
        final Theme theme = new Theme();
        theme.setReferenceId("anotherdomain");
        theme.setReferenceType(ReferenceType.DOMAIN);

        when(repository.findById(any())).thenReturn(Maybe.just(theme));

        final Domain domain = new Domain();
        domain.setId(DOMAIN_ID_1);

        final TestObserver<Void> test = cut.delete(domain, "themeid", new DefaultUser()).test();

        test.awaitTerminalEvent();
        test.assertError(InvalidThemeException.class);

        verify(repository, never()).delete(any());
        verify(auditService, never()).report(any());
        verify(eventService, never()).create(any());
    }
}
