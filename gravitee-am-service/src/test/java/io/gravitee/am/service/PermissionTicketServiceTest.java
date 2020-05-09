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

import io.gravitee.am.model.uma.PermissionRequest;
import io.gravitee.am.model.uma.PermissionTicket;
import io.gravitee.am.model.uma.ResourceSet;
import io.gravitee.am.repository.management.api.PermissionTicketRepository;
import io.gravitee.am.service.exception.InvalidPermissionRequestException;
import io.gravitee.am.service.impl.PermissionTicketServiceImpl;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class PermissionTicketServiceTest {

    @Mock
    PermissionTicketRepository repository;

    @Mock
    private ResourceSetService resourceSetService;

    @InjectMocks
    PermissionTicketService service = new PermissionTicketServiceImpl();

    private static final String DOMAIN_ID = "domainId";
    private static final String CLIENT_ID = "clientId";
    private static final String USER_ID = "userId";

    @Test
    public void create_errorSingleResource_missingResourceSet() {
        //Prepare request & resource
        List<PermissionRequest> request = Arrays.asList(new PermissionRequest().setResourceId("one").setResourceScopes(Arrays.asList("a","b")));

        when(resourceSetService.findByDomainAndClientAndUserAndResources(DOMAIN_ID, CLIENT_ID, USER_ID,Arrays.asList("one"))).thenReturn(Single.just(Collections.emptyList()));
        TestObserver<PermissionTicket> testObserver = service.create(request, DOMAIN_ID, CLIENT_ID, USER_ID).test();

        testObserver.assertNotComplete();
        testObserver.assertError(err -> ((InvalidPermissionRequestException)err).getOAuth2ErrorCode().equals("invalid_resource_id"));
        verify(repository, times(0)).create(any());
    }

    @Test
    public void create_errorSingleResource_missingScope() {
        //Prepare request & resource
        List<PermissionRequest> request = Arrays.asList(new PermissionRequest().setResourceId("one").setResourceScopes(Arrays.asList("a","b")));
        List<ResourceSet> found = Arrays.asList(new ResourceSet().setId("one").setResourceScopes(Arrays.asList("not","same")));

        when(resourceSetService.findByDomainAndClientAndUserAndResources(DOMAIN_ID, CLIENT_ID, USER_ID,Arrays.asList("one"))).thenReturn(Single.just(found));
        TestObserver<PermissionTicket> testObserver = service.create(request, DOMAIN_ID, CLIENT_ID, USER_ID).test();

        testObserver.assertNotComplete();
        testObserver.assertError(err -> ((InvalidPermissionRequestException)err).getOAuth2ErrorCode().equals("invalid_scope"));
        verify(repository, times(0)).create(any());
    }

    @Test
    public void create_successSingleResource() {
        //Prepare request & resource
        List<PermissionRequest> request = Arrays.asList(new PermissionRequest().setResourceId("one").setResourceScopes(Arrays.asList("a","b")));
        List<ResourceSet> found = Arrays.asList(new ResourceSet().setId("one").setResourceScopes(Arrays.asList("a","b")));

        when(resourceSetService.findByDomainAndClientAndUserAndResources(DOMAIN_ID, CLIENT_ID, USER_ID,Arrays.asList("one"))).thenReturn(Single.just(found));
        when(repository.create(any())).thenReturn(Single.just(new PermissionTicket().setId("success")));

        TestObserver<PermissionTicket> testObserver = service.create(request, DOMAIN_ID, CLIENT_ID, USER_ID).test();

        testObserver.assertNoErrors().assertComplete().assertValue(permissionTicket -> "success".equals(permissionTicket.getId()));
        verify(repository, times(1)).create(any());
    }

    @Test
    public void create_errorMultipleResource_missingResourceSet() {
        //Prepare request
        List<PermissionRequest> request = Arrays.asList(
                new PermissionRequest().setResourceId("one").setResourceScopes(Arrays.asList("a","b")),
                new PermissionRequest().setResourceId("two").setResourceScopes(Arrays.asList("c","d"))
        );

        // Prepare Resource Set
        List<ResourceSet> found = Arrays.asList(new ResourceSet().setId("one").setResourceScopes(Arrays.asList("not","same")));

        when(resourceSetService.findByDomainAndClientAndUserAndResources(DOMAIN_ID, CLIENT_ID, USER_ID,Arrays.asList("one","two"))).thenReturn(Single.just(found));
        TestObserver<PermissionTicket> testObserver = service.create(request, DOMAIN_ID, CLIENT_ID, USER_ID).test();

        testObserver.assertNotComplete();
        testObserver.assertError(err -> ((InvalidPermissionRequestException)err).getOAuth2ErrorCode().equals("invalid_resource_id"));
        verify(repository, times(0)).create(any());
    }

    @Test
    public void create_errorMultipleResource_missingScope() {
        //Prepare request
        List<PermissionRequest> request = Arrays.asList(
                new PermissionRequest().setResourceId("one").setResourceScopes(Arrays.asList("a","b")),
                new PermissionRequest().setResourceId("two").setResourceScopes(Arrays.asList("c","d"))
        );

        // Prepare Resource Set
        List<ResourceSet> found = Arrays.asList(
                new ResourceSet().setId("one").setResourceScopes(Arrays.asList("a","b")),
                new ResourceSet().setId("two").setResourceScopes(Arrays.asList("not","same"))
        );

        when(resourceSetService.findByDomainAndClientAndUserAndResources(DOMAIN_ID, CLIENT_ID, USER_ID,Arrays.asList("one","two"))).thenReturn(Single.just(found));
        TestObserver<PermissionTicket> testObserver = service.create(request, DOMAIN_ID, CLIENT_ID, USER_ID).test();

        testObserver.assertNotComplete();
        testObserver.assertError(err -> ((InvalidPermissionRequestException)err).getOAuth2ErrorCode().equals("invalid_scope"));
        verify(repository, times(0)).create(any());
    }

    @Test
    public void create_successMultipleResources() {
        //Prepare request
        List<PermissionRequest> request = Arrays.asList(
                new PermissionRequest().setResourceId("one").setResourceScopes(Arrays.asList("a","b")),
                new PermissionRequest().setResourceId("two").setResourceScopes(Arrays.asList("c","d"))
        );

        // Prepare Resource Set
        List<ResourceSet> found = request.stream()
                .map(s -> new ResourceSet().setId(s.getResourceId()).setResourceScopes(s.getResourceScopes()))
                .collect(Collectors.toList());

        when(resourceSetService.findByDomainAndClientAndUserAndResources(DOMAIN_ID, CLIENT_ID, USER_ID,Arrays.asList("one","two"))).thenReturn(Single.just(found));
        when(repository.create(any())).thenReturn(Single.just(new PermissionTicket().setId("success")));

        TestObserver<PermissionTicket> testObserver = service.create(request, DOMAIN_ID, CLIENT_ID, USER_ID).test();

        testObserver.assertNoErrors().assertComplete().assertValue(permissionTicket -> "success".equals(permissionTicket.getId()));
        verify(repository, times(1)).create(any());
    }

}
