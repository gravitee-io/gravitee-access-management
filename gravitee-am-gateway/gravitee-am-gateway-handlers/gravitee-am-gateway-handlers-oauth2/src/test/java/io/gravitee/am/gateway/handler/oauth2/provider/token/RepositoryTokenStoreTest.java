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
package io.gravitee.am.gateway.handler.oauth2.provider.token;

import io.gravitee.am.gateway.handler.oauth2.provider.RepositoryProviderUtils;
import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.repository.oauth2.model.OAuth2AccessToken;
import io.gravitee.am.repository.oauth2.model.OAuth2Authentication;
import io.gravitee.am.repository.oauth2.model.OAuth2RefreshToken;
import io.gravitee.am.repository.oauth2.model.request.OAuth2Request;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class RepositoryTokenStoreTest {

    @InjectMocks
    private RepositoryTokenStore tokenStore = new RepositoryTokenStore();

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private OAuth2AccessToken oAuth2AccessToken;

    @Mock
    private org.springframework.security.oauth2.common.OAuth2AccessToken springOAuth2AccessToken;

    @Mock
    private OAuth2RefreshToken oAuth2RefreshToken;

    @Mock
    private org.springframework.security.oauth2.common.OAuth2RefreshToken springOAuth2RefreshToken;

    @Mock
    private OAuth2Authentication oAuth2Authentication;

    @Mock
    private OAuth2Request oAuth2Request;

    @Test
    public void shouldReadAuthentication() {
        // prepare OAuth2AccessToken
        final String tokenId = "test-token";
        when(oAuth2AccessToken.getValue()).thenReturn(tokenId);
        when(springOAuth2AccessToken.getValue()).thenReturn(tokenId);

        // prepare OAuth2Authentication
        final String clientId = "test-client";
        when(oAuth2Request.getClientId()).thenReturn(clientId);
        when(oAuth2Authentication.getOAuth2Request()).thenReturn(oAuth2Request);
        when(tokenRepository.readAuthentication(any(OAuth2AccessToken.class))).thenReturn(Optional.ofNullable(oAuth2Authentication));

        // Run
        final org.springframework.security.oauth2.provider.OAuth2Authentication oAuth2Authentication =
                tokenStore.readAuthentication(springOAuth2AccessToken);

        // Verify
        verify(tokenRepository, times(1)).readAuthentication(any(OAuth2AccessToken.class));
        assertEquals(clientId, oAuth2Authentication.getOAuth2Request().getClientId());
    }

    @Test
    public void shouldStoreAccessToken() {
        // prepare OAuth2AccessToken
        final String tokenId = "test-token";
        when(springOAuth2AccessToken.getValue()).thenReturn(tokenId);

        // prepare OAuth2Authentication
        final String clientId = "test-client";
        when(oAuth2Request.getClientId()).thenReturn(clientId);
        when(oAuth2Authentication.getOAuth2Request()).thenReturn(oAuth2Request);

        // Run
        tokenStore.storeAccessToken(springOAuth2AccessToken, RepositoryProviderUtils.convert(oAuth2Authentication));

        // Verify
        verify(tokenRepository, times(1)).storeAccessToken(any(OAuth2AccessToken.class), any(OAuth2Authentication.class));
    }

    @Test
    public void shouldReadAccessToken() {
        // prepare OAuth2AccessToken
        final String tokenId = "test-token";
        when(oAuth2AccessToken.getValue()).thenReturn(tokenId);
        when(tokenRepository.readAccessToken(tokenId)).thenReturn(Optional.ofNullable(oAuth2AccessToken));

        // Run
        final org.springframework.security.oauth2.common.OAuth2AccessToken oAuth2AccessToken =
                tokenStore.readAccessToken(tokenId);

        // Verify
        verify(tokenRepository, times(1)).readAccessToken(anyString());
        assertEquals(tokenId, oAuth2AccessToken.getValue());
    }

    @Test
    public void shouldRemoveAccessToken() {
        // prepare OAuth2AccessToken
        final String tokenId = "test-token";
        when(springOAuth2AccessToken.getValue()).thenReturn(tokenId);

        // Run
        tokenStore.removeAccessToken(springOAuth2AccessToken);

        // Verify
        verify(tokenRepository, times(1)).removeAccessToken(any(OAuth2AccessToken.class));
    }

    @Test
    public void shouldStoreRefreshToken() {
        // prepare OAuth2RefreshToken
        final String tokenId = "test-token";
        when(springOAuth2RefreshToken.getValue()).thenReturn(tokenId);

        // prepare OAuth2Authentication
        final String clientId = "test-client";
        when(oAuth2Request.getClientId()).thenReturn(clientId);
        when(oAuth2Authentication.getOAuth2Request()).thenReturn(oAuth2Request);

        // Run
        tokenStore.storeRefreshToken(springOAuth2RefreshToken, RepositoryProviderUtils.convert(oAuth2Authentication));

        // Verify
        verify(tokenRepository, times(1)).storeRefreshToken(any(OAuth2RefreshToken.class), any());
    }

    @Test
    public void shouldReadRefreshToken() {
        // prepare OAuth2RefreshToken
        final String tokenId = "test-token";
        when(oAuth2RefreshToken.getValue()).thenReturn(tokenId);
        when(tokenRepository.readRefreshToken(tokenId)).thenReturn(Optional.ofNullable(oAuth2RefreshToken));

        // Run
        final org.springframework.security.oauth2.common.OAuth2RefreshToken oAuth2RefreshToken =
                tokenStore.readRefreshToken(tokenId);

        // Verify
        verify(tokenRepository, times(1)).readRefreshToken(tokenId);
        assertEquals(tokenId, oAuth2RefreshToken.getValue());
    }

    @Test
    public void shouldReadAuthenticationForRefreshToken() {
        // prepare OAuth2RefreshToken
        final String tokenId = "test-token";
        when(springOAuth2RefreshToken.getValue()).thenReturn(tokenId);

        // prepare OAuth2Authentication
        final String clientId = "test-client";
        when(oAuth2Request.getClientId()).thenReturn(clientId);
        when(oAuth2Authentication.getOAuth2Request()).thenReturn(oAuth2Request);
        when(tokenRepository.readAuthenticationForRefreshToken(any(OAuth2RefreshToken.class))).thenReturn(Optional.ofNullable(oAuth2Authentication));

        // Run
        final org.springframework.security.oauth2.provider.OAuth2Authentication oAuth2Authentication =
                tokenStore.readAuthenticationForRefreshToken(springOAuth2RefreshToken);

        // Verify
        verify(tokenRepository, times(1)).readAuthenticationForRefreshToken(any(OAuth2RefreshToken.class));
        assertEquals(clientId, oAuth2Authentication.getOAuth2Request().getClientId());
    }

    @Test
    public void shouldRemoveRefreshToken() {
        // prepare OAuth2RefreshToken
        final String tokenId = "test-token";
        when(springOAuth2RefreshToken.getValue()).thenReturn(tokenId);

        // Run
        tokenStore.removeRefreshToken(springOAuth2RefreshToken);

        // Verify
        verify(tokenRepository, times(1)).removeRefreshToken(any(OAuth2RefreshToken.class));
    }

    @Test
    public void shouldRemoveAccessTokenUsingRefreshToken() {
        // prepare OAuth2RefreshToken
        final String tokenId = "test-token";
        when(springOAuth2RefreshToken.getValue()).thenReturn(tokenId);

        // Run
        tokenStore.removeAccessTokenUsingRefreshToken(springOAuth2RefreshToken);

        // Verify
        verify(tokenRepository, times(1)).removeAccessTokenUsingRefreshToken(any(OAuth2RefreshToken.class));
    }

    @Test
    public void shouldGetAccessToken() {
        // prepare OAuth2AccessToken
        final String tokenId = "test-token";
        when(oAuth2AccessToken.getValue()).thenReturn(tokenId);

        // prepare OAuth2Authentication
        final String clientId = "test-client";
        when(oAuth2Request.getClientId()).thenReturn(clientId);
        when(oAuth2Authentication.getOAuth2Request()).thenReturn(oAuth2Request);
        when(tokenRepository.getAccessToken(any())).thenReturn(Optional.ofNullable(oAuth2AccessToken));

        // Run
        final org.springframework.security.oauth2.common.OAuth2AccessToken oAuth2AccessToken =
                tokenStore.getAccessToken(RepositoryProviderUtils.convert(oAuth2Authentication));

        // Verify
        verify(tokenRepository, times(1)).getAccessToken(any());
        assertEquals(tokenId, oAuth2AccessToken.getValue());
    }

    @Test
    public void shouldFindTokensByClientIdAndUserName() {
        // prepare OAuth2AccessTokens
        final String tokenId = "test-token";
        final String clientId = "test-client";
        final String username = "test-username";
        when(oAuth2AccessToken.getValue()).thenReturn(tokenId);
        when(tokenRepository.findTokensByClientIdAndUserName(clientId, username)).thenReturn(Collections.singletonList(oAuth2AccessToken));

        // Run
        final List<org.springframework.security.oauth2.common.OAuth2AccessToken> oAuth2AccessTokens =
                (List<org.springframework.security.oauth2.common.OAuth2AccessToken>) tokenStore.findTokensByClientIdAndUserName(clientId, username);

        // Verify
        verify(tokenRepository, times(1)).findTokensByClientIdAndUserName(clientId, username);
        assertEquals(1, oAuth2AccessTokens.size());
        assertEquals(tokenId, oAuth2AccessTokens.get(0).getValue());
    }

    @Test
    public void shouldFindTokensByClientId() {
        // prepare OAuth2AccessTokens
        final String tokenId = "test-token";
        final String clientId = "test-client";
        when(oAuth2AccessToken.getValue()).thenReturn(tokenId);
        when(tokenRepository.findTokensByClientId(clientId)).thenReturn(Collections.singletonList(oAuth2AccessToken));

        // Run
        final List<org.springframework.security.oauth2.common.OAuth2AccessToken> oAuth2AccessTokens =
                (List<org.springframework.security.oauth2.common.OAuth2AccessToken>) tokenStore.findTokensByClientId(clientId);

        // Verify
        verify(tokenRepository, times(1)).findTokensByClientId(clientId);
        assertEquals(1, oAuth2AccessTokens.size());
        assertEquals(tokenId, oAuth2AccessTokens.get(0).getValue());
    }
}
