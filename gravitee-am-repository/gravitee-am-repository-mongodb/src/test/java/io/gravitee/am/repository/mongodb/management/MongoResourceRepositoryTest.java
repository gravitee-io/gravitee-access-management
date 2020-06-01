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

import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.ResourceRepository;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class MongoResourceRepositoryTest extends AbstractManagementRepositoryTest {

    @Autowired
    private ResourceRepository repository;

    private static final String DOMAIN_ID = "domainId";
    private static final String CLIENT_ID = "clientId";
    private static final String USER_ID = "userId";

    @Override
    public String collectionName() {
        return "resource_set";
    }

    @Test
    public void testFindById() throws TechnicalException {
        // create resource_set, resource_scopes being the most important field.
        Resource resource = new Resource().setResourceScopes(Arrays.asList("a","b","c"));
        Resource rsCreated = repository.create(resource).blockingGet();

        // fetch resource_set
        TestObserver<Resource> testObserver = repository.findById(rsCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(s -> s.getResourceScopes().containsAll(Arrays.asList("a","b","c")));
    }

    @Test
    public void update() throws TechnicalException {
        // create resource_set, resource_scopes being the most important field.
        Resource resource = new Resource().setResourceScopes(Arrays.asList("a","b","c"));
        Resource rsCreated = repository.create(resource).blockingGet();
        Resource toUpdate = new Resource().setId(rsCreated.getId()).setResourceScopes(Arrays.asList("d","e","f"));

        // fetch resource_set
        TestObserver<Resource> testObserver = repository.update(toUpdate).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(s -> s.getResourceScopes().containsAll(Arrays.asList("d","e","f")));
    }

    @Test
    public void delete() throws TechnicalException {
        // create resource_set, resource_scopes being the most important field.
        Resource resource = new Resource().setResourceScopes(Arrays.asList("a","b","c"));
        Resource rsCreated = repository.create(resource).blockingGet();

        // fetch resource_set
        TestObserver<Void> testObserver = repository.delete(rsCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertNoValues();
    }

    @Test
    public void findByDomainAndClientAndUserAndResource() throws TechnicalException {
        // create resource_set, resource_scopes being the most important field.
        Resource resource = new Resource()
                .setResourceScopes(Arrays.asList("a","b","c"))
                .setDomain(DOMAIN_ID)
                .setClientId(CLIENT_ID)
                .setUserId(USER_ID);

        Resource rsCreated = repository.create(resource).blockingGet();

        // fetch scope
        TestObserver<Resource> testObserver = repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, rsCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(s -> s.getResourceScopes().containsAll(Arrays.asList("a","b","c")));
    }

    @Test
    public void findByDomainAndClientAndUser() throws TechnicalException {
        // create resource_set, resource_scopes being the most important field.
        Resource resource1 = new Resource().setResourceScopes(Arrays.asList("a","b","c")).setDomain(DOMAIN_ID).setClientId(CLIENT_ID).setUserId(USER_ID);
        Resource resource2 = new Resource().setResourceScopes(Arrays.asList("d","e","f")).setDomain(DOMAIN_ID).setClientId(CLIENT_ID).setUserId(USER_ID);

        Resource rsCreated1 = repository.create(resource1).blockingGet();
        Resource rsCreated2 = repository.create(resource2).blockingGet();

        // fetch scope
        TestObserver<List<Resource>> testObserver = repository.findByDomainAndClientAndUser(DOMAIN_ID, CLIENT_ID, USER_ID).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        List<String> expectedIds = Arrays.asList(rsCreated1.getId(), rsCreated2.getId());
        testObserver.assertValue(s -> s.stream().map(Resource::getId).collect(Collectors.toList()).containsAll(expectedIds));
    }

    @Test
    public void testFindByDomain() throws TechnicalException {
        // create resource_set, resource_scopes being the most important field.
        Resource resource1 = new Resource().setResourceScopes(Arrays.asList("a","b","c")).setDomain(DOMAIN_ID).setClientId(CLIENT_ID).setUserId(USER_ID);
        Resource resource2 = new Resource().setResourceScopes(Arrays.asList("d","e","f")).setDomain(DOMAIN_ID).setClientId(CLIENT_ID).setUserId(USER_ID);

        repository.create(resource1).blockingGet();
        repository.create(resource2).blockingGet();

        // fetch applications
        TestObserver<Page<Resource>> testObserver = repository.findByDomain(DOMAIN_ID, 0, Integer.MAX_VALUE).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(resources -> resources.getData().size() == 2);
    }

    @Test
    public void testFindByResources() throws TechnicalException {
        // create resource_set, resource_scopes being the most important field.
        Resource resource1 = new Resource().setResourceScopes(Arrays.asList("a","b","c")).setDomain("domainA").setClientId(CLIENT_ID).setUserId(USER_ID);
        Resource resource2 = new Resource().setResourceScopes(Arrays.asList("d","e","f")).setDomain("domainB").setClientId(CLIENT_ID).setUserId(USER_ID);

        Resource rsCreated1 = repository.create(resource1).blockingGet();
        Resource rsCreated2 = repository.create(resource2).blockingGet();

        // fetch applications
        TestObserver<List<Resource>> testObserver = repository.findByResources(Arrays.asList(rsCreated1.getId(),rsCreated2.getId(),"notMatching")).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(resources -> resources.size() == 2);
    }

    @Test
    public void testFindByDomainAndClientAndUserAndResources() throws TechnicalException {
        // create resource_set, resource_scopes being the most important field.
        Resource resource1 = new Resource().setResourceScopes(Arrays.asList("a")).setDomain(DOMAIN_ID).setClientId(CLIENT_ID).setUserId(USER_ID);
        Resource resource2 = new Resource().setResourceScopes(Arrays.asList("b")).setDomain(DOMAIN_ID).setClientId(CLIENT_ID).setUserId(USER_ID);
        Resource resource3 = new Resource().setResourceScopes(Arrays.asList("c")).setDomain("another").setClientId(CLIENT_ID).setUserId(USER_ID);
        Resource resource4 = new Resource().setResourceScopes(Arrays.asList("d")).setDomain(DOMAIN_ID).setClientId("another").setUserId(USER_ID);
        Resource resource5 = new Resource().setResourceScopes(Arrays.asList("d")).setDomain(DOMAIN_ID).setClientId(CLIENT_ID).setUserId("another");

        Resource rsCreated1 = repository.create(resource1).blockingGet();
        Resource rsCreated2 = repository.create(resource2).blockingGet();
        Resource rsCreated3 = repository.create(resource3).blockingGet();
        Resource rsCreated4 = repository.create(resource4).blockingGet();
        Resource rsCreated5 = repository.create(resource5).blockingGet();

        // fetch applications
        TestObserver<List<Resource>> testObserver = repository.findByDomainAndClientAndUserAndResources(DOMAIN_ID, CLIENT_ID, USER_ID, Arrays.asList(
                rsCreated1.getId(),rsCreated2.getId(),rsCreated3.getId(),rsCreated4.getId(),rsCreated5.getId(),"unknown"
        )).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(resources -> resources.size() == 2);
    }
}
