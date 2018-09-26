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
package io.gravitee.am.gateway.handler.oauth2.token;

import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.token.impl.TokenEnhancerImpl;
import io.gravitee.am.gateway.handler.oidc.idtoken.IDTokenService;
import io.gravitee.am.gateway.service.RoleService;
import io.gravitee.am.gateway.service.UserService;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.Role;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.gravitee.am.service.exception.ClientNotFoundException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class TokenEnhancerTest {

    @InjectMocks
    private TokenEnhancer tokenEnhancer = new TokenEnhancerImpl();

    @Mock
    private ClientService clientService;

    @Mock
    private UserService userService;

    @Mock
    private RoleService roleService;

    @Mock
    private IDTokenService idTokenService;

    @Test
    public void shouldEnhanceToken_withoutIDToken() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        // no openid scope for the request

        Client client = new Client();

        AccessToken accessToken = new AccessToken();
        accessToken.setId("token-id");
        accessToken.setToken("token-id");

        when(clientService.findByClientId(anyString())).thenReturn(Maybe.just(client));

        TestObserver<AccessToken> testObserver = tokenEnhancer.enhance(accessToken, oAuth2Request).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(accessToken1 -> accessToken1.getAdditionalInformation().isEmpty());
    }

    @Test
    public void shouldEnhanceToken_clientNotFound() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        // no openid scope for the request

        AccessToken accessToken = new AccessToken();
        accessToken.setId("token-id");
        accessToken.setToken("token-id");

        when(clientService.findByClientId(anyString())).thenReturn(Maybe.empty());

        TestObserver<AccessToken> testObserver = tokenEnhancer.enhance(accessToken, oAuth2Request).test();

        testObserver.assertNotComplete();
        testObserver.assertError(ClientNotFoundException.class);
    }

    @Test
    public void shouldEnhanceToken_withIDToken_clientOnly() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(Collections.singleton("openid"));

        Client client = new Client();
        client.setCertificate("client-certificate");

        AccessToken accessToken = new AccessToken();
        accessToken.setId("token-id");
        accessToken.setToken("token-id");

        String idTokenPayload = "payload";

        when(clientService.findByClientId(anyString())).thenReturn(Maybe.just(client));
        when(idTokenService.create(any(), any(), any())).thenReturn(Single.just(idTokenPayload));

        TestObserver<AccessToken> testObserver = tokenEnhancer.enhance(accessToken, oAuth2Request).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(accessToken1 -> accessToken1.getAdditionalInformation().containsKey("id_token"));

        verify(clientService, times(1)).findByClientId(anyString());
        verify(idTokenService, times(1)).create(any(), any(), any());
        verify(userService, never()).findById(anyString());
        verify(roleService, never()).findByIdIn(anyList());
    }

    @Test
    public void shouldEnhanceToken_withIDToken_withUser_enhanceScopes() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(Collections.singleton("openid"));
        oAuth2Request.setSubject("subject");

        Client client = new Client();
        client.setEnhanceScopesWithUserPermissions(true);

        User user = new User();
        user.setRoles(Collections.singletonList("dev"));

        Role role = new Role();
        role.setId("dev");
        role.setPermissions(Collections.singletonList("write"));

        AccessToken accessToken = new AccessToken();
        accessToken.setId("token-id");
        accessToken.setToken("token-id");
        accessToken.setScopes(Collections.singleton("openid"));


        when(userService.findById(anyString())).thenReturn(Maybe.just(user));
        when(roleService.findByIdIn(anyList())).thenReturn(Single.just(Collections.singleton(role)));
        when(clientService.findByClientId(anyString())).thenReturn(Maybe.just(client));
        when(idTokenService.create(any(), any(), any())).thenReturn(Single.just("payload"));

        TestObserver<AccessToken> testObserver = tokenEnhancer.enhance(accessToken, oAuth2Request).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(accessToken1 -> accessToken1.getAdditionalInformation().containsKey("id_token") && accessToken1.getScopes().contains("write"));

        verify(clientService, times(1)).findByClientId(anyString());
        verify(idTokenService, times(1)).create(any(), any(), any());
        verify(userService, times(1)).findById(anyString());
        verify(roleService, times(1)).findByIdIn(anyList());
    }

    @Test
    public void shouldEnhanceToken_withUserPermissions_disableEnhanceScope() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(Collections.singleton("read"));
        oAuth2Request.setSubject("subject");

        Client client = new Client();
        client.setEnhanceScopesWithUserPermissions(false);

        User user = new User();
        user.setRoles(Collections.singletonList("dev"));

        Role role = new Role();
        role.setId("dev");
        role.setPermissions(Collections.singletonList("write"));

        AccessToken accessToken = new AccessToken();
        accessToken.setId("token-id");
        accessToken.setToken("token-id");
        accessToken.setScopes(Collections.singleton("read"));

        when(userService.findById(anyString())).thenReturn(Maybe.just(user));
        when(roleService.findByIdIn(anyList())).thenReturn(Single.just(Collections.singleton(role)));
        when(clientService.findByClientId(anyString())).thenReturn(Maybe.just(client));

        TestObserver<AccessToken> testObserver = tokenEnhancer.enhance(accessToken, oAuth2Request).test();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(accessToken1 -> accessToken1.getScopes().contains("read") && !accessToken1.getScopes().contains("write"));
    }

    @Test
    public void shouldEnhanceToken_withIDToken_withUser_userNotFound() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("client-id");
        oAuth2Request.setScopes(Collections.singleton("openid"));
        oAuth2Request.setSubject("subject");

        Client client = new Client();

        AccessToken accessToken = new AccessToken();
        accessToken.setId("token-id");
        accessToken.setToken("token-id");
        accessToken.setScopes(Collections.singleton("openid"));

        when(userService.findById(anyString())).thenReturn(Maybe.empty());
        when(clientService.findByClientId(anyString())).thenReturn(Maybe.just(client));

        TestObserver<AccessToken> testObserver = tokenEnhancer.enhance(accessToken, oAuth2Request).test();

        testObserver.assertNotComplete();
        testObserver.assertError(UserNotFoundException.class);

        verify(clientService, times(1)).findByClientId(anyString());
        verify(userService, times(1)).findById(anyString());
        verify(idTokenService, never()).create(any(), any(), any());
        verify(roleService, never()).findByIdIn(anyList());
    }
}
