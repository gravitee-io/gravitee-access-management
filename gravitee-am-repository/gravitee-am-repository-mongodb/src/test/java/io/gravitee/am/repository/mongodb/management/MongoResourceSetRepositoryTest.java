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

import io.gravitee.am.model.uma.ResourceSet;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.repository.management.api.ResourceSetRepository;
import io.reactivex.Completable;
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
public class MongoResourceSetRepositoryTest extends AbstractManagementRepositoryTest {

    @Autowired
    private ResourceSetRepository repository;

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
        ResourceSet resourceSet = new ResourceSet().setResourceScopes(Arrays.asList("a","b","c"));
        ResourceSet rsCreated = repository.create(resourceSet).blockingGet();

        // fetch resource_set
        TestObserver<ResourceSet> testObserver = repository.findById(rsCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(s -> s.getResourceScopes().containsAll(Arrays.asList("a","b","c")));
    }

    @Test
    public void update() throws TechnicalException {
        // create resource_set, resource_scopes being the most important field.
        ResourceSet resourceSet = new ResourceSet().setResourceScopes(Arrays.asList("a","b","c"));
        ResourceSet rsCreated = repository.create(resourceSet).blockingGet();
        ResourceSet toUpdate = new ResourceSet().setId(rsCreated.getId()).setResourceScopes(Arrays.asList("d","e","f"));

        // fetch resource_set
        TestObserver<ResourceSet> testObserver = repository.update(toUpdate).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(s -> s.getResourceScopes().containsAll(Arrays.asList("d","e","f")));
    }

    @Test
    public void delete() throws TechnicalException {
        // create resource_set, resource_scopes being the most important field.
        ResourceSet resourceSet = new ResourceSet().setResourceScopes(Arrays.asList("a","b","c"));
        ResourceSet rsCreated = repository.create(resourceSet).blockingGet();

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
        ResourceSet resourceSet = new ResourceSet()
                .setResourceScopes(Arrays.asList("a","b","c"))
                .setDomain(DOMAIN_ID)
                .setClientId(CLIENT_ID)
                .setUserId(USER_ID);

        ResourceSet rsCreated = repository.create(resourceSet).blockingGet();

        // fetch scope
        TestObserver<ResourceSet> testObserver = repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, rsCreated.getId()).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(s -> s.getResourceScopes().containsAll(Arrays.asList("a","b","c")));
    }

    @Test
    public void findByDomainAndClientAndUser() throws TechnicalException {
        // create resource_set, resource_scopes being the most important field.
        ResourceSet resourceSet1 = new ResourceSet().setResourceScopes(Arrays.asList("a","b","c")).setDomain(DOMAIN_ID).setClientId(CLIENT_ID).setUserId(USER_ID);
        ResourceSet resourceSet2 = new ResourceSet().setResourceScopes(Arrays.asList("d","e","f")).setDomain(DOMAIN_ID).setClientId(CLIENT_ID).setUserId(USER_ID);

        ResourceSet rsCreated1 = repository.create(resourceSet1).blockingGet();
        ResourceSet rsCreated2 = repository.create(resourceSet2).blockingGet();

        // fetch scope
        TestObserver<List<ResourceSet>> testObserver = repository.findByDomainAndClientAndUser(DOMAIN_ID, CLIENT_ID, USER_ID).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        List<String> expectedIds = Arrays.asList(rsCreated1.getId(), rsCreated2.getId());
        testObserver.assertValue(s -> s.stream().map(ResourceSet::getId).collect(Collectors.toList()).containsAll(expectedIds));
    }
}
