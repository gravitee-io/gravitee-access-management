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
import io.gravitee.am.model.uma.Resource;
import io.gravitee.am.repository.management.api.PermissionTicketRepository;
import io.gravitee.am.service.exception.InvalidPermissionRequestException;
import io.gravitee.am.service.exception.InvalidPermissionTicketException;
import io.gravitee.am.service.impl.PermissionTicketServiceImpl;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.internal.operators.flowable.FlowableRange;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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
    private ResourceService resourceService;

    @InjectMocks
    PermissionTicketService service = new PermissionTicketServiceImpl();

    private static final String DOMAIN_ID = "domainId";
    private static final String CLIENT_ID = "clientId";
    private static final String USER_ID = "userId";

    @Test
    public void create_errorSingleResource_missingResource() {
        //Prepare request & resource
        List<PermissionRequest> request = Arrays.asList(new PermissionRequest().setResourceId("one").setResourceScopes(Arrays.asList("a","b")));

        when(resourceService.findByDomainAndClientAndResources(DOMAIN_ID, CLIENT_ID, Arrays.asList("one"))).thenReturn(Flowable.empty());
        TestObserver<PermissionTicket> testObserver = service.create(request, DOMAIN_ID, CLIENT_ID).test();

        testObserver.assertNotComplete();
        testObserver.assertError(err -> ((InvalidPermissionRequestException)err).getOAuth2ErrorCode().equals("invalid_resource_id"));
        verify(repository, times(0)).create(any());
    }

    @Test
    public void create_errorSingleResource_missingScope() {
        //Prepare request & resource
        List<PermissionRequest> request = Arrays.asList(new PermissionRequest().setResourceId("one").setResourceScopes(Arrays.asList("a","b")));

        when(resourceService.findByDomainAndClientAndResources(DOMAIN_ID, CLIENT_ID, Arrays.asList("one"))).thenReturn(Flowable.just(new Resource().setId("one").setResourceScopes(Arrays.asList("not","same"))));
        TestObserver<PermissionTicket> testObserver = service.create(request, DOMAIN_ID, CLIENT_ID).test();

        testObserver.assertNotComplete();
        testObserver.assertError(err -> ((InvalidPermissionRequestException)err).getOAuth2ErrorCode().equals("invalid_scope"));
        verify(repository, times(0)).create(any());
    }

    @Test
    public void create_successSingleResource() {
        //Prepare request & resource
        List<PermissionRequest> request = Arrays.asList(new PermissionRequest().setResourceId("one").setResourceScopes(Arrays.asList("a","b")));

        when(resourceService.findByDomainAndClientAndResources(DOMAIN_ID, CLIENT_ID, Arrays.asList("one"))).thenReturn(Flowable.just(new Resource().setId("one").setResourceScopes(Arrays.asList("a","b"))));
        when(repository.create(any())).thenReturn(Single.just(new PermissionTicket().setId("success")));

        TestObserver<PermissionTicket> testObserver = service.create(request, DOMAIN_ID, CLIENT_ID).test();

        testObserver.assertNoErrors().assertComplete().assertValue(permissionTicket -> "success".equals(permissionTicket.getId()));
        verify(repository, times(1)).create(any());
    }

    @Test
    public void create_errorMultipleResource_missingResource() {
        //Prepare request
        List<PermissionRequest> request = Arrays.asList(
                new PermissionRequest().setResourceId("one").setResourceScopes(Arrays.asList("a","b")),
                new PermissionRequest().setResourceId("two").setResourceScopes(Arrays.asList("c","d"))
        );

        // Prepare Resource
        Flowable<Resource> found = FlowableRange.just(new Resource().setId("one").setResourceScopes(Arrays.asList("not", "same")));

        when(resourceService.findByDomainAndClientAndResources(DOMAIN_ID, CLIENT_ID, Arrays.asList("one","two"))).thenReturn(found);
        TestObserver<PermissionTicket> testObserver = service.create(request, DOMAIN_ID, CLIENT_ID).test();

        testObserver.assertNotComplete();
        testObserver.assertError(err -> ((InvalidPermissionRequestException)err).getOAuth2ErrorCode().equals("invalid_resource_id"));
        verify(repository, times(0)).create(any());
    }

    @Test
    public void create_errorMultipleResource_moreThanOneResourceOwner() {
        //Prepare request
        List<PermissionRequest> request = Arrays.asList(
                new PermissionRequest().setResourceId("one").setResourceScopes(Arrays.asList("a","b")),
                new PermissionRequest().setResourceId("two").setResourceScopes(Arrays.asList("c","d"))
        );

        // Prepare Resource
        Flowable<Resource> found = Flowable.fromIterable(request)
                .map(s -> new Resource().setId(s.getResourceId()).setResourceScopes(s.getResourceScopes()).setUserId("user_"+s.getResourceId()));

        when(resourceService.findByDomainAndClientAndResources(DOMAIN_ID, CLIENT_ID, Arrays.asList("one","two"))).thenReturn(found);
        TestObserver<PermissionTicket> testObserver = service.create(request, DOMAIN_ID, CLIENT_ID).test();

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

        // Prepare Resource
        Flowable<Resource> found = Flowable.just(
                new Resource().setId("one").setResourceScopes(Arrays.asList("a","b")),
                new Resource().setId("two").setResourceScopes(Arrays.asList("not","same"))
        );

        when(resourceService.findByDomainAndClientAndResources(DOMAIN_ID, CLIENT_ID,Arrays.asList("one","two"))).thenReturn(found);
        TestObserver<PermissionTicket> testObserver = service.create(request, DOMAIN_ID, CLIENT_ID).test();

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

        // Prepare Resource
        Flowable<Resource> found = Flowable.fromIterable(request)
                .map(s -> new Resource().setId(s.getResourceId()).setResourceScopes(s.getResourceScopes()));

        when(resourceService.findByDomainAndClientAndResources(DOMAIN_ID, CLIENT_ID, Arrays.asList("one","two"))).thenReturn(found);
        when(repository.create(any())).thenReturn(Single.just(new PermissionTicket().setId("success")));

        TestObserver<PermissionTicket> testObserver = service.create(request, DOMAIN_ID, CLIENT_ID).test();

        testObserver.assertNoErrors().assertComplete().assertValue(permissionTicket -> "success".equals(permissionTicket.getId()));
        verify(repository, times(1)).create(any());
    }

    @Test
    public void create_successMultipleResources_withDupliucatedResourceIds() {
        //Prepare request
        List<PermissionRequest> request = Arrays.asList(
                new PermissionRequest().setResourceId("one").setResourceScopes(new ArrayList<>(Arrays.asList("a","b"))),
                new PermissionRequest().setResourceId("one").setResourceScopes(new ArrayList<>(Arrays.asList("c"))),
                new PermissionRequest().setResourceId("two").setResourceScopes(new ArrayList<>(Arrays.asList("c","d")))
        );

        // Prepare Resource
        Flowable<Resource> found = Flowable.just(
                new Resource().setId("one").setResourceScopes(Arrays.asList("a","b","c")),
                new Resource().setId("two").setResourceScopes(Arrays.asList("c","d"))
        );

        ArgumentCaptor<PermissionTicket> permissionTicketArgumentCaptor = ArgumentCaptor.forClass(PermissionTicket.class);

        when(resourceService.findByDomainAndClientAndResources(DOMAIN_ID, CLIENT_ID, Arrays.asList("one","two"))).thenReturn(found);
        when(repository.create(permissionTicketArgumentCaptor.capture())).thenReturn(Single.just(new PermissionTicket().setId("success")));

        TestObserver<PermissionTicket> testObserver = service.create(request, DOMAIN_ID, CLIENT_ID).test();

        testObserver.assertNoErrors().assertComplete().assertValue(permissionTicket -> "success".equals(permissionTicket.getId()));
        verify(repository, times(1)).create(any());

        //Ensure resource_id have been merged
        Assert.assertTrue(assertMerged(permissionTicketArgumentCaptor.getValue()));

    }

    private boolean assertMerged(PermissionTicket permissionTicket) {
        return permissionTicket.getPermissionRequest().size() == 2 &&
                permissionTicket.getPermissionRequest().stream().filter(pr ->
                        (pr.getResourceId().equals("one") && pr.getResourceScopes().containsAll(Arrays.asList("a","b","c"))) ||
                                (pr.getResourceId().equals("two") && pr.getResourceScopes().containsAll(Arrays.asList("c","d")))
                ).count() == 2;
    }

    @Test
    public void findById() {
        when(repository.findById("id")).thenReturn(Maybe.just(new PermissionTicket()));
        TestObserver<PermissionTicket> testObserver = service.findById("id").test();
        testObserver.assertComplete().assertNoErrors().assertValue(Objects::nonNull);
    }

    @Test
    public void remove_invalidPermissionTicket() {
        when(repository.findById("id")).thenReturn(Maybe.empty());
        TestObserver<PermissionTicket> testObserver = service.remove("id").test();
        testObserver.assertNotComplete().assertError(InvalidPermissionTicketException.class);
    }

    @Test
    public void remove() {
        when(repository.findById("id")).thenReturn(Maybe.just(new PermissionTicket().setId("id")));
        when(repository.delete("id")).thenReturn(Completable.complete());
        TestObserver<PermissionTicket> testObserver = service.remove("id").test();
        testObserver.assertComplete().assertNoErrors().assertValue(Objects::nonNull);
    }
}
