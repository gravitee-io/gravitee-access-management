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
import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.model.application.ApplicationSettings;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.gravitee.am.repository.management.api.search.ApplicationCriteria;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests for the criteria-based application query methods:
 * {@link ApplicationRepository#findByDomain(String, ApplicationCriteria, int, int)} and
 * {@link ApplicationRepository#search(String, ApplicationCriteria, String, int, int)}.
 *
 * @author GraviteeSource Team
 */
public class ApplicationRepositoryCriteriaTest extends AbstractManagementTest {

    @Autowired
    private ApplicationRepository applicationRepository;

    // -------------------------------------------------------------------------
    // findByDomain with ApplicationCriteria
    // -------------------------------------------------------------------------

    @Test
    public void findByDomain_criteria_emptyFilters_returnsAll() {
        String domain = "crit-domain-" + UUID.randomUUID();
        createApp(domain, "app1", true, "clientA");
        createApp(domain, "app2", false, "clientB");

        ApplicationCriteria criteria = new ApplicationCriteria();

        TestObserver<Page<Application>> observer = applicationRepository.findByDomain(domain, criteria, 0, 10).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(page -> page.getTotalCount() == 2 && page.getData().size() == 2);
    }

    @Test
    public void findByDomain_criteria_filterByEnabled_returnsOnlyEnabled() {
        String domain = "crit-enabled-" + UUID.randomUUID();
        createApp(domain, "enabledApp", true, "clientEn");
        createApp(domain, "disabledApp", false, "clientDis");

        ApplicationCriteria criteria = new ApplicationCriteria().setEnabled(true);

        TestObserver<Page<Application>> observer = applicationRepository.findByDomain(domain, criteria, 0, 10).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(page -> page.getTotalCount() == 1
                && page.getData().stream().allMatch(Application::isEnabled));
    }

    @Test
    public void findByDomain_criteria_filterByDisabled_returnsOnlyDisabled() {
        String domain = "crit-disabled-" + UUID.randomUUID();
        createApp(domain, "enabledApp2", true, "clientEn2");
        createApp(domain, "disabledApp2", false, "clientDis2");

        ApplicationCriteria criteria = new ApplicationCriteria().setEnabled(false);

        TestObserver<Page<Application>> observer = applicationRepository.findByDomain(domain, criteria, 0, 10).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(page -> page.getTotalCount() == 1
                && page.getData().stream().noneMatch(Application::isEnabled));
    }

    @Test
    public void findByDomain_criteria_filterByApplicationIds_returnsMatchingApps() {
        String domain = "crit-ids-" + UUID.randomUUID();
        Application a = createApp(domain, "appX", true, "clientX");
        createApp(domain, "appY", true, "clientY");

        ApplicationCriteria criteria = new ApplicationCriteria().setApplicationIds(List.of(a.getId()));

        TestObserver<Page<Application>> observer = applicationRepository.findByDomain(domain, criteria, 0, 10).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(page -> page.getTotalCount() == 1
                && page.getData().iterator().next().getId().equals(a.getId()));
    }

    @Test
    public void findByDomain_criteria_filterByEmptyApplicationIds_returnsNoApps() {
        String domain = "crit-empty-ids-" + UUID.randomUUID();
        createApp(domain, "appZ", true, "clientZ");

        ApplicationCriteria criteria = new ApplicationCriteria().setApplicationIds(List.of());

        TestObserver<Page<Application>> observer = applicationRepository.findByDomain(domain, criteria, 0, 10).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(page -> page.getTotalCount() == 0 && page.getData().isEmpty());
    }

    @Test
    public void findByDomain_criteria_combinedFilters_enabledAndApplicationIds() {
        String domain = "crit-combo-" + UUID.randomUUID();
        Application enabledA = createApp(domain, "enabledA", true, "clientEA");
        Application disabledB = createApp(domain, "disabledB", false, "clientDB");

        // restrict to both IDs but filter by enabled=true
        ApplicationCriteria criteria = new ApplicationCriteria()
                .setApplicationIds(List.of(enabledA.getId(), disabledB.getId()))
                .setEnabled(true);

        TestObserver<Page<Application>> observer = applicationRepository.findByDomain(domain, criteria, 0, 10).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(page -> page.getTotalCount() == 1
                && page.getData().iterator().next().getId().equals(enabledA.getId()));
    }

    @Test
    public void findByDomain_criteria_pagination_respectsPageAndSize() {
        String domain = "crit-page-" + UUID.randomUUID();
        createApp(domain, "p1", true, "cP1");
        createApp(domain, "p2", true, "cP2");
        createApp(domain, "p3", true, "cP3");

        ApplicationCriteria criteria = new ApplicationCriteria().setEnabled(true);

        TestObserver<Page<Application>> page0 = applicationRepository.findByDomain(domain, criteria, 0, 2).test();
        page0.awaitDone(10, TimeUnit.SECONDS);
        page0.assertComplete();
        page0.assertNoErrors();
        page0.assertValue(page -> page.getTotalCount() == 3 && page.getData().size() == 2);

        TestObserver<Page<Application>> page1 = applicationRepository.findByDomain(domain, criteria, 1, 2).test();
        page1.awaitDone(10, TimeUnit.SECONDS);
        page1.assertComplete();
        page1.assertNoErrors();
        page1.assertValue(page -> page.getTotalCount() == 3 && page.getData().size() == 1);
    }

    // -------------------------------------------------------------------------
    // search with ApplicationCriteria
    // -------------------------------------------------------------------------

    @Test
    public void search_criteria_noFilters_matchesQuery() {
        String domain = "crit-search-" + UUID.randomUUID();
        createApp(domain, "searchMe", true, "clientSM");
        createApp(domain, "other", true, "clientO");

        ApplicationCriteria criteria = new ApplicationCriteria();

        TestObserver<Page<Application>> observer = applicationRepository.search(domain, criteria, "searchMe", 0, 10).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(page -> page.getTotalCount() == 1
                && page.getData().iterator().next().getName().equals("searchMe"));
    }

    @Test
    public void search_criteria_filterByEnabled_narrowsResults() {
        String domain = "crit-search-en-" + UUID.randomUUID();
        createApp(domain, "targetEnabled", true, "clientTE");
        createApp(domain, "targetDisabled", false, "clientTD");

        ApplicationCriteria criteria = new ApplicationCriteria().setEnabled(true);

        TestObserver<Page<Application>> observer = applicationRepository.search(domain, criteria, "target*", 0, 10).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(page -> page.getTotalCount() == 1
                && page.getData().stream().allMatch(Application::isEnabled));
    }

    @Test
    public void search_criteria_filterByApplicationIds_narrowsResults() {
        String domain = "crit-search-ids-" + UUID.randomUUID();
        Application included = createApp(domain, "included", true, "clientInc");
        createApp(domain, "excluded", true, "clientExc");

        ApplicationCriteria criteria = new ApplicationCriteria()
                .setApplicationIds(List.of(included.getId()));

        TestObserver<Page<Application>> observer = applicationRepository.search(domain, criteria, "inc*", 0, 10).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(page -> page.getTotalCount() == 1
                && page.getData().iterator().next().getId().equals(included.getId()));
    }

    @Test
    public void search_criteria_emptyApplicationIds_returnsEmpty() {
        String domain = "crit-search-empty-" + UUID.randomUUID();
        createApp(domain, "anyApp", true, "clientAny");

        ApplicationCriteria criteria = new ApplicationCriteria().setApplicationIds(List.of());

        TestObserver<Page<Application>> observer = applicationRepository.search(domain, criteria, "any*", 0, 10).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(page -> page.getTotalCount() == 0 && page.getData().isEmpty());
    }

    @Test
    public void search_criteria_combinedEnabledAndIds() {
        String domain = "crit-search-combo-" + UUID.randomUUID();
        Application enabledApp = createApp(domain, "comboEnabled", true, "clientCE");
        Application disabledApp = createApp(domain, "comboDisabled", false, "clientCD");

        ApplicationCriteria criteria = new ApplicationCriteria()
                .setApplicationIds(List.of(enabledApp.getId(), disabledApp.getId()))
                .setEnabled(true);

        TestObserver<Page<Application>> observer = applicationRepository.search(domain, criteria, "combo*", 0, 10).test();
        observer.awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertNoErrors();
        observer.assertValue(page -> page.getTotalCount() == 1
                && page.getData().iterator().next().getId().equals(enabledApp.getId()));
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private Application createApp(String domain, String name, boolean enabled, String clientId) {
        Application app = new Application();
        app.setDomain(domain);
        app.setName(name);
        app.setEnabled(enabled);

        ApplicationSettings settings = new ApplicationSettings();
        ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setClientId(clientId);
        settings.setOauth(oauth);
        app.setSettings(settings);

        return applicationRepository.create(app).blockingGet();
    }
}
