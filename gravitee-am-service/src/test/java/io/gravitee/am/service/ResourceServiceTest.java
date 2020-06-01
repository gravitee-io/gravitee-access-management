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

import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.repository.management.api.ResourceRepository;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.impl.ResourceServiceImpl;
import io.gravitee.am.service.model.NewResource;
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

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ResourceServiceTest {

    @Mock
    private ResourceRepository repository;

    @Mock
    private ScopeService scopeService;

    @InjectMocks
    private ResourceService service = new ResourceServiceImpl();

    private static final String DOMAIN_ID = "domainId";
    private static final String CLIENT_ID = "clientId";
    private static final String USER_ID = "userId";
    private static final String RESOURCE_ID = "resourceId";

    @Before
    public void setUp() {
        when(repository.findByDomainAndClientAndUser(DOMAIN_ID, CLIENT_ID, USER_ID)).thenReturn(Single.just(Arrays.asList(new Resource().setId(RESOURCE_ID))));
        when(repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.just(new Resource().setId(RESOURCE_ID)));
        when(scopeService.findByDomainAndKeys(DOMAIN_ID, Arrays.asList("scope"))).thenReturn(Single.just(Arrays.asList(new Scope("scope"))));
    }

    @Test
    public void delete_nonExistingResource() {
        when(repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.empty());
        TestObserver testObserver = service.delete(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertError(ResourceNotFoundException.class);
    }

    @Test
    public void delete_existingResource() {
        when(repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.just(new Resource().setId(RESOURCE_ID)));
        when(repository.delete(RESOURCE_ID)).thenReturn(Completable.complete());
        TestObserver testObserver = service.delete(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertComplete().assertNoErrors().assertNoValues();
    }

    @Test
    public void update_nonExistingResource() {
        when(repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.empty());
        TestObserver testObserver = service.update(new NewResource(), DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertError(ResourceNotFoundException.class);
    }

    @Test
    public void update_scopeMissing() {
        NewResource newResource = new JsonObject("{\"resource_scopes\":[]}").mapTo(NewResource.class);
        Resource exitingRS = new Resource().setId(RESOURCE_ID).setDomain(DOMAIN_ID);
        when(repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.just(exitingRS));
        TestObserver<Resource> testObserver = service.update(newResource, DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertError(MissingScopeException.class);
        verify(repository, times(0)).update(any());
    }

    @Test
    public void update_scopeNotFound() {
        NewResource newResource = new JsonObject("{\"resource_scopes\":[\"scope\"]}").mapTo(NewResource.class);
        Resource exitingRS = new Resource().setId(RESOURCE_ID).setDomain(DOMAIN_ID);
        when(scopeService.findByDomainAndKeys(DOMAIN_ID, Arrays.asList("scope"))).thenReturn(Single.just(Collections.emptyList()));
        when(repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.just(exitingRS));
        TestObserver<Resource> testObserver = service.update(newResource, DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertError(ScopeNotFoundException.class);
        verify(repository, times(0)).update(any());
    }

    @Test
    public void update_malformedIconUri() {
        NewResource newResource = new JsonObject("{\"resource_scopes\":[\"scope\"],\"icon_uri\":\"badIconUriFormat\"}").mapTo(NewResource.class);
        Resource exitingRS = new Resource().setId(RESOURCE_ID).setDomain(DOMAIN_ID);
        when(repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.just(exitingRS));
        TestObserver<Resource> testObserver = service.update(newResource, DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertError(MalformedIconUriException.class);
        verify(repository, times(0)).update(any());
    }

    @Test
    public void update_existingResource() {
        NewResource newResource = new JsonObject("{\"resource_scopes\":[\"scope\"],\"icon_uri\":\"https://gravitee.io/icon\"}").mapTo(NewResource.class);
        Resource exitingRS = new Resource().setId(RESOURCE_ID).setDomain(DOMAIN_ID);
        when(repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.just(exitingRS));
        when(repository.update(exitingRS)).thenReturn(Single.just(exitingRS));
        TestObserver<Resource> testObserver = service.update(newResource, DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertComplete().assertNoErrors().assertValue(this::assertResourceValues);
    }

    @Test
    public void create_success() {
        NewResource newResource = new JsonObject("{\"resource_scopes\":[\"scope\"]}").mapTo(NewResource.class);
        when(repository.create(any())).thenReturn(Single.just(new Resource()));
        TestObserver<Resource> testObserver = service.create(newResource, DOMAIN_ID, CLIENT_ID, USER_ID).test();
        testObserver.assertComplete().assertNoErrors();
        ArgumentCaptor<Resource> rsCaptor = ArgumentCaptor.forClass(Resource.class);
        verify(repository, times(1)).create(rsCaptor.capture());
        Assert.assertTrue(this.assertResourceValues(rsCaptor.getValue()));
    }

    @Test
    public void list() {
        TestObserver<List<Resource>> testObserver = service.listByDomainAndClientAndUser(DOMAIN_ID, CLIENT_ID, USER_ID).test();
        testObserver.assertNoErrors().assertComplete().assertValue(resourceSet -> resourceSet.get(0).getId().equals(RESOURCE_ID));
    }

    @Test
    public void findByDomain_fail() {
        when(repository.findByDomain(DOMAIN_ID, 0, Integer.MAX_VALUE)).thenReturn(Single.error(new ArrayIndexOutOfBoundsException()));
        TestObserver<Set<Resource>> testObserver = service.findByDomain(DOMAIN_ID).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertError(TechnicalManagementException.class);
    }

    @Test
    public void findByDomain_success() {
        when(repository.findByDomain(DOMAIN_ID, 0, Integer.MAX_VALUE)).thenReturn(Single.just(new Page<>(Collections.singleton(new Resource()), 0, 1)));
        TestObserver<Set<Resource>> testObserver = service.findByDomain(DOMAIN_ID).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(applications -> applications.size() == 1);
    }

    private boolean assertResourceValues(Resource toValidate) {
        return toValidate!=null &&
                toValidate.getResourceScopes()!=null &&
                toValidate.getResourceScopes().size() == 1 &&
                toValidate.getResourceScopes().get(0).equals("scope") &&
                toValidate.getUpdatedAt() !=null;
    }

    //Testing straightforward CRUD methods
    @Test
    public void findByResources() {
        when(repository.findByResources(anyList())).thenReturn(Single.just(Collections.emptyList()));
        TestObserver testObserver = service.findByResources(Collections.emptyList()).test();
        testObserver.assertComplete().assertNoErrors();
        verify(repository, times(1)).findByResources(Collections.emptyList());
    }

    @Test
    public void findByDomainAndClientAndUserAndResources() {
        when(repository.findByDomainAndClientAndUserAndResources(DOMAIN_ID, CLIENT_ID, USER_ID, Collections.emptyList())).thenReturn(Single.just(Collections.emptyList()));
        TestObserver testObserver = service.findByDomainAndClientAndUserAndResources(DOMAIN_ID, CLIENT_ID, USER_ID, Collections.emptyList()).test();
        testObserver.assertComplete().assertNoErrors();
        verify(repository, times(1)).findByDomainAndClientAndUserAndResources(anyString(), anyString(), anyString(), anyList());
    }

    @Test
    public void update() {
        Date now = new Date(System.currentTimeMillis()-1000);
        Resource toUpdate = new Resource().setId(RESOURCE_ID).setDomain(DOMAIN_ID).setUpdatedAt(now);
        when(repository.update(toUpdate)).thenReturn(Single.just(toUpdate));
        TestObserver<Resource> testObserver = service.update(toUpdate).test();
        testObserver.assertComplete().assertNoErrors();
        ArgumentCaptor<Resource> rsCaptor = ArgumentCaptor.forClass(Resource.class);
        verify(repository, times(1)).update(rsCaptor.capture());
        Assert.assertTrue(rsCaptor.getValue().getUpdatedAt().after(now));
    }

    @Test
    public void delete() {
        Resource toDelete = new Resource().setId(RESOURCE_ID);
        when(repository.delete(RESOURCE_ID)).thenReturn(Completable.complete());
        TestObserver testObserver = service.delete(toDelete).test();
        testObserver.assertComplete().assertNoErrors();
        verify(repository, times(1)).delete(RESOURCE_ID);
    }
}
