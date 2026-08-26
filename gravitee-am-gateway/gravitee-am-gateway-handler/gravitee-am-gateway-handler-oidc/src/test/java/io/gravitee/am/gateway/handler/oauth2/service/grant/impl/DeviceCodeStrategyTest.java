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
package io.gravitee.am.gateway.handler.oauth2.service.grant.impl;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.auth.user.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.oauth2.exception.AuthorizationPendingException;
import io.gravitee.am.gateway.handler.oauth2.service.device.DeviceAuthorizationRequestService;
import io.gravitee.am.gateway.handler.oauth2.service.grant.GrantData;
import io.gravitee.am.gateway.handler.oauth2.service.grant.TokenCreationRequest;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.application.ApplicationDeviceFlowSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.oidc.DeviceFlowSettings;
import io.gravitee.am.model.oidc.OIDCSettings;
import io.gravitee.am.repository.oauth2.model.DeviceAuthorizationRequest;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
class DeviceCodeStrategyTest {

    @Mock
    private DeviceAuthorizationRequestService requestService;

    @Mock
    private UserAuthenticationManager userAuthenticationManager;

    private DeviceCodeStrategy strategy;
    private Domain domain;
    private Client client;

    @BeforeEach
    void init() {
        strategy = new DeviceCodeStrategy(requestService, userAuthenticationManager);
        domain = new Domain();
        DeviceFlowSettings settings = new DeviceFlowSettings();
        settings.setEnabled(true);
        OIDCSettings oidc = new OIDCSettings();
        oidc.setDeviceFlowSettings(settings);
        domain.setOidc(oidc);
        client = new Client();
        client.setClientId("client-id");
        client.setAuthorizedGrantTypes(List.of(GrantType.DEVICE_CODE));
    }

    @Test
    void shouldSupportDeviceCodeGrantWhenEnabled() {
        assertTrue(strategy.supports(GrantType.DEVICE_CODE, client, domain));
    }

    @Test
    void shouldNotSupportAnotherGrantType() {
        assertFalse(strategy.supports(GrantType.AUTHORIZATION_CODE, client, domain));
    }

    @Test
    void shouldNotSupportWhenDeviceFlowIsDisabledOnDomain() {
        domain.getOidc().getDeviceFlowSettings().setEnabled(false);
        assertFalse(strategy.supports(GrantType.DEVICE_CODE, client, domain));
    }

    @Test
    void shouldNotLetAnApplicationOverrideTheDomainSwitch() {
        domain.getOidc().getDeviceFlowSettings().setEnabled(false);
        client.setDeviceFlowSettings(new ApplicationDeviceFlowSettings());

        assertFalse(strategy.supports(GrantType.DEVICE_CODE, client, domain));
    }

    @Test
    void shouldNotSupportWhenClientHasNotTheGrantType() {
        client.setAuthorizedGrantTypes(List.of(GrantType.AUTHORIZATION_CODE));
        assertFalse(strategy.supports(GrantType.DEVICE_CODE, client, domain));
    }

    @Test
    void shouldRejectRequestWithoutDeviceCode() {
        strategy.process(tokenRequest(null), client, domain).test().assertError(InvalidRequestException.class);
    }

    @Test
    void shouldPropagatePollingErrors() {
        when(requestService.retrieve(eq("device-code"), any())).thenReturn(Single.error(new AuthorizationPendingException()));

        strategy.process(tokenRequest("device-code"), client, domain).test()
                .assertError(AuthorizationPendingException.class);
    }

    @Test
    void shouldCarryStoredScopesAndSubjectToTheTokenRequest() {
        DeviceAuthorizationRequest deviceRequest = new DeviceAuthorizationRequest();
        deviceRequest.setId("device-code");
        deviceRequest.setClientId("client-id");
        deviceRequest.setSubject("user-id");
        deviceRequest.setScopes(Set.of("openid", "read"));
        when(requestService.retrieve(eq("device-code"), any())).thenReturn(Single.just(deviceRequest));
        User user = new User();
        user.setId("user-id");
        when(userAuthenticationManager.loadPreAuthenticatedUser(eq("user-id"), any())).thenReturn(Maybe.just(user));

        TokenRequest request = tokenRequest("device-code");
        TestObserver<TokenCreationRequest> observer = strategy.process(request, client, domain).test();

        observer.assertComplete();
        TokenCreationRequest result = observer.values().getFirst();
        assertEquals(GrantType.DEVICE_CODE, result.grantType());
        assertEquals(Set.of("openid", "read"), result.scopes());
        assertEquals(user, result.resourceOwner());
        assertFalse(result.supportRefreshToken());
        assertInstanceOf(GrantData.DeviceCodeData.class, result.grantData());
        assertEquals("device-code", ((GrantData.DeviceCodeData) result.grantData()).deviceCode());
    }

    @Test
    void shouldSupportRefreshTokenWhenClientCarriesTheGrantType() {
        client.setAuthorizedGrantTypes(List.of(GrantType.DEVICE_CODE, GrantType.REFRESH_TOKEN));
        DeviceAuthorizationRequest deviceRequest = new DeviceAuthorizationRequest();
        deviceRequest.setId("device-code");
        deviceRequest.setClientId("client-id");
        deviceRequest.setSubject("user-id");
        deviceRequest.setScopes(Set.of("openid"));
        when(requestService.retrieve(eq("device-code"), any())).thenReturn(Single.just(deviceRequest));
        when(userAuthenticationManager.loadPreAuthenticatedUser(eq("user-id"), any())).thenReturn(Maybe.just(new User()));

        TestObserver<TokenCreationRequest> observer = strategy.process(tokenRequest("device-code"), client, domain).test();

        observer.assertComplete();
        assertTrue(observer.values().getFirst().supportRefreshToken());
    }

    private TokenRequest tokenRequest(String deviceCode) {
        TokenRequest request = new TokenRequest();
        request.setClientId("client-id");
        request.setGrantType(GrantType.DEVICE_CODE);
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        if (deviceCode != null) {
            parameters.add(Parameters.DEVICE_CODE, deviceCode);
        }
        request.setParameters(parameters);
        return request;
    }
}
