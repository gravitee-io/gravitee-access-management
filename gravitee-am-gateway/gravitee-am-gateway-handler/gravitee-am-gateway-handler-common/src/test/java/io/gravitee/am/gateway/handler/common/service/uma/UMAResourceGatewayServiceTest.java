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

package io.gravitee.am.gateway.handler.common.service.uma;


import io.gravitee.am.dataplane.api.repository.AccessPolicyRepository;
import io.gravitee.am.dataplane.api.repository.ResourceRepository;
import io.gravitee.am.gateway.handler.common.service.uma.impl.UMAResourceGatewayServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.oauth2.Scope;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.model.uma.policy.AccessPolicy;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.ScopeService;
import io.gravitee.am.service.exception.AccessPolicyNotFoundException;
import io.gravitee.am.service.exception.MalformedIconUriException;
import io.gravitee.am.service.exception.MissingScopeException;
import io.gravitee.am.service.exception.ResourceNotFoundException;
import io.gravitee.am.service.exception.ScopeNotFoundException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.model.NewResource;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import io.vertx.core.json.JsonObject;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class UMAResourceGatewayServiceTest {

    @Mock
    private ScopeService scopeService;

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private AccessPolicyRepository accessPolicyRepository;

    @Mock
    private DataPlaneRegistry dataPlaneRegistry;

    private UMAResourceGatewayServiceImpl service;

    private static final String DOMAIN_ID = "domainId";
    private static final String CLIENT_ID = "clientId";
    private static final String USER_ID = "userId";
    private static final String RESOURCE_ID = "resourceId";
    private static final String POLICY_ID = "policyId";

    private final Domain DOMAIN = new Domain(DOMAIN_ID);

    @BeforeEach
    public void setUp() throws Exception {
        lenient().when(dataPlaneRegistry.getResourceRepository(any())).thenReturn(resourceRepository);
        lenient().when(dataPlaneRegistry.getAccessPolicyRepository(any())).thenReturn(accessPolicyRepository);
        lenient().when(resourceRepository.findByDomainAndClientAndUser(DOMAIN_ID, CLIENT_ID, USER_ID)).thenReturn(Flowable.just(new Resource().setId(RESOURCE_ID)));
        lenient().when(resourceRepository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.just(new Resource().setId(RESOURCE_ID)));
        lenient().when(scopeService.findByDomainAndKeys(DOMAIN_ID, Arrays.asList("scope"))).thenReturn(Single.just(Arrays.asList(new Scope("scope"))));

        this.service = new UMAResourceGatewayServiceImpl(DOMAIN, dataPlaneRegistry, scopeService);
        this.service.afterPropertiesSet();
    }

    @Test
    public void findAll_fail() {
        when(resourceRepository.findByDomain(DOMAIN_ID, 0, Integer.MAX_VALUE)).thenReturn(Single.error(new ArrayIndexOutOfBoundsException("error for test")));
        TestObserver<Page<Resource>> testObserver = service.findAll(0, Integer.MAX_VALUE).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(TechnicalManagementException.class);
    }

    @Test
    public void findAll_success() {
        when(resourceRepository.findByDomain(DOMAIN_ID, 0, Integer.MAX_VALUE)).thenReturn(Single.just(new Page<>(Collections.singleton(new Resource()), 0, 1)));
        TestObserver<Page<Resource>> testObserver = service.findAll(0, Integer.MAX_VALUE).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(page -> page.getTotalCount() == 1 && page.getData().size() == 1);
    }

    @Test
    public void findByResources() {
        when(resourceRepository.findByResources(anyList())).thenReturn(Flowable.empty());
        TestSubscriber testObserver = service.findByResources(Collections.emptyList()).test();
        testObserver.assertComplete().assertNoErrors();
        verify(resourceRepository, times(1)).findByResources(Collections.emptyList());
    }

    @Test
    public void list() {
        TestSubscriber<Resource> testSubscriber = service.listByClientAndUser(CLIENT_ID, USER_ID).test();
        testSubscriber
                .assertNoErrors()
                .assertComplete()
                .assertValue(resourceSet -> resourceSet.getId().equals(RESOURCE_ID));
    }

    @Test
    public void should_findByClientAndResources() {
        when(resourceRepository.findByDomainAndClientAndResources(DOMAIN_ID, CLIENT_ID, Collections.emptyList())).thenReturn(Flowable.empty());
        TestSubscriber testSubscriber = service.findByClientAndResources(CLIENT_ID, Collections.emptyList()).test();
        testSubscriber.assertComplete().assertNoErrors();
        verify(resourceRepository, times(1)).findByDomainAndClientAndResources(anyString(), anyString(), anyList());
    }

    @Test
    public void should_findByClientAndUserAndResource() {
        when(resourceRepository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.just(new Resource()));
        final var testSubscriber = service.findByClientAndUserAndResource(CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testSubscriber.assertComplete()
                .assertNoErrors()
                .assertValueCount(1);
        verify(resourceRepository, times(1)).findByDomainAndClientAndUserAndResource(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    public void create_success() {
        NewResource newResource = new JsonObject("{\"resource_scopes\":[\"scope\"]}").mapTo(NewResource.class);
        when(resourceRepository.create(any())).thenReturn(Single.just(new Resource()));
        when(accessPolicyRepository.create(any())).thenReturn(Single.just(new AccessPolicy()));
        TestObserver<Resource> testObserver = service.create(newResource, CLIENT_ID, USER_ID).test();
        testObserver.assertComplete().assertNoErrors();
        ArgumentCaptor<Resource> rsCaptor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceRepository, times(1)).create(rsCaptor.capture());
        verify(accessPolicyRepository, times(1)).create(any());
        Assert.assertTrue(this.assertResourceValues(rsCaptor.getValue()));
    }


    @Test
    public void update_nonExistingResource() {
        when(resourceRepository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.empty());
        TestObserver testObserver = service.update(new NewResource(), CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertError(ResourceNotFoundException.class);
    }

    @Test
    public void update_scopeMissing() {
        NewResource newResource = new JsonObject("{\"resource_scopes\":[]}").mapTo(NewResource.class);
        Resource exitingRS = new Resource().setId(RESOURCE_ID).setDomain(DOMAIN_ID);
        when(resourceRepository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.just(exitingRS));
        TestObserver<Resource> testObserver = service.update(newResource, CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertError(MissingScopeException.class);
        verify(resourceRepository, times(0)).update(any());
    }

    @Test
    public void update_scopeNotFound() {
        NewResource newResource = new JsonObject("{\"resource_scopes\":[\"scope\"]}").mapTo(NewResource.class);
        Resource exitingRS = new Resource().setId(RESOURCE_ID).setDomain(DOMAIN_ID);
        when(scopeService.findByDomainAndKeys(DOMAIN_ID, Arrays.asList("scope"))).thenReturn(Single.just(Collections.emptyList()));
        when(resourceRepository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.just(exitingRS));
        TestObserver<Resource> testObserver = service.update(newResource, CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertError(ScopeNotFoundException.class);
        verify(resourceRepository, times(0)).update(any());
    }

    @Test
    public void update_malformedIconUri() {
        NewResource newResource = new JsonObject("{\"resource_scopes\":[\"scope\"],\"icon_uri\":\"badIconUriFormat\"}").mapTo(NewResource.class);
        Resource exitingRS = new Resource().setId(RESOURCE_ID).setDomain(DOMAIN_ID);
        when(resourceRepository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.just(exitingRS));
        TestObserver<Resource> testObserver = service.update(newResource, CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertError(MalformedIconUriException.class);
        verify(resourceRepository, times(0)).update(any());
    }

    @Test
    public void update_existingResource() {
        NewResource newResource = new JsonObject("{\"resource_scopes\":[\"scope\"],\"icon_uri\":\"https://gravitee.io/icon\"}").mapTo(NewResource.class);
        Resource exitingRS = new Resource().setId(RESOURCE_ID).setDomain(DOMAIN_ID);
        when(resourceRepository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.just(exitingRS));
        when(resourceRepository.update(exitingRS)).thenReturn(Single.just(exitingRS));
        TestObserver<Resource> testObserver = service.update(newResource, CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertComplete().assertNoErrors().assertValue(this::assertResourceValues);
    }

    @Test
    public void delete_nonExistingResource() {
        when(resourceRepository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.empty());
        TestObserver testObserver = service.delete(CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertError(ResourceNotFoundException.class);
    }

    @Test
    public void delete_existingResource() {
        when(resourceRepository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.just(new Resource().setId(RESOURCE_ID)));
        when(resourceRepository.delete(RESOURCE_ID)).thenReturn(Completable.complete());
        TestObserver testObserver = service.delete(CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertComplete().assertNoErrors().assertNoValues();
    }


    @Test
    public void findAccessPolicies() {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setId("policy-id");
        accessPolicy.setResource(RESOURCE_ID);
        accessPolicy.setDomain(DOMAIN_ID);
        when(accessPolicyRepository.findByDomainAndResource(DOMAIN_ID, RESOURCE_ID)).thenReturn(Flowable.just(accessPolicy));
        TestObserver<List<AccessPolicy>> testObserver = service.findAccessPolicies(CLIENT_ID, USER_ID, RESOURCE_ID).toList().test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(accessPolicies -> accessPolicies.size() == 1);
        verify(resourceRepository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, times(1)).findByDomainAndResource(DOMAIN_ID, RESOURCE_ID);
    }

    @Test
    public void findAccessPolicies_resourceNotFound() {
        when(resourceRepository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.empty());
        TestSubscriber<AccessPolicy> testSubscriber = service.findAccessPolicies(CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testSubscriber.assertNotComplete().assertError(ResourceNotFoundException.class);
        verify(resourceRepository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, never()).findByDomainAndResource(DOMAIN_ID, RESOURCE_ID);
    }

    @Test
    public void findAccessPolicies_technicalFailure() {
        when(resourceRepository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.error(RuntimeException::new));
        TestSubscriber<AccessPolicy> testSubscriber = service.findAccessPolicies(CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testSubscriber.assertNotComplete().assertError(TechnicalManagementException.class);
        verify(resourceRepository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
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
    public void findAccessPolicy_resourceNotFound() {
        when(resourceRepository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.empty());
        TestObserver<AccessPolicy> testObserver = service.findAccessPolicy(CLIENT_ID, USER_ID, RESOURCE_ID, POLICY_ID).test();
        testObserver.assertNotComplete().assertError(ResourceNotFoundException.class);
        verify(resourceRepository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, never()).findById(POLICY_ID);
    }

    @Test
    public void findAccessPolicy_technicalFailure() {
        when(resourceRepository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.error(RuntimeException::new));
        TestObserver<AccessPolicy> testObserver = service.findAccessPolicy(CLIENT_ID, USER_ID, RESOURCE_ID, POLICY_ID).test();
        testObserver.assertNotComplete().assertError(TechnicalManagementException.class);
        verify(resourceRepository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, never()).findById(POLICY_ID);
    }

    @Test
    public void createAccessPolicy() {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setId(POLICY_ID);
        accessPolicy.setResource(RESOURCE_ID);
        accessPolicy.setDomain(DOMAIN_ID);
        when(accessPolicyRepository.create(accessPolicy)).thenReturn(Single.just(accessPolicy));
        TestObserver<AccessPolicy> testObserver = service.createAccessPolicy(accessPolicy, CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(accessPolicy1 -> accessPolicy1.getId().equals(POLICY_ID));
        verify(resourceRepository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, times(1)).create(accessPolicy);
    }

    @Test
    public void createAccessPolicy_resourceNotFound() {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setId(POLICY_ID);
        accessPolicy.setResource(RESOURCE_ID);
        accessPolicy.setDomain(DOMAIN_ID);
        when(resourceRepository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.empty());
        TestObserver<AccessPolicy> testObserver = service.createAccessPolicy(accessPolicy, CLIENT_ID, USER_ID, RESOURCE_ID).test();
        testObserver.assertNotComplete().assertError(ResourceNotFoundException.class);
        verify(resourceRepository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
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
        TestObserver<AccessPolicy> testObserver = service.updateAccessPolicy(accessPolicy, CLIENT_ID, USER_ID, RESOURCE_ID, POLICY_ID).test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(accessPolicy1 -> accessPolicy1.getId().equals(POLICY_ID));
        verify(resourceRepository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, times(1)).findById(POLICY_ID);
        verify(accessPolicyRepository, times(1)).update(any());
    }

    @Test
    public void updateAccessPolicy_resourceNotFound() {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setId(POLICY_ID);
        accessPolicy.setResource(RESOURCE_ID);
        accessPolicy.setDomain(DOMAIN_ID);
        when(resourceRepository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.empty());
        TestObserver<AccessPolicy> testObserver = service.updateAccessPolicy(accessPolicy, CLIENT_ID, USER_ID, RESOURCE_ID, POLICY_ID).test();
        testObserver.assertNotComplete().assertError(ResourceNotFoundException.class);
        verify(resourceRepository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
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
        TestObserver<AccessPolicy> testObserver = service.updateAccessPolicy(accessPolicy, CLIENT_ID, USER_ID, RESOURCE_ID, POLICY_ID).test();
        testObserver.assertNotComplete().assertError(AccessPolicyNotFoundException.class);
        verify(resourceRepository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, times(1)).findById(POLICY_ID);
        verify(accessPolicyRepository, never()).update(any());
    }

    @Test
    public void deleteAccessPolicy() {
        when(accessPolicyRepository.delete(POLICY_ID)).thenReturn(Completable.complete());
        TestObserver testObserver = service.deleteAccessPolicy(CLIENT_ID, USER_ID, RESOURCE_ID, POLICY_ID).test();
        testObserver.assertComplete().assertNoErrors();
        verify(resourceRepository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, times(1)).delete(POLICY_ID);
    }

    @Test
    public void deleteAccessPolicy_resourceNotFound() {
        when(resourceRepository.findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID)).thenReturn(Maybe.empty());
        TestObserver testObserver = service.deleteAccessPolicy(CLIENT_ID, USER_ID, RESOURCE_ID, POLICY_ID).test();
        testObserver.assertNotComplete().assertError(ResourceNotFoundException.class);
        verify(resourceRepository, times(1)).findByDomainAndClientAndUserAndResource(DOMAIN_ID, CLIENT_ID, USER_ID, RESOURCE_ID);
        verify(accessPolicyRepository, never()).delete(POLICY_ID);
    }

    private boolean assertResourceValues(Resource toValidate) {
        return toValidate!=null &&
                toValidate.getResourceScopes()!=null &&
                toValidate.getResourceScopes().size() == 1 &&
                toValidate.getResourceScopes().get(0).equals("scope") &&
                toValidate.getUpdatedAt() !=null;
    }
}
