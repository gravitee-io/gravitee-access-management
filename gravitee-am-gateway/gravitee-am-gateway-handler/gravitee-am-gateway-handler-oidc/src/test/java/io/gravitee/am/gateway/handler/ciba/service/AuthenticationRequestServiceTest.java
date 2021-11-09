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
package io.gravitee.am.gateway.handler.ciba.service;

import io.gravitee.am.gateway.handler.ciba.exception.AuthenticationRequestNotFoundException;
import io.gravitee.am.gateway.handler.ciba.exception.AuthorizationPendingException;
import io.gravitee.am.gateway.handler.ciba.exception.SlowDownException;
import io.gravitee.am.gateway.handler.ciba.service.request.AuthenticationRequestStatus;
import io.gravitee.am.gateway.handler.oauth2.exception.AccessDeniedException;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.CIBASettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.repository.oidc.api.CibaAuthRequestRepository;
import io.gravitee.am.repository.oidc.model.CibaAuthRequest;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthenticationRequestServiceTest {

    private Domain domain = new Domain();

    private CIBASettings cibaSettings;

    @Mock
    private CibaAuthRequestRepository requestRepository;

    @InjectMocks
    private AuthenticationRequestServiceImpl service;

    @Before
    public void init() {
        final OIDCSettings oidc = new OIDCSettings();
        this.cibaSettings = new CIBASettings();
        oidc.setCibaSettings(this.cibaSettings);
        this.domain.setOidc(oidc);
    }

    @Test
    public void shouldNotRetrieve_UnknownId() {
        when(requestRepository.findById(anyString())).thenReturn(Maybe.empty());

        final TestObserver<CibaAuthRequest> observer = service.retrieve(domain, "unknown").test();
        observer.awaitTerminalEvent();
        observer.assertError(AuthenticationRequestNotFoundException.class);
    }

    @Test
    public void shouldRetrieve_SlowDown() {
        CibaAuthRequest request = new CibaAuthRequest();
        request.setLastAccessAt(new Date(Instant.now().minusSeconds(1).toEpochMilli()));
        request.setStatus(AuthenticationRequestStatus.ONGOING.name());

        when(requestRepository.findById(anyString())).thenReturn(Maybe.just(request));

        final TestObserver<CibaAuthRequest> observer = service.retrieve(domain,"reqid").test();

        observer.awaitTerminalEvent();
        observer.assertError(SlowDownException.class);
    }

    @Test
    public void shouldRetrieve_Pending() {
        CibaAuthRequest request = new CibaAuthRequest();
        request.setLastAccessAt(new Date(Instant.now().minusSeconds(6).toEpochMilli()));
        request.setStatus(AuthenticationRequestStatus.ONGOING.name());

        when(requestRepository.findById(anyString())).thenReturn(Maybe.just(request));
        when(requestRepository.update(any())).thenReturn(Single.just(request));

        final TestObserver<CibaAuthRequest> observer = service.retrieve(domain,"reqid").test();

        observer.awaitTerminalEvent();
        observer.assertError(AuthorizationPendingException.class);

        verify(requestRepository).update(request);
    }

    @Test
    public void shouldRetrieve_AccessDenied() {
        CibaAuthRequest request = new CibaAuthRequest();
        request.setStatus(AuthenticationRequestStatus.REJECTED.name());

        when(requestRepository.findById(anyString())).thenReturn(Maybe.just(request));
        when(requestRepository.delete(any())).thenReturn(Completable.complete());

        final TestObserver<CibaAuthRequest> observer = service.retrieve(domain,"reqid").test();

        observer.awaitTerminalEvent();
        observer.assertError(AccessDeniedException.class);
    }

    @Test
    public void shouldRetrieve() {
        CibaAuthRequest request = new CibaAuthRequest();
        request.setStatus(AuthenticationRequestStatus.SUCCESS.name());

        when(requestRepository.findById(anyString())).thenReturn(Maybe.just(request));
        when(requestRepository.delete(any())).thenReturn(Completable.complete());

        final TestObserver<CibaAuthRequest> observer = service.retrieve(domain,"reqid").test();

        observer.awaitTerminalEvent();
        observer.assertValueCount(1);
    }
}