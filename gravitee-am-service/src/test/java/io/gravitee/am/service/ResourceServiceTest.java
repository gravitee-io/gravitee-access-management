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

import io.gravitee.am.model.Application;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.model.uma.policy.AccessPolicy;
import io.gravitee.am.repository.management.api.AccessPolicyRepository;
import io.gravitee.am.repository.management.api.ResourceRepository;
import io.gravitee.am.service.exception.*;
import io.gravitee.am.service.impl.ResourceServiceImpl;
import io.gravitee.am.service.model.NewResource;
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
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
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ResourceServiceTest {

    @Mock
    private ResourceRepository repository;

    @Mock
    private AccessPolicyRepository accessPolicyRepository;

    @Mock
    private ScopeService scopeService;

    @Mock
    private UserService userService;

    @Mock
    private ApplicationService applicationService;

    @InjectMocks
    private ResourceService service = new ResourceServiceImpl();

    private static final String DOMAIN_ID = "domainId";
    private static final String CLIENT_ID = "clientId";
    private static final String USER_ID = "userId";
    private static final String RESOURCE_ID = "resourceId";
    private static final String POLICY_ID = "policyId";

    @Before
    public void setUp() {
        when(repository.findByDomainAndClientAndUser(DOMAIN_ID, CLIENT_ID, USER_ID)).thenReturn(Flowable.just(new Resource().setId(RESOURCE_ID)));
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
        when(accessPolicyRepository.create(any())).thenReturn(Single.just(new AccessPolicy()));
        TestObserver<Resource> testObserver = service.create(newResource, DOMAIN_ID, CLIENT_ID, USER_ID).test();
        testObserver.assertComplete().assertNoErrors();
        ArgumentCaptor<Resource> rsCaptor = ArgumentCaptor.forClass(Resource.class);
        verify(repository, times(1)).create(rsCaptor.capture());
        verify(accessPolicyRepository, times(1)).create(any());
        Assert.assertTrue(this.assertResourceValues(rsCaptor.getValue()));
    }

    @Test
    public void list() {
        TestSubscriber<Resource> testSubscriber = service.listByDomainAndClientAndUser(DOMAIN_ID, CLIENT_ID, USER_ID).test();
        testSubscriber
                .assertNoErrors()
                .assertComplete()
                .assertValue(resourceSet -> resourceSet.getId().equals(RESOURCE_ID));
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
        when(repository.findByResources(anyList())).thenReturn(Flowable.empty());
        TestSubscriber testObserver = service.findByResources(Collections.emptyList()).test();
        testObserver.assertComplete().assertNoErrors();
        verify(repository, times(1)).findByResources(Collections.emptyList());
    }

    @Test
    public void findByDomainAndClient() {
        when(repository.findByDomainAndClient(DOMAIN_ID, CLIENT_ID, 0, Integer.MAX_VALUE)).thenReturn(Single.just(new Page<>(Collections.emptyList(), 0, 0)));
        TestObserver testObserver = service.findByDomainAndClient(DOMAIN_ID, CLIENT_ID, 0, Integer.MAX_VALUE).test();
        testObserver.assertComplete().assertNoErrors();
        verify(repository, times(1)).findByDomainAndClient(DOMAIN_ID, CLIENT_ID, 0, Integer.MAX_VALUE);
    }

    @Test
    public void findByDomainAndClient_technicalFailure() {
        when(repository.findByDomainAndClient(DOMAIN_ID, CLIENT_ID, 0, Integer.MAX_VALUE)).thenReturn(Single.error(RuntimeException::new));
        TestObserver testObserver = service.findByDomainAndClient(DOMAIN_ID, CLIENT_ID, 0, Integer.MAX_VALUE).test();
        testObserver.assertNotComplete().assertError(TechnicalManagementException.class);
        verify(repository, times(1)).findByDomainAndClient(DOMAIN_ID, CLIENT_ID, 0, Integer.MAX_VALUE);
    }

    @Test
    public void findByDomainAndClientAndResources() {
        when(repository.findByDomainAndClientAndResources(DOMAIN_ID, CLIENT_ID, Collections.emptyList())).thenReturn(Flowable.empty());
        TestSubscriber testSubscriber = service.findByDomainAndClientAndResources(DOMAIN_ID, CLIENT_ID, Collections.emptyList()).test();
        testSubscriber.assertComplete().assertNoErrors();
        verify(repository, times(1)).findByDomainAndClientAndResources(anyString(), anyString(), anyList());
    }

    @Test
    public void findByDomainAndClientResource() {
        when(repository.findByDomainAndClientAndResources(eq(DOMAIN_ID), eq(CLIENT_ID), anyList())).thenReturn(Flowable.empty());
        TestObserver testObserver = service.findByDomainAndClientResource(DOMAIN_ID, CLIENT_ID, RESOURCE_ID).test();
        testObserver.assertComplete().assertNoErrors();
        verify(repository, times(1)).findByDomainAndClientAndResources(eq(DOMAIN_ID), eq(CLIENT_ID), anyList());
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
        Resource toDelete = new Resource().setId(RESOURCE_ID).setDomain(DOMAIN_ID);
        when(accessPolicyRepository.findByDomainAndResource(toDelete.getDomain(), toDelete.getId())).thenReturn(Flowable.empty());
        when(repository.delete(RESOURCE_ID)).thenReturn(Completable.complete());
        TestObserver testObserver = service.delete(toDelete).test();
        testObserver.assertComplete().assertNoErrors();
        verify(repository, times(1)).delete(RESOURCE_ID);
    }

    @Test
    public void findAccessPolicies() {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setId("policy-id");
        accessPolicy.setResource(RESOURCE_ID);
        accessPolicy.setDomain(DOMAIN_ID);
        when(accessPolicyRepository.findByDomainAndResource(DOMAIN_ID, RESOURCE_ID)).thenReturn(Flowable.just(accessPolicy));
        TestObserver<List<AccessPolicy>> testObserver = service.findAccessPolicies(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID).toList().test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(accessPolicies -> accessPolicies.size() == 1);
        verify(repository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, times(1)).findByDomainAndResource(DOMAIN_ID, RESOURCE_ID);
    }

    @Test
    public void findAccessPolicies_resourceNotFound() {
        when(repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.empty());
        TestSubscriber<AccessPolicy> testSubscriber = service.findAccessPolicies(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testSubscriber.assertNotComplete().assertError(ResourceNotFoundException.class);
        verify(repository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, never()).findByDomainAndResource(DOMAIN_ID, RESOURCE_ID);
    }

    @Test
    public void findAccessPolicies_technicalFailure() {
        when(repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.error(RuntimeException::new));
        TestSubscriber<AccessPolicy> testSubscriber = service.findAccessPolicies(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testSubscriber.assertNotComplete().assertError(TechnicalManagementException.class);
        verify(repository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, never()).findByDomainAndResource(DOMAIN_ID, RESOURCE_ID);
    }

    @Test
    public void findAccessPoliciesByResources() {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setId(POLICY_ID);
        accessPolicy.setResource(RESOURCE_ID);
        accessPolicy.setDomain(DOMAIN_ID);
        List<String> resourceIds = Collections.singletonList(RESOURCE_ID);
        when(accessPolicyRepository.findByResources(resourceIds)).thenReturn(Flowable.just(accessPolicy));
        TestObserver<List<AccessPolicy>> testObserver = service.findAccessPoliciesByResources(resourceIds).toList().test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(accessPolicies -> accessPolicies.size() == 1);
        verify(accessPolicyRepository, times(1)).findByResources(resourceIds);
    }

    @Test
    public void findAccessPoliciesByResources_technicalFailure() {
        List<String> resourceIds = Collections.singletonList(RESOURCE_ID);
        when(accessPolicyRepository.findByResources(resourceIds)).thenReturn(Flowable.error(RuntimeException::new));
        TestSubscriber<AccessPolicy> testObserver = service.findAccessPoliciesByResources(resourceIds).test();
        testObserver.assertNotComplete().assertError(TechnicalManagementException.class);
        verify(accessPolicyRepository, times(1)).findByResources(resourceIds);
    }

    @Test
    public void countAccessPolicyByResource() {
        when(accessPolicyRepository.countByResource(RESOURCE_ID)).thenReturn(Single.just(1l));
        TestObserver<Long> testObserver = service.countAccessPolicyByResource(RESOURCE_ID).test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(accessPolicies -> accessPolicies == 1l);
        verify(accessPolicyRepository, times(1)).countByResource(RESOURCE_ID);
    }

    @Test
    public void countAccessPolicyByResource_technicalFailure() {
        when(accessPolicyRepository.countByResource(RESOURCE_ID)).thenReturn(Single.error(RuntimeException::new));
        TestObserver<Long> testObserver = service.countAccessPolicyByResource(RESOURCE_ID).test();
        testObserver.assertNotComplete().assertError(TechnicalManagementException.class);
        verify(accessPolicyRepository, times(1)).countByResource(RESOURCE_ID);
    }

    @Test
    public void findAccessPolicy() {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setId(POLICY_ID);
        accessPolicy.setResource(RESOURCE_ID);
        accessPolicy.setDomain(DOMAIN_ID);
        when(accessPolicyRepository.findById(POLICY_ID)).thenReturn(Maybe.just(accessPolicy));
        TestObserver<AccessPolicy> testObserver = service.findAccessPolicy(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID, POLICY_ID).test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(accessPolicy1 -> accessPolicy1.getId().equals(POLICY_ID));
        verify(repository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, times(1)).findById(POLICY_ID);
    }

    @Test
    public void findAccessPolicy_resourceNotFound() {
        when(repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.empty());
        TestObserver<AccessPolicy> testObserver = service.findAccessPolicy(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID, POLICY_ID).test();
        testObserver.assertNotComplete().assertError(ResourceNotFoundException.class);
        verify(repository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, never()).findById(POLICY_ID);
    }

    @Test
    public void findAccessPolicy_technicalFailure() {
        when(repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.error(RuntimeException::new));
        TestObserver<AccessPolicy> testObserver = service.findAccessPolicy(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID, POLICY_ID).test();
        testObserver.assertNotComplete().assertError(TechnicalManagementException.class);
        verify(repository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, never()).findById(POLICY_ID);
    }

    @Test
    public void findAccessPolicy_byId() {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setId(POLICY_ID);
        accessPolicy.setResource(RESOURCE_ID);
        accessPolicy.setDomain(DOMAIN_ID);
        when(accessPolicyRepository.findById(POLICY_ID)).thenReturn(Maybe.just(accessPolicy));
        TestObserver<AccessPolicy> testObserver = service.findAccessPolicy(POLICY_ID).test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(accessPolicy1 -> accessPolicy1.getId().equals(POLICY_ID));
        verify(accessPolicyRepository, times(1)).findById(POLICY_ID);
    }

    @Test
    public void findAccessPolicy_byId_technicalFailure() {
        when(accessPolicyRepository.findById(POLICY_ID)).thenReturn(Maybe.error(RuntimeException::new));
        TestObserver<AccessPolicy> testObserver = service.findAccessPolicy(POLICY_ID).test();
        testObserver.assertNotComplete().assertError(TechnicalManagementException.class);
        verify(accessPolicyRepository, times(1)).findById(POLICY_ID);
    }

    @Test
    public void createAccessPolicy() {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setId(POLICY_ID);
        accessPolicy.setResource(RESOURCE_ID);
        accessPolicy.setDomain(DOMAIN_ID);
        when(accessPolicyRepository.create(accessPolicy)).thenReturn(Single.just(accessPolicy));
        TestObserver<AccessPolicy> testObserver = service.createAccessPolicy(accessPolicy, DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(accessPolicy1 -> accessPolicy1.getId().equals(POLICY_ID));
        verify(repository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, times(1)).create(accessPolicy);
    }

    @Test
    public void createAccessPolicy_resourceNotFound() {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setId(POLICY_ID);
        accessPolicy.setResource(RESOURCE_ID);
        accessPolicy.setDomain(DOMAIN_ID);
        when(repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.empty());
        TestObserver<AccessPolicy> testObserver = service.createAccessPolicy(accessPolicy, DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertNotComplete().assertError(ResourceNotFoundException.class);
        verify(repository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, never()).create(accessPolicy);
    }

    @Test
    public void updateAccessPolicy() {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setId(POLICY_ID);
        accessPolicy.setResource(RESOURCE_ID);
        accessPolicy.setDomain(DOMAIN_ID);
        when(accessPolicyRepository.findById(POLICY_ID)).thenReturn(Maybe.just(accessPolicy));
        when(accessPolicyRepository.update(any())).thenReturn(Single.just(accessPolicy));
        TestObserver<AccessPolicy> testObserver = service.updateAccessPolicy(accessPolicy, DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID, POLICY_ID).test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(accessPolicy1 -> accessPolicy1.getId().equals(POLICY_ID));
        verify(repository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, times(1)).findById(POLICY_ID);
        verify(accessPolicyRepository, times(1)).update(any());
    }

    @Test
    public void updateAccessPolicy_resourceNotFound() {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setId(POLICY_ID);
        accessPolicy.setResource(RESOURCE_ID);
        accessPolicy.setDomain(DOMAIN_ID);
        when(repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.empty());
        TestObserver<AccessPolicy> testObserver = service.updateAccessPolicy(accessPolicy, DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID, POLICY_ID).test();
        testObserver.assertNotComplete().assertError(ResourceNotFoundException.class);
        verify(repository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, never()).findById(POLICY_ID);
        verify(accessPolicyRepository, never()).update(any());
    }

    @Test
    public void updateAccessPolicy_policyNotFound() {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setId(POLICY_ID);
        accessPolicy.setResource(RESOURCE_ID);
        accessPolicy.setDomain(DOMAIN_ID);
        when(accessPolicyRepository.findById(POLICY_ID)).thenReturn(Maybe.empty());
        TestObserver<AccessPolicy> testObserver = service.updateAccessPolicy(accessPolicy, DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID, POLICY_ID).test();
        testObserver.assertNotComplete().assertError(AccessPolicyNotFoundException.class);
        verify(repository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, times(1)).findById(POLICY_ID);
        verify(accessPolicyRepository, never()).update(any());
    }

    @Test
    public void deleteAccessPolicy() {
        when(accessPolicyRepository.delete(POLICY_ID)).thenReturn(Completable.complete());
        TestObserver testObserver = service.deleteAccessPolicy(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID, POLICY_ID).test();
        testObserver.assertComplete().assertNoErrors();
        verify(repository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, times(1)).delete(POLICY_ID);
    }

    @Test
    public void deleteAccessPolicy_resourceNotFound() {
        when(repository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.empty());
        TestObserver testObserver = service.deleteAccessPolicy(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID, POLICY_ID).test();
        testObserver.assertNotComplete().assertError(ResourceNotFoundException.class);
        verify(repository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, never()).delete(POLICY_ID);
    }

    @Test
    public void getMetadata_noResources_null() {
        TestObserver<Map<String, Map<String, Object>>> testObserver = service.getMetadata(null).test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(map -> map.isEmpty());
    }

    @Test
    public void getMetadata_noResources_empty() {
        TestObserver<Map<String, Map<String, Object>>> testObserver = service.getMetadata(Collections.emptyList()).test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(map -> map.isEmpty());
    }

    @Test
    public void getMetadata_resources() {
        Resource resource = new Resource();
        resource.setDomain(DOMAIN_ID);
        resource.setClientId(CLIENT_ID);
        resource.setUserId(USER_ID);
        List<Resource> resources = Collections.singletonList(resource);

        when(userService.findByIdIn(anyList())).thenReturn(Flowable.just(new User()));
        when(applicationService.findByIdIn(anyList())).thenReturn(Flowable.just(new Application()));
        TestObserver<Map<String, Map<String, Object>>> testObserver = service.getMetadata(resources).test();
        testObserver.assertComplete().assertNoErrors();
    }
}
