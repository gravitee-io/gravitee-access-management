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
import io.gravitee.am.model.application.ApplicationType;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.login.LoginSettings;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.AbstractManagementTest;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;
import org.mockito.internal.util.collections.Sets;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ApplicationRepositoryTest extends AbstractManagementTest {

    @Autowired
    private ApplicationRepository applicationRepository;

    @Test
    public void testFindByDomain() throws TechnicalException {
        // create application
        Application application = new Application();
        application.setName("testApp");
        application.setDomain("testDomain");
        applicationRepository.create(application).blockingGet();

        // fetch applications
        TestObserver<Page<Application>> testObserver = applicationRepository.findByDomain("testDomain", 0, Integer.MAX_VALUE).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(applicationPage -> applicationPage.getData().size() == 1);
    }

    @Test
    public void testFindByDomainAndClaim() throws TechnicalException {
        // create application
        Application application = new Application();
        application.setName("testApp");
        application.setDomain("testDomain");
        ApplicationSettings settings = new ApplicationSettings();
        application.setSettings(settings);
        ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        settings.setOauth(oauth);
        oauth.setGrantTypes(Arrays.asList("test-grant"));
        applicationRepository.create(application).blockingGet();

        // fetch applications
        TestSubscriber<Application> testSubscriber = applicationRepository.findByDomainAndExtensionGrant("testDomain", "test-grant").test();
        testSubscriber.awaitTerminalEvent();

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
    }


    @Test
    public void testFindByDomainAndClientId() {
        // create application
        Application application = new Application();
        application.setName("testApp");
        application.setDomain("testDomain");
        ApplicationSettings settings = new ApplicationSettings();
        application.setSettings(settings);
        ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        settings.setOauth(oauth);
        oauth.setClientId("clientId1");
        oauth.setGrantTypes(Arrays.asList("test-grant"));
        Application createdApplication = applicationRepository.create(application).blockingGet();

        // fetch applications
        TestObserver<Application> testObserver = applicationRepository.findByDomainAndClientId("testDomain", "clientId1").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(app -> app.getId().equalsIgnoreCase(createdApplication.getId()));
    }

    @Test
    public void testFindByDomainPagination() throws TechnicalException {
        // create app 1
        Application app = new Application();
        app.setName("testClientId");
        app.setDomain("testDomainPagination");
        applicationRepository.create(app).blockingGet();

        // create app 2
        Application app2 = new Application();
        app2.setName("testClientId2");
        app2.setDomain("testDomainPagination");
        applicationRepository.create(app2).blockingGet();

        TestObserver<Page<Application>> testObserver = applicationRepository.findByDomain("testDomainPagination", 1, 1).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(pageApplications -> pageApplications.getTotalCount() == 2 && pageApplications.getData().size() == 1);
    }

    @Test
    public void testFindById() throws TechnicalException {
        // create app
        Application app = buildApplication();
        Application appCreated = applicationRepository.create(app).blockingGet();

        // fetch app
        TestObserver<Application> testObserver = applicationRepository.findById(appCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEqualsTo(app, testObserver);
    }

    @Test
    public void testFindByIdentity() {
        // create app
        Application app = buildApplication();
        Application appCreated = applicationRepository.create(app).blockingGet();

        // fetch app
        TestSubscriber<Application> testSubscriber = applicationRepository.findByIdentityProvider(appCreated.getIdentities().iterator().next()).test();
        testSubscriber.awaitTerminalEvent();

        testSubscriber.assertComplete();
        testSubscriber.assertNoErrors();
        testSubscriber.assertValueCount(1);
        testSubscriber.assertValue(s -> s.getId().equals(appCreated.getId()));
    }

    private void assertEqualsTo(Application app, TestObserver<Application> testObserver) {
        testObserver.assertValue(a -> a.getName().equals(app.getName()));
        testObserver.assertValue(a -> a.getType().equals(app.getType()));
        testObserver.assertValue(a -> a.isEnabled() == app.isEnabled());
        testObserver.assertValue(a -> a.isTemplate() == app.isTemplate());
        testObserver.assertValue(a -> a.getFactors().containsAll(app.getFactors()));
        testObserver.assertValue(a -> a.getCertificate().equals(app.getCertificate()));
        testObserver.assertValue(a -> a.getDescription().equals(app.getDescription()));
        testObserver.assertValue(a -> a.getIdentities().containsAll(app.getIdentities()));
        testObserver.assertValue(a -> a.getSettings() != null);
        testObserver.assertValue(a -> a.getSettings().getOauth() != null);
        testObserver.assertValue(a -> a.getSettings().getOauth().getGrantTypes().containsAll(Arrays.asList("authorization_code")));
        testObserver.assertValue(a -> a.getMetadata() != null);
        testObserver.assertValue(a -> a.getMetadata().containsKey("key1"));
    }

    private static Application buildApplication() {
        String random = UUID.randomUUID().toString();
        Application app = new Application();
        app.setType(ApplicationType.NATIVE);
        app.setCertificate("cert" + random);
        app.setDescription("desc" + random);
        app.setDomain("domain" + random);
        app.setName("name" + random);
        app.setTemplate(true);
        app.setEnabled(true);
        app.setIdentities(Sets.newSet("ipd1" + random, "idp2" + random));
        app.setFactors(Sets.newSet("fact1" + random, "fact2" + random));
        app.setSettings(buildApplicationSettings());
        HashMap<String, Object> metadata = new HashMap<>();
        metadata.put("key1", "value1");
        app.setMetadata(metadata);
        app.setCreatedAt(new Date());
        app.setUpdatedAt(new Date());
        return app;
    }

    private static ApplicationSettings buildApplicationSettings() {
        ApplicationSettings settings = new ApplicationSettings();
        settings.setLogin(new LoginSettings());
        ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        oauth.setGrantTypes(Collections.singletonList("authorization_code"));
        settings.setOauth(oauth);
        return settings;
    }

    @Test
    public void testNotFoundById() throws TechnicalException {
        applicationRepository.findById("test").test().assertEmpty();
    }

    @Test
    public void testCreate() throws TechnicalException {
        Application application = new Application();
        application.setName("testClientId");

        TestObserver<Application> testObserver = applicationRepository.create(application).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(a -> a.getName().equals(application.getName()));
    }

    @Test
    public void testUpdate() throws TechnicalException {
        // create app
        Application app = buildApplication();
        Application appCreated = applicationRepository.create(app).blockingGet();

        // update app
        Application updatedApp = buildApplication();
        updatedApp.setId(appCreated.getId());

        TestObserver<Application> testObserver = applicationRepository.update(updatedApp).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        assertEqualsTo(updatedApp, testObserver);
    }

    @Test
    public void testDelete() throws TechnicalException {
        // create app
        Application app = new Application();
        app.setName("testClientId");
        Application appCreated = applicationRepository.create(app).blockingGet();

        // fetch app
        TestObserver<Application> testObserver = applicationRepository.findById(appCreated.getId()).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(a -> a.getName().equals(app.getName()));

        // delete app
        TestObserver testObserver1 = applicationRepository.delete(appCreated.getId()).test();
        testObserver1.awaitTerminalEvent();

        // fetch app
        applicationRepository.findById(appCreated.getId()).test().assertEmpty();
    }

    @Test
    public void testSearch_strict() {
        final String domain = "domain";
        // create app
        Application app = new Application();
        app.setDomain(domain);
        app.setName("clientId");
        applicationRepository.create(app).blockingGet();

        Application app2 = new Application();
        app2.setDomain(domain);
        app2.setName("clientId2");
        applicationRepository.create(app2).blockingGet();

        // fetch user
        TestObserver<Page<Application>> testObserver = applicationRepository.search(domain, "clientId", 0, Integer.MAX_VALUE).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(apps -> apps.getData().size() == 1);
        testObserver.assertValue(apps -> apps.getTotalCount() == 1);
        testObserver.assertValue(apps -> apps.getData().iterator().next().getName().equals(app.getName()));

    }

    @Test
    public void testSearch_wildcard() {
        final String domain = "domain";
        // create app
        Application app = new Application();
        app.setDomain(domain);
        app.setName("clientId");
        applicationRepository.create(app).blockingGet();

        Application app2 = new Application();
        app2.setDomain(domain);
        app2.setName("clientId2");
        applicationRepository.create(app2).blockingGet();

        Application app3 = new Application();
        app3.setDomain(domain);
        app3.setName("test");
        applicationRepository.create(app3).blockingGet();

        Application app4 = new Application();
        app4.setDomain(domain);
        app4.setName("test");
        ApplicationSettings settings = new ApplicationSettings();
        app4.setSettings(settings);
        ApplicationOAuthSettings oauth = new ApplicationOAuthSettings();
        settings.setOauth(oauth);
        oauth.setClientId("clientId4");
        applicationRepository.create(app4).blockingGet();

        // fetch apps
        TestObserver<Page<Application>> testObserver = applicationRepository.search(domain, "clientId*", 0, Integer.MAX_VALUE).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(apps -> apps.getData().size() == 3);
        testObserver.assertValue(apps -> apps.getTotalCount() == 3);
    }

}
