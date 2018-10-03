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
package io.gravitee.am.gateway.handler.oauth2.approval;

import io.gravitee.am.gateway.handler.oauth2.approval.impl.ApprovalServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.exception.AccessDeniedException;
import io.gravitee.am.gateway.handler.oauth2.request.AuthorizationRequest;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oauth2.ScopeApproval;
import io.gravitee.am.repository.oauth2.api.ScopeApprovalRepository;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.*;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ApprovalServiceTest {

    @InjectMocks
    private ApprovalService approvalService = new ApprovalServiceImpl();

    @Mock
    private ScopeApprovalRepository scopeApprovalRepository;

    @Mock
    private ClientService clientService;

    @Mock
    private Domain domain;

    @Test
    public void shouldApproveRequest_clientAutoApproval() {
        final String clientId = "client_id";
        final String userId = "user_id";
        final String autoApproveScope = "read";
        Client client = new Client();
        client.setClientId(clientId);
        client.setAutoApproveScopes(Collections.singletonList(autoApproveScope));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setClientId(clientId);
        authorizationRequest.setScopes(Collections.singleton(autoApproveScope));

        when(clientService.findByClientId(clientId)).thenReturn(Maybe.just(client));

        TestObserver<AuthorizationRequest> testObserver = approvalService.checkApproval(authorizationRequest, client, userId).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(request -> request.isApproved());
    }

    @Test
    public void shouldNotApproveRequest_noClientAutoApproval() {
        final String clientId = "client_id";
        final String userId = "user_id";
        final String autoApproveScope = "read";
        Client client = new Client();
        client.setClientId(clientId);
        client.setAutoApproveScopes(null);

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setClientId(clientId);
        authorizationRequest.setScopes(Collections.singleton(autoApproveScope));

        when(clientService.findByClientId(clientId)).thenReturn(Maybe.just(client));
        when(scopeApprovalRepository.findByDomainAndUserAndClient(anyString(), anyString(), anyString())).thenReturn(Single.just(Collections.emptySet()));

        approvalService.checkApproval(authorizationRequest, client, userId).test().assertError(AccessDeniedException.class);
    }

    @Test
    public void shouldApproveRequest_noClientAutoApproval_userApproval() {
        final String clientId = "client_id";
        final String userId = "user_id";
        final String autoApproveScope = "read";
        final String domainId = "domain_id";
        Client client = new Client();
        client.setClientId(clientId);
        client.setAutoApproveScopes(null);

        ScopeApproval userScopeApproval = new ScopeApproval();
        userScopeApproval.setClientId(clientId);
        userScopeApproval.setUserId(userId);
        userScopeApproval.setDomain(domainId);
        userScopeApproval.setScope(autoApproveScope);
        userScopeApproval.setExpiresAt(new Date(System.currentTimeMillis() + (60 * 60 * 1000)));
        userScopeApproval.setStatus(ScopeApproval.ApprovalStatus.APPROVED);

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setClientId(clientId);
        authorizationRequest.setScopes(Collections.singleton(autoApproveScope));

        when(domain.getId()).thenReturn(domainId);
        when(clientService.findByClientId(clientId)).thenReturn(Maybe.just(client));
        when(scopeApprovalRepository.findByDomainAndUserAndClient(domainId, userId, clientId)).thenReturn(Single.just(Collections.singleton(userScopeApproval)));

        TestObserver<AuthorizationRequest> testObserver = approvalService.checkApproval(authorizationRequest, client, userId).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(request -> request.isApproved());
    }

    @Test
    public void shouldNotApproveRequest_noClientAutoApproval_userDenial() {
        final String clientId = "client_id";
        final String userId = "user_id";
        final String autoApproveScope = "read";
        final String domainId = "domain_id";
        Client client = new Client();
        client.setClientId(clientId);
        client.setAutoApproveScopes(null);

        ScopeApproval userScopeApproval = new ScopeApproval();
        userScopeApproval.setClientId(clientId);
        userScopeApproval.setUserId(userId);
        userScopeApproval.setDomain(domainId);
        userScopeApproval.setScope(autoApproveScope);
        userScopeApproval.setExpiresAt(new Date(System.currentTimeMillis() + (60 * 60 * 1000)));
        userScopeApproval.setStatus(ScopeApproval.ApprovalStatus.DENIED);

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setClientId(clientId);
        authorizationRequest.setScopes(Collections.singleton(autoApproveScope));

        when(domain.getId()).thenReturn(domainId);
        when(clientService.findByClientId(clientId)).thenReturn(Maybe.just(client));
        when(scopeApprovalRepository.findByDomainAndUserAndClient(domainId, userId, clientId)).thenReturn(Single.just(Collections.singleton(userScopeApproval)));

        approvalService.checkApproval(authorizationRequest, client, userId).test().assertError(AccessDeniedException.class);
    }

    @Test
    public void shouldApproveRequest_userApprovalChoice() {
        final String clientId = "client_id";
        final String userId = "user_id";
        final String readScope = "read";
        final String writeScope = "write";
        Client client = new Client();
        client.setClientId(clientId);
        client.setAutoApproveScopes(Collections.singletonList(readScope));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setClientId(clientId);
        authorizationRequest.setScopes(new HashSet<>(Arrays.asList(readScope, writeScope)));

        Map<String, String> approvalParameters = new HashMap<>();
        approvalParameters.put(OAuth2Constants.SCOPE_PREFIX + readScope, "true");
        approvalParameters.put(OAuth2Constants.SCOPE_PREFIX + writeScope, "false");
        authorizationRequest.setApprovalParameters(approvalParameters);

        when(scopeApprovalRepository.upsert(any())).thenReturn(Single.just(new ScopeApproval()));

        TestObserver<AuthorizationRequest> testObserver = approvalService.saveApproval(authorizationRequest, userId).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(request -> request.isApproved());
        testObserver.assertValue(request -> request.getScopes().size() == 1);
        testObserver.assertValue(request -> request.getScopes().iterator().next().equals("read"));
    }

    @Test
    public void shouldNotApproveRequest_userApprovalChoice() {
        final String clientId = "client_id";
        final String userId = "user_id";
        final String readScope = "read";
        final String writeScope = "write";
        Client client = new Client();
        client.setClientId(clientId);
        client.setAutoApproveScopes(Collections.singletonList(readScope));

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setClientId(clientId);
        authorizationRequest.setScopes(new HashSet<>(Arrays.asList(readScope, writeScope)));

        Map<String, String> approvalParameters = new HashMap<>();
        approvalParameters.put(OAuth2Constants.SCOPE_PREFIX + readScope, "false");
        approvalParameters.put(OAuth2Constants.SCOPE_PREFIX + writeScope, "false");
        authorizationRequest.setApprovalParameters(approvalParameters);

        when(scopeApprovalRepository.upsert(any())).thenReturn(Single.just(new ScopeApproval()));

        TestObserver<AuthorizationRequest> testObserver = approvalService.saveApproval(authorizationRequest, userId).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(request -> !request.isApproved());
        testObserver.assertValue(request -> request.getScopes().isEmpty());
    }
}
