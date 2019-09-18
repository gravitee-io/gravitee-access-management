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
package io.gravitee.am.repository.mongodb.management;

import io.gravitee.am.model.Application;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.ApplicationRepository;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoApplicationRepositoryTest extends AbstractManagementRepositoryTest {

    @Autowired
    private ApplicationRepository applicationRepository;

    @Override
    public String collectionName() {
        return "applications";
    }

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
        Application app = new Application();
        app.setName("testClientId");
        Application appCreated = applicationRepository.create(app).blockingGet();

        // fetch app
        TestObserver<Application> testObserver = applicationRepository.findById(appCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(a -> a.getName().equals("testClientId"));
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
        Application app = new Application();
        app.setName("testClientId");
        Application appCreated = applicationRepository.create(app).blockingGet();

        // update app
        Application updatedApp = new Application();
        updatedApp.setId(appCreated.getId());
        updatedApp.setName("testUpdatedClientId");

        TestObserver<Application> testObserver = applicationRepository.update(updatedApp).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(a -> a.getName().equals(updatedApp.getName()));
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

        // fetch apps
        TestObserver<Page<Application>> testObserver = applicationRepository.search(domain, "clientId*", 0, Integer.MAX_VALUE).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(apps -> apps.getData().size() == 2);
    }

}
