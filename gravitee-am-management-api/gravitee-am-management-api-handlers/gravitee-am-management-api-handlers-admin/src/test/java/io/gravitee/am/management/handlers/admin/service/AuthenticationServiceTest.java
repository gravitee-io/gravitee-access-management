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
package io.gravitee.am.management.handlers.admin.service;

import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.management.handlers.admin.service.impl.AuthenticationServiceImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.UserService;
import io.gravitee.am.service.model.NewUser;
import io.gravitee.am.service.model.UpdateUser;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthenticationServiceTest {

    private static final String domainId = "domain-id";

    @InjectMocks
    private AuthenticationService authenticationService = new AuthenticationServiceImpl();

    @Mock
    private AuthenticationSuccessEvent eventMock;

    @Mock
    private Authentication authenticationMock;

    @Mock
    private User userDetailsMock;

    @Mock
    private UserService userServiceMock;

    @Mock
    private Domain domainMock;

    @Mock
    private io.gravitee.am.model.User repositoryUserMock;

    @Mock
    private AuditService auditService;

    @Test
    public void shouldCreateUser() {
        when(domainMock.getId()).thenReturn(domainId);
        when(authenticationMock.getPrincipal()).thenReturn(userDetailsMock);
        when(userServiceMock.findByDomainAndExternalIdAndSource(domainMock.getId(), userDetailsMock.getUsername(), null)).thenReturn(Maybe.empty());
        when(userServiceMock.findByDomainAndUsernameAndSource(domainMock.getId(), userDetailsMock.getUsername(), null)).thenReturn(Maybe.empty());
        when(userServiceMock.create(anyString(), any(NewUser.class))).thenReturn(Single.just(new io.gravitee.am.model.User()));

        authenticationService.onAuthenticationSuccess(authenticationMock);

        verify(userServiceMock, times(1)).findByDomainAndExternalIdAndSource(domainMock.getId(), userDetailsMock.getUsername(), null);
        verify(userServiceMock, times(1)).findByDomainAndUsernameAndSource(domainMock.getId(), userDetailsMock.getUsername(), null);
        verify(userServiceMock, times(1)).create(any(String.class), any(NewUser.class));
        verify(userServiceMock, never()).update(any(String.class), any(String.class), any(UpdateUser.class));
    }

    @Test
    public void shouldUpdatedUser() {
        when(domainMock.getId()).thenReturn(domainId);
        when(repositoryUserMock.getId()).thenReturn("userId");
        when(authenticationMock.getPrincipal()).thenReturn(userDetailsMock);
        when(userServiceMock.findByDomainAndExternalIdAndSource(domainMock.getId(), userDetailsMock.getUsername(), null)).thenReturn(Maybe.just(repositoryUserMock));
        when(userServiceMock.update(anyString(), anyString(), any(UpdateUser.class))).thenReturn(Single.just(new io.gravitee.am.model.User()));

        authenticationService.onAuthenticationSuccess(authenticationMock);

        verify(userServiceMock, times(1)).findByDomainAndExternalIdAndSource(domainMock.getId(), userDetailsMock.getUsername(), null);
        verify(userServiceMock, times(1)).update(any(String.class), any(String.class), any(UpdateUser.class));
        verify(userServiceMock, never()).create(any(String.class), any(NewUser.class));
    }
}
