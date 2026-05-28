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
package io.gravitee.am.service.impl;

import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationCursorRequest;
import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.repository.management.api.ApplicationCursorRepository;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ApplicationSearcherImplTest {

    private static final String ORGANIZATION = "DEFAULT";
    private static final String DOMAIN = "domain-1";
    private static final String OWNER_EMAIL = "owner@example.com";
    private static final int LIMIT = 25;

    @InjectMocks
    private final ApplicationSearcherImpl searcher = new ApplicationSearcherImpl();

    @Mock
    private ApplicationCursorRepository cursorRepository;

    @Mock
    private ApplicationOwnerService applicationOwnerService;

    private ApplicationCursorRequest cursor() {
        return ApplicationCursorRequest.initialCursor("updatedAt", "DESC", 0, null, null, null);
    }

    private CursorPage<Application, ApplicationCursorRequest> emptyPage() {
        return new CursorPage<>(List.of(), null, 0L);
    }

    @Test
    public void searchByDomainCursor_noOwnerEmail_delegatesToFindByDomainCursor() {
        final ApplicationCursorRequest cursor = cursor();
        final CursorPage<Application, ApplicationCursorRequest> page = emptyPage();
        when(cursorRepository.findByDomainCursor(DOMAIN, cursor, LIMIT)).thenReturn(Single.just(page));

        final TestObserver<CursorPage<Application, ApplicationCursorRequest>> observer =
                searcher.searchByDomainCursor(ORGANIZATION, DOMAIN, cursor, null, LIMIT).test();

        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete().assertNoErrors().assertValue(page);
        verify(cursorRepository).findByDomainCursor(DOMAIN, cursor, LIMIT);
        verify(cursorRepository, never()).findByDomainAndIdsCursor(eq(DOMAIN), org.mockito.ArgumentMatchers.anyList(), eq(cursor), eq(LIMIT));
        verify(applicationOwnerService, never()).retrieveOwnerApplicationIds(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void searchByDomainCursor_withOwnerEmail_passesOwnerAppIdsToFindByDomainAndIdsCursor() {
        final ApplicationCursorRequest cursor = cursor();
        final List<String> ownerAppIds = List.of("app-1", "app-2");
        final CursorPage<Application, ApplicationCursorRequest> page = emptyPage();
        when(applicationOwnerService.retrieveOwnerApplicationIds(OWNER_EMAIL, ORGANIZATION)).thenReturn(Maybe.just(ownerAppIds));
        when(cursorRepository.findByDomainAndIdsCursor(DOMAIN, ownerAppIds, cursor, LIMIT)).thenReturn(Single.just(page));

        final TestObserver<CursorPage<Application, ApplicationCursorRequest>> observer =
                searcher.searchByDomainCursor(ORGANIZATION, DOMAIN, cursor, OWNER_EMAIL, LIMIT).test();

        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete().assertNoErrors().assertValue(page);
        verify(cursorRepository).findByDomainAndIdsCursor(DOMAIN, ownerAppIds, cursor, LIMIT);
        verify(cursorRepository, never()).findByDomainCursor(eq(DOMAIN), eq(cursor), eq(LIMIT));
    }

    @Test
    public void searchByDomainCursor_withOwnerEmail_ownerHasNoApps_passesEmptyIdList() {
        final ApplicationCursorRequest cursor = cursor();
        final CursorPage<Application, ApplicationCursorRequest> page = emptyPage();
        when(applicationOwnerService.retrieveOwnerApplicationIds(OWNER_EMAIL, ORGANIZATION)).thenReturn(Maybe.empty());
        when(cursorRepository.findByDomainAndIdsCursor(DOMAIN, List.of(), cursor, LIMIT)).thenReturn(Single.just(page));

        final TestObserver<CursorPage<Application, ApplicationCursorRequest>> observer =
                searcher.searchByDomainCursor(ORGANIZATION, DOMAIN, cursor, OWNER_EMAIL, LIMIT).test();

        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete().assertNoErrors().assertValue(page);
        verify(cursorRepository).findByDomainAndIdsCursor(DOMAIN, List.of(), cursor, LIMIT);
    }

    @Test
    public void searchByDomainAndIdsCursor_noOwnerEmail_delegatesWithProvidedIds() {
        final ApplicationCursorRequest cursor = cursor();
        final List<String> scopedIds = List.of("app-1", "app-2", "app-3");
        final CursorPage<Application, ApplicationCursorRequest> page = emptyPage();
        when(cursorRepository.findByDomainAndIdsCursor(DOMAIN, scopedIds, cursor, LIMIT)).thenReturn(Single.just(page));

        final TestObserver<CursorPage<Application, ApplicationCursorRequest>> observer =
                searcher.searchByDomainAndIdsCursor(ORGANIZATION, DOMAIN, scopedIds, cursor, null, LIMIT).test();

        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete().assertNoErrors().assertValue(page);
        verify(cursorRepository).findByDomainAndIdsCursor(DOMAIN, scopedIds, cursor, LIMIT);
        verify(applicationOwnerService, never()).retrieveOwnerApplicationIds(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    public void searchByDomainAndIdsCursor_withOwnerEmail_intersectsOwnerIdsWithPermissionScope() {
        final ApplicationCursorRequest cursor = cursor();
        final List<String> scopedIds = List.of("app-1", "app-2", "app-3");
        final List<String> ownerAppIds = List.of("app-2", "app-3", "app-9");
        final List<String> expectedIntersection = List.of("app-2", "app-3");
        final CursorPage<Application, ApplicationCursorRequest> page = emptyPage();
        when(applicationOwnerService.retrieveOwnerApplicationIds(OWNER_EMAIL, ORGANIZATION)).thenReturn(Maybe.just(ownerAppIds));
        when(cursorRepository.findByDomainAndIdsCursor(DOMAIN, expectedIntersection, cursor, LIMIT)).thenReturn(Single.just(page));

        final TestObserver<CursorPage<Application, ApplicationCursorRequest>> observer =
                searcher.searchByDomainAndIdsCursor(ORGANIZATION, DOMAIN, scopedIds, cursor, OWNER_EMAIL, LIMIT).test();

        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete().assertNoErrors().assertValue(page);
        verify(cursorRepository).findByDomainAndIdsCursor(DOMAIN, expectedIntersection, cursor, LIMIT);
    }

    @Test
    public void searchByDomainAndIdsCursor_withOwnerEmail_ownerHasNoApps_passesEmptyIdList() {
        final ApplicationCursorRequest cursor = cursor();
        final List<String> scopedIds = List.of("app-1", "app-2");
        final CursorPage<Application, ApplicationCursorRequest> page = emptyPage();
        when(applicationOwnerService.retrieveOwnerApplicationIds(OWNER_EMAIL, ORGANIZATION)).thenReturn(Maybe.empty());
        when(cursorRepository.findByDomainAndIdsCursor(DOMAIN, List.of(), cursor, LIMIT)).thenReturn(Single.just(page));

        final TestObserver<CursorPage<Application, ApplicationCursorRequest>> observer =
                searcher.searchByDomainAndIdsCursor(ORGANIZATION, DOMAIN, scopedIds, cursor, OWNER_EMAIL, LIMIT).test();

        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete().assertNoErrors().assertValue(page);
        verify(cursorRepository).findByDomainAndIdsCursor(DOMAIN, List.of(), cursor, LIMIT);
    }

    @Test
    public void searchByDomainAndIdsCursor_withOwnerEmail_noOverlap_passesEmptyIdList() {
        final ApplicationCursorRequest cursor = cursor();
        final List<String> scopedIds = List.of("app-1", "app-2");
        final List<String> ownerAppIds = List.of("app-100", "app-200");
        final CursorPage<Application, ApplicationCursorRequest> page = emptyPage();
        when(applicationOwnerService.retrieveOwnerApplicationIds(OWNER_EMAIL, ORGANIZATION)).thenReturn(Maybe.just(ownerAppIds));
        when(cursorRepository.findByDomainAndIdsCursor(DOMAIN, List.of(), cursor, LIMIT)).thenReturn(Single.just(page));

        final TestObserver<CursorPage<Application, ApplicationCursorRequest>> observer =
                searcher.searchByDomainAndIdsCursor(ORGANIZATION, DOMAIN, scopedIds, cursor, OWNER_EMAIL, LIMIT).test();

        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertComplete().assertNoErrors().assertValue(page);
        verify(cursorRepository).findByDomainAndIdsCursor(DOMAIN, List.of(), cursor, LIMIT);
    }

    @Test
    public void searchByDomainCursor_repositoryError_propagatesError() {
        final ApplicationCursorRequest cursor = cursor();
        final RuntimeException boom = new RuntimeException("boom");
        when(cursorRepository.findByDomainCursor(DOMAIN, cursor, LIMIT)).thenReturn(Single.error(boom));

        final TestObserver<CursorPage<Application, ApplicationCursorRequest>> observer =
                searcher.searchByDomainCursor(ORGANIZATION, DOMAIN, cursor, null, LIMIT).test();

        observer.awaitDone(5, TimeUnit.SECONDS);
        observer.assertError(boom);
    }

}
