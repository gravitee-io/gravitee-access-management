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

import io.gravitee.am.model.Application;
import io.gravitee.am.model.application.ApplicationCursorRequest;
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.cursor.CursorPage;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ApplicationCursorRepositoryTest extends AbstractManagementTest {

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private ApplicationCursorRepository applicationCursorRepository;

    @Test
    public void findByDomainCursor_firstPage_returnsDataAndNextCursor() {
        String domain = "cursor-domain-" + UUID.randomUUID();
        createApp(domain, "app-a");
        createApp(domain, "app-b");
        createApp(domain, "app-c");

        ApplicationCursorRequest cursor = ApplicationCursorRequest.initialCursor("name", "ASC", 0, null, null, null);

        TestObserver<CursorPage<Application, ApplicationCursorRequest>> observer = applicationCursorRepository.findByDomainCursor(domain, cursor, 2).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(page -> page.getTotalCount() == 3
                && page.isHasNext()
                && page.getData().size() == 2
                && List.of("app-a", "app-b").equals(page.getData().stream().map(Application::getName).toList()));
    }

    @Test
    public void findByDomainCursor_nextPage_usesReturnedCursor() {
        String domain = "cursor-next-domain-" + UUID.randomUUID();
        createApp(domain, "app-a");
        createApp(domain, "app-b");
        createApp(domain, "app-c");

        ApplicationCursorRequest cursor = ApplicationCursorRequest.initialCursor("name", "ASC", 0, null, null, null);

        CursorPage<Application, ApplicationCursorRequest> firstPage = applicationCursorRepository.findByDomainCursor(domain, cursor, 2).blockingGet();
        CursorPage<Application, ApplicationCursorRequest> secondPage = applicationCursorRepository.findByDomainCursor(domain, firstPage.getNextCursor(), 2).blockingGet();

        assertFalse(secondPage.isHasNext());
        assertEquals(1, secondPage.getData().size());
        assertEquals("app-c", secondPage.getData().get(0).getName());
        assertEquals(Long.valueOf(3), secondPage.getTotalCount());
    }

    @Test
    public void findByDomainAndIdsCursor_filtersApplications() {
        String domain = "cursor-ids-domain-" + UUID.randomUUID();
        Application appA = createApp(domain, "app-a");
        createApp(domain, "app-b");
        Application appC = createApp(domain, "app-c");

        ApplicationCursorRequest cursor = ApplicationCursorRequest.initialCursor("name", "ASC", 0, null, null, null);

        CursorPage<Application, ApplicationCursorRequest> page = applicationCursorRepository.findByDomainAndIdsCursor(domain, List.of(appC.getId(), appA.getId()), cursor, 10).blockingGet();

        assertFalse(page.isHasNext());
        assertEquals(List.of("app-a", "app-c"), page.getData().stream().map(Application::getName).toList());
        assertEquals(Long.valueOf(2), page.getTotalCount());
    }

    @Test
    public void findByDomainCursor_query_matchesClientId_caseInsensitive() {
        String domain = "cursor-query-domain-" + UUID.randomUUID();
        createApp(domain, "app-a", "client-a");
        createApp(domain, "app-b", "other-client");

        ApplicationCursorRequest cursor = ApplicationCursorRequest.initialCursor("name", "ASC", 0, "CLIENT-A", null, null);

        CursorPage<Application, ApplicationCursorRequest> page = applicationCursorRepository.findByDomainCursor(domain, cursor, 10).blockingGet();

        assertFalse(page.isHasNext());
        assertEquals(List.of("app-a"), page.getData().stream().map(Application::getName).toList());
        assertEquals(Long.valueOf(1), page.getTotalCount());
    }

    @Test
    public void findByDomainCursor_wildcardQuery_matchesClientIdWithEscapedCharacters() {
        String domain = "cursor-wildcard-domain-" + UUID.randomUUID();
        createApp(domain, "app-a", "client[test]");
        createApp(domain, "app-b", "client-other");

        ApplicationCursorRequest cursor = ApplicationCursorRequest.initialCursor("name", "ASC", 0, "*[*", null, null);

        CursorPage<Application, ApplicationCursorRequest> page = applicationCursorRepository.findByDomainCursor(domain, cursor, 10).blockingGet();

        assertFalse(page.isHasNext());
        assertEquals(List.of("app-a"), page.getData().stream().map(Application::getName).toList());
        assertEquals(Long.valueOf(1), page.getTotalCount());
    }

    @Test
    public void findByDomainCursor_updatedAt_respectsFirstPageOffset() {
        String domain = "cursor-updated-at-domain-" + UUID.randomUUID();
        updateTimestamp(createApp(domain, "app-a"), 1_000L);
        updateTimestamp(createApp(domain, "app-b"), 2_000L);
        updateTimestamp(createApp(domain, "app-c"), 3_000L);

        ApplicationCursorRequest cursor = ApplicationCursorRequest.initialCursor("updatedAt", "DESC", 1, null, null, null);

        CursorPage<Application, ApplicationCursorRequest> page = applicationCursorRepository.findByDomainCursor(domain, cursor, 1).blockingGet();

        assertEquals(List.of("app-b"), page.getData().stream().map(Application::getName).toList());
        assertTrue(page.isHasNext());
        assertEquals(Long.valueOf(3), page.getTotalCount());
    }

    @Test
    public void findByDomainCursor_invalidSortField_returnsError() {
        String domain = "cursor-invalid-sort-domain-" + UUID.randomUUID();
        createApp(domain, "app-a");

        ApplicationCursorRequest cursor = ApplicationCursorRequest.initialCursor("invalid", "ASC", 0, null, null, null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> applicationCursorRepository.findByDomainCursor(domain, cursor, 10).blockingGet());
        assertEquals("Invalid sort field: invalid", error.getMessage());
    }

    private Application createApp(String domain, String name) {
        return createApp(domain, name, null);
    }

    private Application createApp(String domain, String name, String clientId) {
        Application app = new Application();
        app.setDomain(domain);
        app.setName(name);
        if (clientId != null) {
            ApplicationSettings settings = new ApplicationSettings();
            ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
            oauth.setClientId(clientId);
            settings.setOauth(oauth);
            app.setSettings(settings);
        }
        return applicationRepository.create(app).blockingGet();
    }

    private void updateTimestamp(Application app, long timestamp) {
        app.setUpdatedAt(new Date(timestamp));
        applicationRepository.update(app).blockingGet();
    }
}
