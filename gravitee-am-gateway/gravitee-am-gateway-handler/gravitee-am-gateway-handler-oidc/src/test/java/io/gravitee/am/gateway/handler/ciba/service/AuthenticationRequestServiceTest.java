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

import io.gravitee.am.authdevice.notifier.api.AuthenticationDeviceNotifierProvider;
import io.gravitee.am.authdevice.notifier.api.model.ADCallbackContext;
import io.gravitee.am.authdevice.notifier.api.model.ADUserResponse;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.exception.oauth2.InvalidTokenException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.ciba.exception.AuthenticationRequestNotFoundException;
import io.gravitee.am.gateway.handler.ciba.exception.AuthorizationPendingException;
import io.gravitee.am.gateway.handler.ciba.exception.AuthorizationRejectedException;
import io.gravitee.am.gateway.handler.ciba.exception.SlowDownException;
import io.gravitee.am.gateway.handler.ciba.service.request.AuthenticationRequestStatus;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.manager.authdevice.notifier.AuthenticationDeviceNotifierManager;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.CIBASettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.repository.oidc.api.CibaAuthRequestRepository;
import io.gravitee.am.repository.oidc.model.CibaAuthRequest;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.vertx.rxjava3.core.MultiMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthenticationRequestServiceTest {

    public static final int RETENTION_PERIOD = 960;
    private Domain domain = new Domain();
    private Client client;

    private CIBASettings cibaSettings;

    @InjectMocks
    private AuthenticationRequestServiceImpl service;

    @Mock
    private CibaAuthRequestRepository requestRepository;

    @Mock
    private AuthenticationDeviceNotifierManager notifierManager;

    @Mock
    private JWTService jwtService;

    @Mock
    private ClientSyncService clientService;


    @Before
    public void init() {
        final OIDCSettings oidc = new OIDCSettings();
        this.cibaSettings = new CIBASettings();
        oidc.setCibaSettings(this.cibaSettings);
        this.domain.setOidc(oidc);
        this.client = new Client();
        this.client.setClientId("client-id");
    }

    @Test
    public void shouldNotRetrieve_UnknownId() {
        when(requestRepository.findById(anyString())).thenReturn(Maybe.empty());

        final TestObserver<CibaAuthRequest> observer = service.retrieve(domain, "unknown", client).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidGrantException.class);
    }

    @Test
    public void shouldRetrieve_SlowDown() {
        CibaAuthRequest request = new CibaAuthRequest();
        request.setLastAccessAt(new Date(Instant.now().minusSeconds(1).toEpochMilli()));
        request.setStatus(AuthenticationRequestStatus.ONGOING.name());
        request.setExpireAt(new Date(Instant.now().plusSeconds(RETENTION_PERIOD).toEpochMilli()));
        request.setClientId(client.getClientId());

        when(requestRepository.findById(anyString())).thenReturn(Maybe.just(request));

        final TestObserver<CibaAuthRequest> observer = service.retrieve(domain,"reqid", client).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(SlowDownException.class);
    }

    @Test
    public void shouldRetrieve_Pending() {
        CibaAuthRequest request = new CibaAuthRequest();
        request.setLastAccessAt(new Date(Instant.now().minusSeconds(6).toEpochMilli()));
        request.setStatus(AuthenticationRequestStatus.ONGOING.name());
        request.setExpireAt(new Date(Instant.now().plusSeconds(RETENTION_PERIOD).toEpochMilli()));
        request.setClientId(client.getClientId());

        when(requestRepository.findById(anyString())).thenReturn(Maybe.just(request));
        when(requestRepository.update(any())).thenReturn(Single.just(request));

        final TestObserver<CibaAuthRequest> observer = service.retrieve(domain,"reqid", client).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(AuthorizationPendingException.class);

        verify(requestRepository).update(request);
    }

    @Test
    public void shouldRetrieve_AuthorizationRejectedException() {
        CibaAuthRequest request = new CibaAuthRequest();
        request.setStatus(AuthenticationRequestStatus.REJECTED.name());
        request.setExpireAt(new Date(Instant.now().plusSeconds(RETENTION_PERIOD).toEpochMilli()));
        request.setClientId(client.getClientId());

        when(requestRepository.findById(anyString())).thenReturn(Maybe.just(request));
        when(requestRepository.delete(any())).thenReturn(Completable.complete());

        final TestObserver<CibaAuthRequest> observer = service.retrieve(domain,"reqid", client).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(AuthorizationRejectedException.class);
    }

    @Test
    public void shouldRetrieve() {
        CibaAuthRequest request = new CibaAuthRequest();
        request.setStatus(AuthenticationRequestStatus.SUCCESS.name());
        request.setExpireAt(new Date(Instant.now().plusSeconds(RETENTION_PERIOD).toEpochMilli()));
        request.setClientId(client.getClientId());

        when(requestRepository.findById(anyString())).thenReturn(Maybe.just(request));
        when(requestRepository.delete(any())).thenReturn(Completable.complete());

        final TestObserver<CibaAuthRequest> observer = service.retrieve(domain,"reqid", client).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertValueCount(1);
    }

    @Test
    public void shouldNotUpdate() {
        CibaAuthRequest request = mock(CibaAuthRequest.class);
        when(requestRepository.findById(any())).thenReturn(Maybe.empty());
        final TestObserver<CibaAuthRequest> observer = service.updateAuthDeviceInformation(request).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(AuthenticationRequestNotFoundException.class);

        verify(requestRepository, never()).update(any());
    }

    @Test
    public void shouldUpdate() {
        CibaAuthRequest request = mock(CibaAuthRequest.class);
        when(requestRepository.findById(any())).thenReturn(Maybe.just(request));
        when(requestRepository.update(any())).thenReturn(Single.just(request));

        final TestObserver<CibaAuthRequest> observer = service.updateAuthDeviceInformation(request).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertValueCount(1);

        verify(request, never()).setLastAccessAt(any());
        verify(request).setExternalTrxId(any());
        verify(request).setExternalInformation(any());
    }

    @Test
    public void shouldUpdateAuthReqStatus() {
        final String STATE = "state";
        final String EXTERNAL_ID = "externalId";
        final String AUTH_REQ_ID = "auth_red_id";
        final boolean requestValidated = new Random().nextBoolean();

        AuthenticationDeviceNotifierProvider provider = mock(AuthenticationDeviceNotifierProvider.class);
        when(notifierManager.getAuthDeviceNotifierProviders()).thenReturn(List.of(provider));
        when(provider.extractUserResponse(any())).thenReturn(Single.just(Optional.of(new ADUserResponse(EXTERNAL_ID, STATE, requestValidated))));

        final JWT stateJwt = new JWT();
        stateJwt.setJti(EXTERNAL_ID);
        when(this.jwtService.decode(STATE, JWTService.TokenType.STATE)).thenReturn(Single.just(stateJwt));
        when(this.clientService.findByClientId(any())).thenReturn(Maybe.just(new Client()));
        when(this.jwtService.decodeAndVerify(anyString(), any(Client.class), any())).thenReturn(Single.just(stateJwt));

        final CibaAuthRequest cibaRequest = new CibaAuthRequest();
        cibaRequest.setId(AUTH_REQ_ID);
        when(this.requestRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Maybe.just(cibaRequest));

        final String status = requestValidated ? AuthenticationRequestStatus.SUCCESS.name() : AuthenticationRequestStatus.REJECTED.name();
        when(this.requestRepository.updateStatus(AUTH_REQ_ID, status)).thenReturn(Single.just(cibaRequest));

        final ADCallbackContext context = new ADCallbackContext(MultiMap.caseInsensitiveMultiMap(), MultiMap.caseInsensitiveMultiMap());
        final TestObserver<Void> observer = this.service.validateUserResponse(context).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();

        verify(requestRepository).updateStatus(AUTH_REQ_ID, status);
    }

    @Test
    public void shouldNotUpdateStatus_missingUserResponse() {
        AuthenticationDeviceNotifierProvider provider = mock(AuthenticationDeviceNotifierProvider.class);
        when(notifierManager.getAuthDeviceNotifierProviders()).thenReturn(List.of(provider));
        when(provider.extractUserResponse(any())).thenReturn(Single.just(Optional.empty()));

        final ADCallbackContext context = new ADCallbackContext(MultiMap.caseInsensitiveMultiMap(), MultiMap.caseInsensitiveMultiMap());
        final TestObserver<Void> observer = this.service.validateUserResponse(context).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidRequestException.class);

        verify(clientService, never()).findByClientId(any());
        verify(requestRepository, never()).updateStatus(any(), any());
    }

    @Test
    public void shouldNotUpdateStatus_UnknownClient() {
        final String STATE = "state";
        final String EXTERNAL_ID = "externalId";
        final boolean requestValidated = new Random().nextBoolean();

        AuthenticationDeviceNotifierProvider provider = mock(AuthenticationDeviceNotifierProvider.class);
        when(notifierManager.getAuthDeviceNotifierProviders()).thenReturn(List.of(provider));
        when(provider.extractUserResponse(any())).thenReturn(Single.just(Optional.of(new ADUserResponse(EXTERNAL_ID, STATE, requestValidated))));

        final JWT stateJwt = new JWT();
        stateJwt.setJti(EXTERNAL_ID);
        when(this.jwtService.decode(STATE, JWTService.TokenType.STATE)).thenReturn(Single.just(stateJwt));
        when(this.clientService.findByClientId(any())).thenReturn(Maybe.empty());

        final ADCallbackContext context = new ADCallbackContext(MultiMap.caseInsensitiveMultiMap(), MultiMap.caseInsensitiveMultiMap());
        final TestObserver<Void> observer = this.service.validateUserResponse(context).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidRequestException.class);

        verify(requestRepository, never()).updateStatus(any(), any());
    }

    @Test
    public void shouldNotUpdateStatus_InvalidSignature() {
        final String STATE = "state";
        final String EXTERNAL_ID = "externalId";
        final boolean requestValidated = new Random().nextBoolean();

        AuthenticationDeviceNotifierProvider provider = mock(AuthenticationDeviceNotifierProvider.class);
        when(notifierManager.getAuthDeviceNotifierProviders()).thenReturn(List.of(provider));
        when(provider.extractUserResponse(any())).thenReturn(Single.just(Optional.of(new ADUserResponse(EXTERNAL_ID, STATE, requestValidated))));

        final JWT stateJwt = new JWT();
        stateJwt.setJti(EXTERNAL_ID);
        when(this.jwtService.decode(STATE, JWTService.TokenType.STATE)).thenReturn(Single.just(stateJwt));
        when(this.clientService.findByClientId(any())).thenReturn(Maybe.just(new Client()));
        when(this.jwtService.decodeAndVerify(anyString(), any(Client.class), any())).thenReturn(Single.error(new InvalidTokenException()));

        final ADCallbackContext context = new ADCallbackContext(MultiMap.caseInsensitiveMultiMap(), MultiMap.caseInsensitiveMultiMap());
        final TestObserver<Void> observer = this.service.validateUserResponse(context).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidRequestException.class);

        verify(clientService).findByClientId(any());
        verify(requestRepository, never()).updateStatus(any(), any());
    }

    @Test
    public void shouldNotUpdateStatus_StateMismatch() {
        final String STATE = "state";
        final String EXTERNAL_ID = "externalId";
        final boolean requestValidated = new Random().nextBoolean();

        AuthenticationDeviceNotifierProvider provider = mock(AuthenticationDeviceNotifierProvider.class);
        when(notifierManager.getAuthDeviceNotifierProviders()).thenReturn(List.of(provider));
        when(provider.extractUserResponse(any())).thenReturn(Single.just(Optional.of(new ADUserResponse("unknown", STATE, requestValidated))));

        final JWT stateJwt = new JWT();
        stateJwt.setJti(EXTERNAL_ID);
        when(this.jwtService.decode(STATE, JWTService.TokenType.STATE)).thenReturn(Single.just(stateJwt));
        when(this.clientService.findByClientId(any())).thenReturn(Maybe.just(new Client()));
        when(this.jwtService.decodeAndVerify(anyString(), any(Client.class), any())).thenReturn(Single.just(stateJwt));

        final ADCallbackContext context = new ADCallbackContext(MultiMap.caseInsensitiveMultiMap(), MultiMap.caseInsensitiveMultiMap());
        final TestObserver<Void> observer = this.service.validateUserResponse(context).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidRequestException.class);

        verify(clientService).findByClientId(any());
        verify(requestRepository, never()).updateStatus(any(), any());
    }

    @Test
    public void shouldNotUpdateStatus_UnknownRequestId() {
        final String STATE = "state";
        final String EXTERNAL_ID = "externalId";
        final String AUTH_REQ_ID = "auth_red_id";
        final boolean requestValidated = new Random().nextBoolean();

        AuthenticationDeviceNotifierProvider provider = mock(AuthenticationDeviceNotifierProvider.class);
        when(notifierManager.getAuthDeviceNotifierProviders()).thenReturn(List.of(provider));
        when(provider.extractUserResponse(any())).thenReturn(Single.just(Optional.of(new ADUserResponse(EXTERNAL_ID, STATE, requestValidated))));

        final JWT stateJwt = new JWT();
        stateJwt.setJti(EXTERNAL_ID);
        when(this.jwtService.decode(STATE, JWTService.TokenType.STATE)).thenReturn(Single.just(stateJwt));
        when(this.clientService.findByClientId(any())).thenReturn(Maybe.just(new Client()));
        when(this.jwtService.decodeAndVerify(anyString(), any(Client.class), any())).thenReturn(Single.just(stateJwt));

        final CibaAuthRequest cibaRequest = new CibaAuthRequest();
        cibaRequest.setId(AUTH_REQ_ID);
        when(this.requestRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Maybe.empty());

        final ADCallbackContext context = new ADCallbackContext(MultiMap.caseInsensitiveMultiMap(), MultiMap.caseInsensitiveMultiMap());
        final TestObserver<Void> observer = this.service.validateUserResponse(context).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidRequestException.class);

        verify(clientService).findByClientId(any());
        verify(jwtService).decodeAndVerify(anyString(), any(Client.class), any());
        verify(requestRepository, never()).updateStatus(any(), any());
    }

}