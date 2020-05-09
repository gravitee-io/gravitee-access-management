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

import io.gravitee.am.model.uma.ResourceSet;
import io.gravitee.am.repository.management.api.ResourceSetRepository;
import io.gravitee.am.service.exception.ResourceSetNotFoundException;
import io.gravitee.am.service.impl.ResourceSetServiceImpl;
import io.gravitee.am.service.model.NewResourceSet;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.vertx.core.json.JsonObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ResourceSetServiceTest {

    @Mock
    private ResourceSetRepository repository;

    @InjectMocks
    private ResourceSetService service = new ResourceSetServiceImpl();

    private static final String DOMAIN_ID = "domainId";
    private static final String CLIENT_ID = "clientId";
    private static final String USER_ID = "userId";
    private static final String RESOURCE_ID = "resourceId";

    @Before
    public void setUp() {
        when(repository.findByDomainAndClientAndUser(DOMAIN_ID, CLIENT_ID, USER_ID)).thenReturn(Single.just(Arrays.asList(new ResourceSet().setId(RESOURCE_ID))));
        when(repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.just(new ResourceSet().setId(RESOURCE_ID)));
    }

    @Test
    public void delete_nonExistingResource() {
        when(repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.empty());
        TestObserver testObserver = service.delete(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertError(ResourceSetNotFoundException.class);
    }

    @Test
    public void delete_existingResource() {
        when(repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.just(new ResourceSet().setId(RESOURCE_ID)));
        when(repository.delete(RESOURCE_ID)).thenReturn(Completable.complete());
        TestObserver testObserver = service.delete(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertComplete().assertNoErrors().assertNoValues();
    }

    @Test
    public void update_nonExistingResource() {
        when(repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.empty());
        TestObserver testObserver = service.update(new NewResourceSet(), DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertError(ResourceSetNotFoundException.class);
    }

    @Test
    public void update_existingResource() {
        NewResourceSet newResourceSet = new JsonObject("{\"resource_scopes\":[\"scope\"]}").mapTo(NewResourceSet.class);
        ResourceSet exitingRS = new ResourceSet().setId(RESOURCE_ID);
        when(repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.just(exitingRS));
        when(repository.update(exitingRS)).thenReturn(Single.just(exitingRS));
        TestObserver<ResourceSet> testObserver = service.update(newResourceSet, DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertComplete().assertNoErrors().assertValue(this::assertResourceSetValues);
    }

    @Test
    public void create_success() {
        NewResourceSet newResourceSet = new JsonObject("{\"resource_scopes\":[\"scope\"]}").mapTo(NewResourceSet.class);
        ArgumentCaptor<ResourceSet> rsCaptor = ArgumentCaptor.forClass(ResourceSet.class);
        when(repository.create(any())).thenReturn(Single.just(new ResourceSet()));
        TestObserver<ResourceSet> testObserver = service.create(newResourceSet, DOMAIN_ID, CLIENT_ID, USER_ID).test();
        verify(repository, times(1)).create(rsCaptor.capture());
        Assert.assertTrue(this.assertResourceSetValues(rsCaptor.getValue()));
    }

    @Test
    public void list() {
        TestObserver<List<ResourceSet>> testObserver = service.listByDomainAndClientAndUser(DOMAIN_ID, CLIENT_ID, USER_ID).test();
        testObserver.assertNoErrors().assertComplete().assertValue(resourceSets -> resourceSets.get(0).getId().equals(RESOURCE_ID));
    }

    private boolean assertResourceSetValues(ResourceSet toValidate) {
        return toValidate!=null &&
                toValidate.getResourceScopes()!=null &&
                toValidate.getResourceScopes().size() == 1 &&
                toValidate.getResourceScopes().get(0).equals("scope") &&
                toValidate.getUpdatedAt() !=null;
    }
}
