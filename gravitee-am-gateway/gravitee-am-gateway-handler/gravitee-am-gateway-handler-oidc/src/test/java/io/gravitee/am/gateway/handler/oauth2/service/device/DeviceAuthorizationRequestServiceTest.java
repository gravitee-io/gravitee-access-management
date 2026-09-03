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
package io.gravitee.am.gateway.handler.oauth2.service.device;

import io.gravitee.am.gateway.handler.oauth2.exception.AuthorizationPendingException;
import io.gravitee.am.gateway.handler.oauth2.exception.ExpiredTokenException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.exception.SlowDownException;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.application.ApplicationDeviceFlowSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.DeviceFlowSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.repository.oauth2.api.DeviceAuthorizationRequestRepository;
import io.gravitee.am.repository.oauth2.model.DeviceAuthorizationRequest;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class DeviceAuthorizationRequestServiceTest {

    private static final int RETENTION = 900;
    private static final int EXPIRY = 600;
    private static final int INTERVAL = 5;

    @Mock
    private DeviceAuthorizationRequestRepository repository;

    @Mock
    private Domain domain;

    @InjectMocks
    private DeviceAuthorizationRequestServiceImpl service;

    private Client client;

    @BeforeEach
    void init() {
        ReflectionTestUtils.setField(service, "requestRetentionInSec", RETENTION);
        client = new Client();
        client.setClientId("client-id");
    }

    private void withDeviceFlowSettings() {
        DeviceFlowSettings settings = new DeviceFlowSettings();
        settings.setEnabled(true);
        settings.setDeviceCodeExpiry(EXPIRY);
        settings.setPollingInterval(INTERVAL);
        OIDCSettings oidc = new OIDCSettings();
        oidc.setDeviceFlowSettings(settings);
        when(domain.getOidc()).thenReturn(oidc);
    }

    @Test
    void shouldRegisterRequestWithGeneratedCodesAndPendingStatus() {
        withDeviceFlowSettings();
        when(repository.create(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        TestObserver<DeviceAuthorizationRequest> observer = service.register(client, Set.of("openid")).test();

        observer.assertComplete();
        ArgumentCaptor<DeviceAuthorizationRequest> captor = ArgumentCaptor.forClass(DeviceAuthorizationRequest.class);
        org.mockito.Mockito.verify(repository).create(captor.capture());
        DeviceAuthorizationRequest created = captor.getValue();
        assertEquals(UserCodeGenerator.LENGTH, created.getUserCode().length());
        assertEquals(DeviceAuthorizationRequestStatus.PENDING.name(), created.getStatus());
        assertEquals("client-id", created.getClientId());
        assertEquals(Set.of("openid"), created.getScopes());
        long ttlInSec = (created.getExpireAt().getTime() - created.getCreatedAt().getTime()) / 1000;
        assertEquals(EXPIRY + RETENTION, ttlInSec);
    }

    @Test
    void shouldRejectUnknownDeviceCode() {
        withDeviceFlowSettings();
        when(repository.findById("unknown")).thenReturn(Maybe.empty());

        service.retrieve("unknown", client).test().assertError(InvalidGrantException.class);
    }

    @Test
    void shouldRejectDeviceCodeIssuedToAnotherClient() {
        withDeviceFlowSettings();
        DeviceAuthorizationRequest request = pendingRequest();
        request.setClientId("another-client");
        when(repository.findById("device-code")).thenReturn(Maybe.just(request));

        service.retrieve("device-code", client).test().assertError(InvalidGrantException.class);
    }

    @Test
    void shouldReportPendingWhileNotApproved() {
        withDeviceFlowSettings();
        DeviceAuthorizationRequest request = pendingRequest();
        request.setLastAccessAt(new Date(Instant.now().minusSeconds(INTERVAL + 1L).toEpochMilli()));
        when(repository.findById("device-code")).thenReturn(Maybe.just(request));
        when(repository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        service.retrieve("device-code", client).test().assertError(AuthorizationPendingException.class);
    }

    @Test
    void shouldReportExpiredRatherThanMissingInsideRetentionWindow() {
        withDeviceFlowSettings();
        DeviceAuthorizationRequest request = pendingRequest();
        request.setExpireAt(new Date(Instant.now().plusSeconds(RETENTION - 10L).toEpochMilli()));
        when(repository.findById("device-code")).thenReturn(Maybe.just(request));

        service.retrieve("device-code", client).test().assertError(ExpiredTokenException.class);
    }

    @Test
    void shouldNormalizeUserCodeBeforeLookup() {
        DeviceAuthorizationRequest request = pendingRequest();
        when(repository.findByUserCode("BCDFGHJK")).thenReturn(Maybe.just(request));

        service.findByUserCode("bcdf-ghjk").test().assertValue(request);
    }

    @Test
    void shouldReturnEmptyForBlankUserCode() {
        service.findByUserCode(" - ").test().assertNoValues().assertComplete();
    }

    @Test
    void shouldApproveWithSubjectAndScopes() {
        DeviceAuthorizationRequest request = pendingRequest();
        when(repository.findById("device-code")).thenReturn(Maybe.just(request));
        when(repository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        service.approve("device-code", "client-id", "user-id", Set.of("openid")).test().assertComplete();

        assertEquals(DeviceAuthorizationRequestStatus.APPROVED.name(), request.getStatus());
        assertEquals("user-id", request.getSubject());
        assertEquals(Set.of("openid"), request.getScopes());
    }

    @Test
    void shouldRefuseToApproveForAnotherClient() {
        DeviceAuthorizationRequest request = pendingRequest();
        when(repository.findById("device-code")).thenReturn(Maybe.just(request));

        service.approve("device-code", "another-client", "user-id", Set.of("openid")).test()
                .assertError(InvalidGrantException.class);

        assertEquals(DeviceAuthorizationRequestStatus.PENDING.name(), request.getStatus());
    }

    @Test
    void shouldRefuseToApproveARequestThatIsNoLongerPending() {
        DeviceAuthorizationRequest request = pendingRequest();
        request.setStatus(DeviceAuthorizationRequestStatus.APPROVED.name());
        when(repository.findById("device-code")).thenReturn(Maybe.just(request));

        service.approve("device-code", "client-id", "user-id", Set.of("openid")).test()
                .assertError(InvalidGrantException.class);
    }

    @Test
    void shouldRefuseToApproveAnExpiredRequest() {
        DeviceAuthorizationRequest request = pendingRequest();
        request.setExpireAt(new Date(Instant.now().plusSeconds(RETENTION - 10L).toEpochMilli()));
        when(repository.findById("device-code")).thenReturn(Maybe.just(request));

        service.approve("device-code", "client-id", "user-id", Set.of("openid")).test()
                .assertError(ExpiredTokenException.class);
    }

    @Test
    void shouldDenyAPendingRequest() {
        DeviceAuthorizationRequest request = pendingRequest();
        when(repository.findById("device-code")).thenReturn(Maybe.just(request));
        when(repository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        service.deny("device-code", "client-id").test().assertComplete();

        assertEquals(DeviceAuthorizationRequestStatus.DENIED.name(), request.getStatus());
    }

    @Test
    void shouldRefuseToDenyForAnotherClient() {
        DeviceAuthorizationRequest request = pendingRequest();
        when(repository.findById("device-code")).thenReturn(Maybe.just(request));

        service.deny("device-code", "another-client").test().assertError(InvalidGrantException.class);

        assertEquals(DeviceAuthorizationRequestStatus.PENDING.name(), request.getStatus());
    }

    @Test
    void shouldRefuseToApproveARequestThatWasDenied() {
        DeviceAuthorizationRequest request = pendingRequest();
        request.setStatus(DeviceAuthorizationRequestStatus.DENIED.name());
        when(repository.findById("device-code")).thenReturn(Maybe.just(request));

        service.approve("device-code", "client-id", "user-id", Set.of("openid")).test()
                .assertError(InvalidGrantException.class);

        assertEquals(DeviceAuthorizationRequestStatus.DENIED.name(), request.getStatus());
    }

    @Test
    void shouldWidenTheIntervalByFiveSecondsOnEachSlowDown() {
        withDeviceFlowSettings();
        DeviceAuthorizationRequest request = pendingRequest();
        when(repository.findById("device-code")).thenReturn(Maybe.just(request));
        when(repository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        service.retrieve("device-code", client).test().assertError(SlowDownException.class);
        assertEquals(5, request.getIntervalIncrement());

        service.retrieve("device-code", client).test().assertError(SlowDownException.class);
        assertEquals(10, request.getIntervalIncrement());
    }

    @Test
    void shouldAdvanceLastAccessOnEachSlowDown() {
        withDeviceFlowSettings();
        DeviceAuthorizationRequest request = pendingRequest();
        Date previousAccess = new Date(Instant.now().minusSeconds(1L).toEpochMilli());
        request.setLastAccessAt(previousAccess);
        when(repository.findById("device-code")).thenReturn(Maybe.just(request));
        when(repository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        service.retrieve("device-code", client).test().assertError(SlowDownException.class);

        assertTrue(request.getLastAccessAt().after(previousAccess));
    }

    @Test
    void shouldCapTheEscalatedIntervalAtSixtySeconds() {
        withDeviceFlowSettings();
        DeviceAuthorizationRequest request = pendingRequest();
        when(repository.findById("device-code")).thenReturn(Maybe.just(request));
        when(repository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        for (int i = 0; i < 50; i++) {
            service.retrieve("device-code", client).test().assertError(SlowDownException.class);
        }

        assertEquals(60 - INTERVAL, request.getIntervalIncrement());
        assertEquals(60, service.computeIntervalInSec(request, INTERVAL));
    }

    @Test
    void shouldNotSlowDownAClientThatRespectsTheInterval() {
        withDeviceFlowSettings();
        DeviceAuthorizationRequest request = pendingRequest();
        request.setLastAccessAt(new Date(Instant.now().minusSeconds(INTERVAL + 1L).toEpochMilli()));
        when(repository.findById("device-code")).thenReturn(Maybe.just(request));
        when(repository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        service.retrieve("device-code", client).test().assertError(AuthorizationPendingException.class);

        assertEquals(0, request.getIntervalIncrement());
    }

    @Test
    void shouldServeAWidenedRequestNormallyOnceTheWidenedIntervalElapsed() {
        withDeviceFlowSettings();
        DeviceAuthorizationRequest request = pendingRequest();
        request.setIntervalIncrement(10);
        request.setLastAccessAt(new Date(Instant.now().minusSeconds(INTERVAL + 10L + 1L).toEpochMilli()));
        when(repository.findById("device-code")).thenReturn(Maybe.just(request));
        when(repository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        service.retrieve("device-code", client).test().assertError(AuthorizationPendingException.class);

        assertEquals(10, request.getIntervalIncrement());
    }

    @Test
    void shouldDetectExpiryAgainstTheRetentionWindow() {
        DeviceAuthorizationRequest request = pendingRequest();
        assertFalse(service.isExpired(request));

        request.setExpireAt(new Date(Instant.now().plusSeconds(RETENTION - 10L).toEpochMilli()));
        assertTrue(service.isExpired(request));
    }

    @Test
    void shouldInheritTheDomainTimingsWhenTheApplicationDoesNotOverrideThem() {
        withDeviceFlowSettings();

        assertEquals(EXPIRY, service.getDeviceCodeExpiryInSec(client));
        assertEquals(INTERVAL, service.getPollingIntervalInSec(client));
    }

    @Test
    void shouldFollowTheDomainTimingsAsTheyChange() {
        withDeviceFlowSettings();
        assertEquals(EXPIRY, service.getDeviceCodeExpiryInSec(client));

        domain.getOidc().getDeviceFlowSettings().setDeviceCodeExpiry(1200);
        domain.getOidc().getDeviceFlowSettings().setPollingInterval(9);

        assertEquals(1200, service.getDeviceCodeExpiryInSec(client));
        assertEquals(9, service.getPollingIntervalInSec(client));
    }

    @Test
    void shouldPreferTheApplicationTimingsOverTheDomainOnes() {
        withApplicationOverride(120, 2);

        assertEquals(120, service.getDeviceCodeExpiryInSec(client));
        assertEquals(2, service.getPollingIntervalInSec(client));
    }

    @Test
    void shouldIssueACodeLivingForTheApplicationExpiry() {
        withApplicationOverride(120, 2);
        when(repository.create(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        service.register(client, Set.of("openid")).test().assertComplete();

        ArgumentCaptor<DeviceAuthorizationRequest> captor = ArgumentCaptor.forClass(DeviceAuthorizationRequest.class);
        org.mockito.Mockito.verify(repository).create(captor.capture());
        DeviceAuthorizationRequest created = captor.getValue();
        assertEquals(120 + RETENTION, (created.getExpireAt().getTime() - created.getCreatedAt().getTime()) / 1000);
    }

    @Test
    void shouldHoldAPollingDeviceToTheApplicationInterval() {
        withApplicationOverride(120, 2);
        DeviceAuthorizationRequest request = pendingRequest();
        request.setLastAccessAt(new Date(Instant.now().minusSeconds(3L).toEpochMilli()));
        when(repository.findById("device-code")).thenReturn(Maybe.just(request));
        when(repository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        service.retrieve("device-code", client).test().assertError(AuthorizationPendingException.class);

        request.setLastAccessAt(new Date());
        service.retrieve("device-code", client).test().assertError(SlowDownException.class);
    }

    @Test
    void shouldWidenTheApplicationIntervalOnSlowDown() {
        withApplicationOverride(120, 2);
        DeviceAuthorizationRequest request = pendingRequest();
        when(repository.findById("device-code")).thenReturn(Maybe.just(request));
        when(repository.update(any())).thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

        service.retrieve("device-code", client).test().assertError(SlowDownException.class);

        assertEquals(5, request.getIntervalIncrement());
    }

    private void withApplicationOverride(int expiry, int interval) {
        ApplicationDeviceFlowSettings override = new ApplicationDeviceFlowSettings();
        override.setDeviceCodeExpiry(expiry);
        override.setPollingInterval(interval);
        client.setDeviceFlowSettings(override);
    }

    private DeviceAuthorizationRequest pendingRequest() {
        DeviceAuthorizationRequest request = new DeviceAuthorizationRequest();
        request.setId("device-code");
        request.setUserCode("BCDFGHJK");
        request.setClientId("client-id");
        request.setStatus(DeviceAuthorizationRequestStatus.PENDING.name());
        request.setCreatedAt(new Date());
        request.setLastAccessAt(new Date());
        request.setExpireAt(new Date(Instant.now().plusSeconds(EXPIRY + RETENTION).toEpochMilli()));
        return request;
    }
}
