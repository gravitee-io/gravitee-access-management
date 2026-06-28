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
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.common.client.ClientLookupService;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.manager.authdevice.notifier.AuthenticationDeviceNotifierManager;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.identityprovider.api.AuthenticationProvider;
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
import io.vertx.core.MultiMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.gravitee.am.gateway.handler.ciba.service.request.CibaAuthenticationRequest;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

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
    private ClientLookupService clientLookupService;

    @Mock
    private IdentityProviderManager identityProviderManager;

    @Mock
    private UserAuthenticationManager userAuthenticationManager;

    @Mock
    private AuthenticationProvider authProvider;


    @Before
    public void init() throws Exception {
        final OIDCSettings oidc = new OIDCSettings();
        this.cibaSettings = new CIBASettings();
        oidc.setCibaSettings(this.cibaSettings);
        this.domain.setOidc(oidc);
        this.domain.setId("dom1");
        this.client = new Client();
        this.client.setClientId("client-id");
        // Inject the real Domain instance into the service (not a @Mock, so @InjectMocks skips it)
        java.lang.reflect.Field domainField = AuthenticationRequestServiceImpl.class.getDeclaredField("domain");
        domainField.setAccessible(true);
        domainField.set(service, this.domain);
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
        when(this.clientLookupService.findByClientId(any())).thenReturn(Maybe.just(new Client()));
        when(this.jwtService.decodeAndVerify(anyString(), any(Client.class), any())).thenReturn(Single.just(stateJwt));

        final CibaAuthRequest cibaRequest = new CibaAuthRequest();
        cibaRequest.setId(AUTH_REQ_ID);
        when(this.requestRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Maybe.just(cibaRequest));

        final String status = requestValidated ? AuthenticationRequestStatus.SUCCESS.name() : AuthenticationRequestStatus.REJECTED.name();
        when(this.requestRepository.updateStatus(AUTH_REQ_ID, status)).thenReturn(Single.just(cibaRequest));

        final ADCallbackContext context = new ADCallbackContext(MultiMap.caseInsensitiveMultiMap(), MultiMap.caseInsensitiveMultiMap());
        final TestObserver<Void> observer = this.service.validateUserResponse(context, mock(io.gravitee.gateway.api.Request.class)).test();
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
        final TestObserver<Void> observer = this.service.validateUserResponse(context, mock(io.gravitee.gateway.api.Request.class)).test();

        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidRequestException.class);

        verify(clientLookupService, never()).findByClientId(any());
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
        when(this.clientLookupService.findByClientId(any())).thenReturn(Maybe.empty());

        final ADCallbackContext context = new ADCallbackContext(MultiMap.caseInsensitiveMultiMap(), MultiMap.caseInsensitiveMultiMap());
        final TestObserver<Void> observer = this.service.validateUserResponse(context, mock(io.gravitee.gateway.api.Request.class)).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        // Unknown client now surfaces directly as InvalidClientException (client lookup moved out of verifyState's
        // onErrorResumeNext wrapper); both are OAuth2Exception -> 400 at the callback handler.
        observer.assertError(io.gravitee.am.common.exception.oauth2.OAuth2Exception.class);

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
        when(this.clientLookupService.findByClientId(any())).thenReturn(Maybe.just(new Client()));
        when(this.jwtService.decodeAndVerify(anyString(), any(Client.class), any())).thenReturn(Single.error(new InvalidTokenException()));

        final ADCallbackContext context = new ADCallbackContext(MultiMap.caseInsensitiveMultiMap(), MultiMap.caseInsensitiveMultiMap());
        final TestObserver<Void> observer = this.service.validateUserResponse(context, mock(io.gravitee.gateway.api.Request.class)).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidRequestException.class);

        verify(clientLookupService).findByClientId(any());
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
        when(this.clientLookupService.findByClientId(any())).thenReturn(Maybe.just(new Client()));
        when(this.jwtService.decodeAndVerify(anyString(), any(Client.class), any())).thenReturn(Single.just(stateJwt));

        final ADCallbackContext context = new ADCallbackContext(MultiMap.caseInsensitiveMultiMap(), MultiMap.caseInsensitiveMultiMap());
        final TestObserver<Void> observer = this.service.validateUserResponse(context, mock(io.gravitee.gateway.api.Request.class)).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidRequestException.class);

        verify(clientLookupService).findByClientId(any());
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
        when(this.clientLookupService.findByClientId(any())).thenReturn(Maybe.just(new Client()));
        when(this.jwtService.decodeAndVerify(anyString(), any(Client.class), any())).thenReturn(Single.just(stateJwt));

        final CibaAuthRequest cibaRequest = new CibaAuthRequest();
        cibaRequest.setId(AUTH_REQ_ID);
        when(this.requestRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Maybe.empty());

        final ADCallbackContext context = new ADCallbackContext(MultiMap.caseInsensitiveMultiMap(), MultiMap.caseInsensitiveMultiMap());
        final TestObserver<Void> observer = this.service.validateUserResponse(context, mock(io.gravitee.gateway.api.Request.class)).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidRequestException.class);

        verify(clientLookupService).findByClientId(any());
        verify(jwtService).decodeAndVerify(anyString(), any(Client.class), any());
        verify(requestRepository, never()).updateStatus(any(), any());
    }

    @Test
    public void shouldRideIdpPipelineAndSetLocalSubject_onSuccess() {
        final String STATE = "state", EXTERNAL_ID = "externalId", AUTH_REQ_ID = "auth_req", IDP = "idp-acme";

        AuthenticationDeviceNotifierProvider provider = mock(AuthenticationDeviceNotifierProvider.class);
        when(notifierManager.getAuthDeviceNotifierProviders()).thenReturn(List.of(provider));
        when(provider.extractUserResponse(any())).thenReturn(Single.just(Optional.of(
                new ADUserResponse(EXTERNAL_ID, STATE, true, "id-tok", "acc-tok", IDP))));

        final JWT stateJwt = new JWT(); stateJwt.setJti(EXTERNAL_ID);
        when(jwtService.decode(STATE, JWTService.TokenType.STATE)).thenReturn(Single.just(stateJwt));
        when(clientLookupService.findByClientId(any())).thenReturn(Maybe.just(new Client()));
        when(jwtService.decodeAndVerify(anyString(), any(Client.class), any())).thenReturn(Single.just(stateJwt));

        final CibaAuthRequest cibaRequest = new CibaAuthRequest(); cibaRequest.setId(AUTH_REQ_ID);
        cibaRequest.setDeviceNotifierId("n1");
        when(requestRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Maybe.just(cibaRequest));

        io.gravitee.am.identityprovider.api.DefaultUser principal = new io.gravitee.am.identityprovider.api.DefaultUser("acme|9");
        principal.setAdditionalInformation(new java.util.HashMap<>());
        when(identityProviderManager.get(IDP)).thenReturn(Maybe.just(authProvider));
        when(authProvider.retrieveUserFromTokenResponse(eq("acc-tok"), eq("id-tok"), any())).thenReturn(Maybe.just(principal));

        io.gravitee.am.model.User local = new io.gravitee.am.model.User(); local.setId("local-uuid-1");
        ArgumentCaptor<io.gravitee.am.identityprovider.api.User> principalCaptor =
                ArgumentCaptor.forClass(io.gravitee.am.identityprovider.api.User.class);
        when(userAuthenticationManager.connect(principalCaptor.capture(), isNull(), any(), eq(true))).thenReturn(Single.just(local));

        when(requestRepository.update(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));
        when(requestRepository.updateStatus(AUTH_REQ_ID, "SUCCESS")).thenReturn(Single.just(cibaRequest));

        final ADCallbackContext ctx = new ADCallbackContext(MultiMap.caseInsensitiveMultiMap(), MultiMap.caseInsensitiveMultiMap());
        service.validateUserResponse(ctx, mock(io.gravitee.gateway.api.Request.class)).test()
               .awaitDone(10, TimeUnit.SECONDS).assertNoErrors();

        ArgumentCaptor<CibaAuthRequest> captor = ArgumentCaptor.forClass(CibaAuthRequest.class);
        verify(requestRepository).update(captor.capture());
        assertEquals("local-uuid-1", captor.getValue().getSubject());
        verify(requestRepository).updateStatus(AUTH_REQ_ID, "SUCCESS");
        verify(authProvider).retrieveUserFromTokenResponse(eq("acc-tok"), eq("id-tok"), any());
        assertEquals(IDP, principalCaptor.getValue().getAdditionalInformation().get("source"));
        verify(userAuthenticationManager).connect(any(io.gravitee.am.identityprovider.api.User.class), isNull(), any(), eq(true));
    }

    @Test
    public void shouldFailClosed_whenVerifyMapErrors() {
        final String STATE = "state", EXTERNAL_ID = "externalId", AUTH_REQ_ID = "auth_req", IDP = "idp-acme";
        AuthenticationDeviceNotifierProvider provider = mock(AuthenticationDeviceNotifierProvider.class);
        when(notifierManager.getAuthDeviceNotifierProviders()).thenReturn(List.of(provider));
        when(provider.extractUserResponse(any())).thenReturn(Single.just(Optional.of(
                new ADUserResponse(EXTERNAL_ID, STATE, true, "id-tok", "acc-tok", IDP))));
        final JWT stateJwt = new JWT(); stateJwt.setJti(EXTERNAL_ID);
        when(jwtService.decode(STATE, JWTService.TokenType.STATE)).thenReturn(Single.just(stateJwt));
        when(clientLookupService.findByClientId(any())).thenReturn(Maybe.just(new Client()));
        when(jwtService.decodeAndVerify(anyString(), any(Client.class), any())).thenReturn(Single.just(stateJwt));
        final CibaAuthRequest cibaRequest = new CibaAuthRequest(); cibaRequest.setId(AUTH_REQ_ID); cibaRequest.setDeviceNotifierId("n1");
        when(requestRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Maybe.just(cibaRequest));
        when(identityProviderManager.get(IDP)).thenReturn(Maybe.just(authProvider));
        when(authProvider.retrieveUserFromTokenResponse(any(), any(), any()))
                .thenReturn(Maybe.error(new io.gravitee.am.common.exception.authentication.BadCredentialsException("bad sig")));

        final ADCallbackContext ctx = new ADCallbackContext(MultiMap.caseInsensitiveMultiMap(), MultiMap.caseInsensitiveMultiMap());
        service.validateUserResponse(ctx, mock(io.gravitee.gateway.api.Request.class)).test()
               .awaitDone(10, TimeUnit.SECONDS).assertError(io.gravitee.am.common.exception.authentication.BadCredentialsException.class);
        verify(userAuthenticationManager, never()).connect(any(), any(), any(), anyBoolean());
        verify(requestRepository, never()).updateStatus(any(), eq("SUCCESS"));
    }

    /** Sets up a validated federated completion whose ADUserResponse self-describes the given idp. */
    private void arrangeFederatedCompletion(String state, String externalId, String authReqId, String idp) {
        AuthenticationDeviceNotifierProvider provider = mock(AuthenticationDeviceNotifierProvider.class);
        when(notifierManager.getAuthDeviceNotifierProviders()).thenReturn(List.of(provider));
        when(provider.extractUserResponse(any())).thenReturn(Single.just(Optional.of(
                new ADUserResponse(externalId, state, true, "id-tok", "acc-tok", idp))));
        final JWT stateJwt = new JWT(); stateJwt.setJti(externalId);
        when(jwtService.decode(state, JWTService.TokenType.STATE)).thenReturn(Single.just(stateJwt));
        when(clientLookupService.findByClientId(any())).thenReturn(Maybe.just(new Client()));
        when(jwtService.decodeAndVerify(anyString(), any(Client.class), any())).thenReturn(Single.just(stateJwt));
        final CibaAuthRequest cibaRequest = new CibaAuthRequest(); cibaRequest.setId(authReqId); cibaRequest.setDeviceNotifierId("n1");
        when(requestRepository.findByExternalId(externalId)).thenReturn(Maybe.just(cibaRequest));
    }

    private TestObserver<Void> runCompletion() {
        final ADCallbackContext ctx = new ADCallbackContext(MultiMap.caseInsensitiveMultiMap(), MultiMap.caseInsensitiveMultiMap());
        return service.validateUserResponse(ctx, mock(io.gravitee.gateway.api.Request.class)).test().awaitDone(10, TimeUnit.SECONDS);
    }

    @Test
    public void shouldFailClosed_whenIdpNotAvailable() {
        final String IDP = "idp-acme";
        arrangeFederatedCompletion("state", "externalId", "auth_req", IDP);
        when(identityProviderManager.get(IDP)).thenReturn(Maybe.empty());

        runCompletion().assertError(InvalidRequestException.class);
        verify(userAuthenticationManager, never()).connect(any(), any(), any(), anyBoolean());
        verify(requestRepository, never()).updateStatus(any(), eq("SUCCESS"));
    }

    @Test
    public void shouldFailClosed_whenEmptyUserFromIdp() {
        final String IDP = "idp-acme";
        arrangeFederatedCompletion("state", "externalId", "auth_req", IDP);
        when(identityProviderManager.get(IDP)).thenReturn(Maybe.just(authProvider));
        when(authProvider.retrieveUserFromTokenResponse(any(), any(), any())).thenReturn(Maybe.empty());

        runCompletion().assertError(InvalidRequestException.class);
        verify(userAuthenticationManager, never()).connect(any(), any(), any(), anyBoolean());
        verify(requestRepository, never()).updateStatus(any(), eq("SUCCESS"));
    }

    @Test
    public void shouldFailClosed_whenNonDefaultUserPrincipal() {
        final String IDP = "idp-acme";
        arrangeFederatedCompletion("state", "externalId", "auth_req", IDP);
        when(identityProviderManager.get(IDP)).thenReturn(Maybe.just(authProvider));
        // A principal that is a User but NOT a DefaultUser must be rejected (the completion maps DefaultUser only).
        when(authProvider.retrieveUserFromTokenResponse(any(), any(), any()))
                .thenReturn(Maybe.just(mock(io.gravitee.am.identityprovider.api.User.class)));

        runCompletion().assertError(InvalidRequestException.class);
        verify(userAuthenticationManager, never()).connect(any(), any(), any(), anyBoolean());
        verify(requestRepository, never()).updateStatus(any(), eq("SUCCESS"));
    }

    @Test
    public void shouldStatusOnly_whenNoIdToken() {
        final String STATE = "state";
        final String EXTERNAL_ID = "externalId";
        final String AUTH_REQ_ID = "auth_red_id";

        AuthenticationDeviceNotifierProvider provider = mock(AuthenticationDeviceNotifierProvider.class);
        when(notifierManager.getAuthDeviceNotifierProviders()).thenReturn(List.of(provider));
        when(provider.extractUserResponse(any())).thenReturn(Single.just(Optional.of(new ADUserResponse(EXTERNAL_ID, STATE, true))));

        final JWT stateJwt = new JWT();
        stateJwt.setJti(EXTERNAL_ID);
        when(this.jwtService.decode(STATE, JWTService.TokenType.STATE)).thenReturn(Single.just(stateJwt));
        when(this.clientLookupService.findByClientId(any())).thenReturn(Maybe.just(new Client()));
        when(this.jwtService.decodeAndVerify(anyString(), any(Client.class), any())).thenReturn(Single.just(stateJwt));

        final CibaAuthRequest cibaRequest = new CibaAuthRequest();
        cibaRequest.setId(AUTH_REQ_ID);
        when(this.requestRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Maybe.just(cibaRequest));

        final String status = AuthenticationRequestStatus.SUCCESS.name();
        when(this.requestRepository.updateStatus(AUTH_REQ_ID, status)).thenReturn(Single.just(cibaRequest));

        final ADCallbackContext context = new ADCallbackContext(MultiMap.caseInsensitiveMultiMap(), MultiMap.caseInsensitiveMultiMap());
        final TestObserver<Void> observer = this.service.validateUserResponse(context, mock(io.gravitee.gateway.api.Request.class)).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();

        verify(identityProviderManager, never()).get(any());
        verify(userAuthenticationManager, never()).connect(any(), any(), any(), anyBoolean());
        verify(requestRepository, never()).update(any());
        verify(requestRepository).updateStatus(AUTH_REQ_ID, status);
        assertNull(cibaRequest.getSubject());
    }

    @Test
    public void shouldStatusOnly_onReject_evenWithIdToken() {
        final String STATE = "state";
        final String EXTERNAL_ID = "externalId";
        final String AUTH_REQ_ID = "auth_red_id";

        AuthenticationDeviceNotifierProvider provider = mock(AuthenticationDeviceNotifierProvider.class);
        when(notifierManager.getAuthDeviceNotifierProviders()).thenReturn(List.of(provider));
        when(provider.extractUserResponse(any())).thenReturn(Single.just(Optional.of(new ADUserResponse(EXTERNAL_ID, STATE, false, "id-tok", "acc-tok"))));

        final JWT stateJwt = new JWT();
        stateJwt.setJti(EXTERNAL_ID);
        when(this.jwtService.decode(STATE, JWTService.TokenType.STATE)).thenReturn(Single.just(stateJwt));
        when(this.clientLookupService.findByClientId(any())).thenReturn(Maybe.just(new Client()));
        when(this.jwtService.decodeAndVerify(anyString(), any(Client.class), any())).thenReturn(Single.just(stateJwt));

        final CibaAuthRequest cibaRequest = new CibaAuthRequest();
        cibaRequest.setId(AUTH_REQ_ID);
        cibaRequest.setDeviceNotifierId("n1");
        when(this.requestRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Maybe.just(cibaRequest));

        final String status = AuthenticationRequestStatus.REJECTED.name();
        when(this.requestRepository.updateStatus(AUTH_REQ_ID, status)).thenReturn(Single.just(cibaRequest));

        final ADCallbackContext context = new ADCallbackContext(MultiMap.caseInsensitiveMultiMap(), MultiMap.caseInsensitiveMultiMap());
        final TestObserver<Void> observer = this.service.validateUserResponse(context, mock(io.gravitee.gateway.api.Request.class)).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertNoErrors();

        verify(identityProviderManager, never()).get(any());
        verify(userAuthenticationManager, never()).connect(any(), any(), any(), anyBoolean());
        verify(requestRepository, never()).update(any());
        verify(requestRepository).updateStatus(AUTH_REQ_ID, status);
        assertNull(cibaRequest.getSubject());
    }

    @Test
    public void shouldRideIdpPipeline_whenUserinfoMode_noIdToken() {
        final String STATE = "state", EXTERNAL_ID = "externalId", AUTH_REQ_ID = "auth_req", IDP = "idp-acme";
        AuthenticationDeviceNotifierProvider provider = mock(AuthenticationDeviceNotifierProvider.class);
        when(notifierManager.getAuthDeviceNotifierProviders()).thenReturn(List.of(provider));
        when(provider.extractUserResponse(any())).thenReturn(Single.just(Optional.of(
                new ADUserResponse(EXTERNAL_ID, STATE, true, null, "acc-tok", IDP))));   // no id_token
        final JWT stateJwt = new JWT(); stateJwt.setJti(EXTERNAL_ID);
        when(jwtService.decode(STATE, JWTService.TokenType.STATE)).thenReturn(Single.just(stateJwt));
        when(clientLookupService.findByClientId(any())).thenReturn(Maybe.just(new Client()));
        when(jwtService.decodeAndVerify(anyString(), any(Client.class), any())).thenReturn(Single.just(stateJwt));
        final CibaAuthRequest cibaRequest = new CibaAuthRequest(); cibaRequest.setId(AUTH_REQ_ID); cibaRequest.setDeviceNotifierId("n1");
        when(requestRepository.findByExternalId(EXTERNAL_ID)).thenReturn(Maybe.just(cibaRequest));
        io.gravitee.am.identityprovider.api.DefaultUser principal = new io.gravitee.am.identityprovider.api.DefaultUser("acme|9");
        principal.setAdditionalInformation(new java.util.HashMap<>());
        when(identityProviderManager.get(IDP)).thenReturn(Maybe.just(authProvider));
        when(authProvider.retrieveUserFromTokenResponse(eq("acc-tok"), isNull(), any())).thenReturn(Maybe.just(principal));
        io.gravitee.am.model.User local = new io.gravitee.am.model.User(); local.setId("local-uuid-1");
        when(userAuthenticationManager.connect(any(), isNull(), any(), eq(true))).thenReturn(Single.just(local));
        when(requestRepository.update(any())).thenAnswer(inv -> Single.just(inv.getArgument(0)));
        when(requestRepository.updateStatus(AUTH_REQ_ID, "SUCCESS")).thenReturn(Single.just(cibaRequest));

        final ADCallbackContext ctx = new ADCallbackContext(MultiMap.caseInsensitiveMultiMap(), MultiMap.caseInsensitiveMultiMap());
        service.validateUserResponse(ctx, mock(io.gravitee.gateway.api.Request.class)).test()
               .awaitDone(10, TimeUnit.SECONDS).assertNoErrors();
        verify(authProvider).retrieveUserFromTokenResponse(eq("acc-tok"), isNull(), any());
        verify(requestRepository).updateStatus(AUTH_REQ_ID, "SUCCESS");
    }

    @Test
    public void register_copiesAuthorizationDetails_ontoEntity() {
        java.util.Map<String, Object> d = new java.util.HashMap<>();
        d.put("type", "fdx_v1.0");
        CibaAuthenticationRequest request = new CibaAuthenticationRequest();
        request.setId("req-1");
        request.setScopes(java.util.Set.of("openid"));
        request.setSubject("alice");
        request.setAuthorizationDetails(java.util.List.of(d));

        ArgumentCaptor<CibaAuthRequest> captor = ArgumentCaptor.forClass(CibaAuthRequest.class);
        when(requestRepository.create(captor.capture())).thenAnswer(inv -> Single.just(inv.getArgument(0)));

        service.register(request, client).blockingGet();

        CibaAuthRequest entity = captor.getValue();
        assertNotNull(entity.getAuthorizationDetails());
        assertEquals("fdx_v1.0", entity.getAuthorizationDetails().get(0).get("type"));
    }

    @Test
    public void register_leavesAuthorizationDetailsNull_whenAbsent() {
        CibaAuthenticationRequest request = new CibaAuthenticationRequest();
        request.setId("req-2");
        request.setScopes(java.util.Set.of("openid"));
        request.setSubject("alice");

        ArgumentCaptor<CibaAuthRequest> captor = ArgumentCaptor.forClass(CibaAuthRequest.class);
        when(requestRepository.create(captor.capture())).thenAnswer(inv -> Single.just(inv.getArgument(0)));

        service.register(request, client).blockingGet();
        assertNull(captor.getValue().getAuthorizationDetails());
    }

}
