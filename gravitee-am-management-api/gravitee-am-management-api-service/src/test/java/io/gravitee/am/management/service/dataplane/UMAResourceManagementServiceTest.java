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

package io.gravitee.am.management.service.dataplane;


import io.gravitee.am.dataplane.api.repository.AccessPolicyRepository;
import io.gravitee.am.dataplane.api.repository.ResourceRepository;
import io.gravitee.am.dataplane.api.repository.UserRepository;
import io.gravitee.am.management.service.dataplane.impl.UMAResourceManagementServiceImpl;
import io.gravitee.am.model.Application;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.model.uma.policy.AccessPolicy;
import io.gravitee.am.plugins.dataplane.core.DataPlaneRegistry;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.gravitee.am.service.impl.ApplicationServiceImpl;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class UMAResourceManagementServiceTest {

    @Mock
    private DataPlaneRegistry dataPlaneRegistry;

    @Mock
    private ApplicationServiceImpl applicationService;

    @Mock
    private AccessPolicyRepository accessPolicyRepository;

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UMAResourceManagementService service = new UMAResourceManagementServiceImpl();

    private static final String DOMAIN_ID = "domainId";
    private static final Domain DOMAIN = new Domain(DOMAIN_ID);
    private static final String CLIENT_ID = "clientId";
    private static final String USER_ID = "userId";
    private static final String RESOURCE_ID = "resourceId";
    private static final String POLICY_ID = "policyId";


    @BeforeEach
    public void setUp() {
        lenient().when(dataPlaneRegistry.getResourceRepository(any())).thenReturn(resourceRepository);
        lenient().when(dataPlaneRegistry.getAccessPolicyRepository(any())).thenReturn(accessPolicyRepository);
        lenient().when(dataPlaneRegistry.getUserRepository(any())).thenReturn(userRepository);
    }

    @Test
    public void findByDomain_fail() {
        when(resourceRepository.findByDomain(DOMAIN_ID)).thenReturn(Flowable.error(new ArrayIndexOutOfBoundsException()));
        final var testObserver = service.findByDomain(DOMAIN).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertError(TechnicalManagementException.class);
    }

    @Test
    public void findByDomain_success() {
        when(resourceRepository.findByDomain(DOMAIN_ID)).thenReturn(Flowable.just(new Resource(), new Resource()));
        final var testObserver = service.findByDomain(DOMAIN).test();
        testObserver.awaitDone(10, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(2);
    }

    @Test
    public void findAccessPoliciesByResources() {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setId(POLICY_ID);
        accessPolicy.setResource(RESOURCE_ID);
        accessPolicy.setDomain(DOMAIN_ID);
        List<String> resourceIds = Collections.singletonList(RESOURCE_ID);
        when(accessPolicyRepository.findByResources(resourceIds)).thenReturn(Flowable.just(accessPolicy));
        TestObserver<List<AccessPolicy>> testObserver = service.findAccessPoliciesByResources(DOMAIN, resourceIds).toList().test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(accessPolicies -> accessPolicies.size() == 1);
        verify(accessPolicyRepository, times(1)).findByResources(resourceIds);
    }

    @Test
    public void findAccessPoliciesByResources_technicalFailure() {
        List<String> resourceIds = Collections.singletonList(RESOURCE_ID);
        when(accessPolicyRepository.findByResources(resourceIds)).thenReturn(Flowable.error(RuntimeException::new));
        TestSubscriber<AccessPolicy> testObserver = service.findAccessPoliciesByResources(DOMAIN, resourceIds).test();
        testObserver.assertNotComplete().assertError(TechnicalManagementException.class);
        verify(accessPolicyRepository, times(1)).findByResources(resourceIds);
    }

    @Test
    public void findAccessPolicy_byId() {
        AccessPolicy accessPolicy = new AccessPolicy();
        accessPolicy.setId(POLICY_ID);
        accessPolicy.setResource(RESOURCE_ID);
        accessPolicy.setDomain(DOMAIN_ID);
        when(accessPolicyRepository.findById(POLICY_ID)).thenReturn(Maybe.just(accessPolicy));
        TestObserver<AccessPolicy> testObserver = service.findAccessPolicy(DOMAIN, POLICY_ID).test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(accessPolicy1 -> accessPolicy1.getId().equals(POLICY_ID));
        verify(accessPolicyRepository, times(1)).findById(POLICY_ID);
    }

    @Test
    public void findAccessPolicy_byId_technicalFailure() {
        when(accessPolicyRepository.findById(POLICY_ID)).thenReturn(Maybe.error(RuntimeException::new));
        TestObserver<AccessPolicy> testObserver = service.findAccessPolicy(DOMAIN, POLICY_ID).test();
        testObserver.assertNotComplete().assertError(TechnicalManagementException.class);
        verify(accessPolicyRepository, times(1)).findById(POLICY_ID);
    }

    @Test
    public void findByDomainAndClientResource() {
        when(resourceRepository.findByDomainAndClientAndResources(eq(DOMAIN_ID), eq(CLIENT_ID), anyList())).thenReturn(Flowable.empty());
        TestObserver testObserver = service.findByDomainAndClientResource(DOMAIN, CLIENT_ID, RESOURCE_ID).test();
        testObserver.assertComplete().assertNoErrors();
        verify(resourceRepository, times(1)).findByDomainAndClientAndResources(eq(DOMAIN_ID), eq(CLIENT_ID), anyList());
    }

    @Test
    public void findByDomainAndClient() {
        when(resourceRepository.findByDomainAndClient(DOMAIN_ID, CLIENT_ID, 0, Integer.MAX_VALUE)).thenReturn(Single.just(new Page<>(Collections.emptyList(), 0, 0)));
        TestObserver testObserver = service.findByDomainAndClient(DOMAIN, CLIENT_ID, 0, Integer.MAX_VALUE).test();
        testObserver.assertComplete().assertNoErrors();
        verify(resourceRepository, times(1)).findByDomainAndClient(DOMAIN_ID, CLIENT_ID, 0, Integer.MAX_VALUE);
    }

    @Test
    public void findByDomainAndClient_technicalFailure() {
        when(resourceRepository.findByDomainAndClient(DOMAIN_ID, CLIENT_ID, 0, Integer.MAX_VALUE)).thenReturn(Single.error(RuntimeException::new));
        TestObserver testObserver = service.findByDomainAndClient(DOMAIN, CLIENT_ID, 0, Integer.MAX_VALUE).test();
        testObserver.assertNotComplete().assertError(TechnicalManagementException.class);
        verify(resourceRepository, times(1)).findByDomainAndClient(DOMAIN_ID, CLIENT_ID, 0, Integer.MAX_VALUE);
    }


    @Test
    public void countAccessPolicyByResource() {
        when(accessPolicyRepository.countByResource(RESOURCE_ID)).thenReturn(Single.just(1l));
        TestObserver<Long> testObserver = service.countAccessPolicyByResource(DOMAIN, RESOURCE_ID).test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(accessPolicies -> accessPolicies == 1l);
        verify(accessPolicyRepository, times(1)).countByResource(RESOURCE_ID);
    }

    @Test
    public void countAccessPolicyByResource_technicalFailure() {
        when(accessPolicyRepository.countByResource(RESOURCE_ID)).thenReturn(Single.error(RuntimeException::new));
        TestObserver<Long> testObserver = service.countAccessPolicyByResource(DOMAIN, RESOURCE_ID).test();
        testObserver.assertNotComplete().assertError(TechnicalManagementException.class);
        verify(accessPolicyRepository, times(1)).countByResource(RESOURCE_ID);
    }

    @Test
    public void getMetadata_noResources_null() {
        TestObserver<Map<String, Map<String, Object>>> testObserver = service.getMetadata(DOMAIN, null).test();
        testObserver.assertComplete().assertNoErrors();
        testObserver.assertValue(map -> map.isEmpty());
    }

    @Test
    public void getMetadata_noResources_empty() {
        TestObserver<Map<String, Map<String, Object>>> testObserver = service.getMetadata(DOMAIN, Collections.emptyList()).test();
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

        when(userRepository.findByIdIn(anyList())).thenReturn(Flowable.just(new User()));
        when(applicationService.findByIdIn(anyList())).thenReturn(Flowable.just(new Application()));
        TestObserver<Map<String, Map<String, Object>>> testObserver = service.getMetadata(DOMAIN, resources).test();
        testObserver.assertComplete().assertNoErrors();
    }
}
