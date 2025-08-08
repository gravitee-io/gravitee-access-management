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
package io.gravitee.am.gateway.services.purge;

import io.gravitee.am.dataplane.api.DataPlaneProvider;
import io.gravitee.am.dataplane.api.repository.DeviceRepository;
import io.gravitee.am.dataplane.api.repository.LoginAttemptRepository;
import io.gravitee.am.dataplane.api.repository.PermissionTicketRepository;
import io.gravitee.am.dataplane.api.repository.ScopeApprovalRepository;
import io.gravitee.am.dataplane.api.repository.UserActivityRepository;
import io.gravitee.am.plugins.dataplane.core.SingleDataPlaneProvider;
import io.gravitee.am.repository.gateway.api.AuthenticationFlowContextRepository;
import io.gravitee.am.repository.management.api.EventRepository;
import io.gravitee.am.repository.oauth2.api.AccessTokenRepository;
import io.gravitee.am.repository.oauth2.api.AuthorizationCodeRepository;
import io.gravitee.am.repository.oauth2.api.PushedAuthorizationRequestRepository;
import io.gravitee.am.repository.oauth2.api.RefreshTokenRepository;
import io.gravitee.am.repository.oidc.api.CibaAuthRequestRepository;
import io.gravitee.am.repository.oidc.api.RequestObjectRepository;
import io.reactivex.rxjava3.core.Completable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class PurgeManagerTest {

    @InjectMocks
    private PurgeManager manager;

    @Mock
    private LoginAttemptRepository loginAttemptRepository;

    @Mock
    private PermissionTicketRepository permissionTicketRepository;

    @Mock
    private AccessTokenRepository accessTokenRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RequestObjectRepository requestObjectRepository;

    @Mock
    private ScopeApprovalRepository scopeApprovalRepository;

    @Mock
    private AuthorizationCodeRepository authorizationCodeRepository;

    @Mock
    private AuthenticationFlowContextRepository authenticationFlowContextRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    protected PushedAuthorizationRequestRepository pushedAuthRequestRepository;

    @Mock
    protected CibaAuthRequestRepository cibaAuthRequestRepository;

    @Mock
    protected UserActivityRepository userActivityRepository;

    @Mock
    protected SingleDataPlaneProvider singleDataPlaneProvider;

    @Mock
    protected EventRepository eventRepository;

    @Before
    public void prepare() {
        DataPlaneProvider dataPlaneProvider = Mockito.mock();
        when(singleDataPlaneProvider.get()).thenReturn(dataPlaneProvider);
        when(dataPlaneProvider.getLoginAttemptRepository()).thenReturn(loginAttemptRepository);
        when(dataPlaneProvider.getDeviceRepository()).thenReturn(deviceRepository);
        when(dataPlaneProvider.getUserActivityRepository()).thenReturn(userActivityRepository);
        when(dataPlaneProvider.getScopeApprovalRepository()).thenReturn(scopeApprovalRepository);
        when(dataPlaneProvider.getPermissionTicketRepository()).thenReturn(permissionTicketRepository);

        when(accessTokenRepository.purgeExpiredData()).thenReturn(Completable.complete());
        when(loginAttemptRepository.purgeExpiredData()).thenReturn(Completable.complete());
        when(permissionTicketRepository.purgeExpiredData()).thenReturn(Completable.complete());
        when(authorizationCodeRepository.purgeExpiredData()).thenReturn(Completable.complete());
        when(scopeApprovalRepository.purgeExpiredData()).thenReturn(Completable.complete());
        when(refreshTokenRepository.purgeExpiredData()).thenReturn(Completable.complete());
        when(requestObjectRepository.purgeExpiredData()).thenReturn(Completable.complete());
        when(authenticationFlowContextRepository.purgeExpiredData()).thenReturn(Completable.complete());
        when(pushedAuthRequestRepository.purgeExpiredData()).thenReturn(Completable.complete());
        when(cibaAuthRequestRepository.purgeExpiredData()).thenReturn(Completable.complete());
        when(deviceRepository.purgeExpiredData()).thenReturn(Completable.complete());
        when(userActivityRepository.purgeExpiredData()).thenReturn(Completable.complete());
        when(eventRepository.purgeExpiredData()).thenReturn(Completable.complete());
    }

    @Test
    public void testNullExclude() {
        manager.purge(null);

        verify(accessTokenRepository).purgeExpiredData();
        verify(loginAttemptRepository).purgeExpiredData();
        verify(permissionTicketRepository).purgeExpiredData();
        verify(authorizationCodeRepository).purgeExpiredData();
        verify(scopeApprovalRepository).purgeExpiredData();
        verify(refreshTokenRepository).purgeExpiredData();
        verify(requestObjectRepository).purgeExpiredData();
        verify(authenticationFlowContextRepository).purgeExpiredData();
        verify(pushedAuthRequestRepository).purgeExpiredData();
        verify(cibaAuthRequestRepository).purgeExpiredData();
        verify(deviceRepository).purgeExpiredData();
        verify(userActivityRepository).purgeExpiredData();
        verify(eventRepository).purgeExpiredData();
    }

    @Test
    public void testEmptyExclude() {
        manager.purge(Collections.emptyList());

        verify(accessTokenRepository).purgeExpiredData();
        verify(loginAttemptRepository).purgeExpiredData();
        verify(permissionTicketRepository).purgeExpiredData();
        verify(authorizationCodeRepository).purgeExpiredData();
        verify(scopeApprovalRepository).purgeExpiredData();
        verify(refreshTokenRepository).purgeExpiredData();
        verify(requestObjectRepository).purgeExpiredData();
        verify(authenticationFlowContextRepository).purgeExpiredData();
        verify(pushedAuthRequestRepository).purgeExpiredData();
        verify(cibaAuthRequestRepository).purgeExpiredData();
        verify(deviceRepository).purgeExpiredData();
        verify(userActivityRepository).purgeExpiredData();
        verify(eventRepository).purgeExpiredData();
    }

    @Test
    public void testExclude_AccessToken() {
        manager.purge(Arrays.asList(TableName.access_tokens));

        verify(accessTokenRepository, never()).purgeExpiredData();
        verify(loginAttemptRepository).purgeExpiredData();
        verify(permissionTicketRepository).purgeExpiredData();
        verify(authorizationCodeRepository).purgeExpiredData();
        verify(scopeApprovalRepository).purgeExpiredData();
        verify(refreshTokenRepository).purgeExpiredData();
        verify(requestObjectRepository).purgeExpiredData();
        verify(authenticationFlowContextRepository).purgeExpiredData();
        verify(pushedAuthRequestRepository).purgeExpiredData();
        verify(cibaAuthRequestRepository).purgeExpiredData();
        verify(deviceRepository).purgeExpiredData();
        verify(userActivityRepository).purgeExpiredData();
        verify(eventRepository).purgeExpiredData();
    }
}
