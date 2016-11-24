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

import io.gravitee.am.repository.oauth2.api.TokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.common.DefaultExpiringOAuth2RefreshToken;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2RefreshToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.OAuth2Request;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class RepositoryTokenStore implements TokenStore {

    @Autowired
    private TokenRepository tokenRepository;

    @Override
    public OAuth2Authentication readAuthentication(OAuth2AccessToken token) {
        Optional<io.gravitee.am.repository.oauth2.model.OAuth2Authentication> oAuth2Authentication
                = tokenRepository.readAuthentication(convert(token));

        if (oAuth2Authentication.isPresent()) {
            return convert(oAuth2Authentication.get());
        } else {
            return null;
        }
    }

    @Override
    public OAuth2Authentication readAuthentication(String token) {
        Optional<io.gravitee.am.repository.oauth2.model.OAuth2Authentication> oAuth2Authentication
                = tokenRepository.readAuthentication(token);

        if (oAuth2Authentication.isPresent()) {
            return convert(oAuth2Authentication.get());
        } else {
            return null;
        }
    }

    @Override
    public void storeAccessToken(OAuth2AccessToken token, OAuth2Authentication authentication) {
        tokenRepository.storeAccessToken(convert(token), convert(authentication));
    }

    @Override
    public OAuth2AccessToken readAccessToken(String tokenValue) {
        Optional<io.gravitee.am.repository.oauth2.model.OAuth2AccessToken> oAuth2AccessToken = tokenRepository.readAccessToken(tokenValue);

        if (oAuth2AccessToken.isPresent()) {
            return convert(oAuth2AccessToken.get());
        } else {
            return null;
        }
    }

    @Override
    public void removeAccessToken(OAuth2AccessToken token) {
        tokenRepository.removeAccessToken(convert(token));
    }

    @Override
    public void storeRefreshToken(OAuth2RefreshToken refreshToken, OAuth2Authentication authentication) {
        tokenRepository.storeRefreshToken(convert(refreshToken), convert(authentication));
    }

    @Override
    public OAuth2RefreshToken readRefreshToken(String tokenValue) {
        Optional<io.gravitee.am.repository.oauth2.model.OAuth2RefreshToken> oAuth2RefreshToken
                = tokenRepository.readRefreshToken(tokenValue);

        if(oAuth2RefreshToken.isPresent()) {
            return convert(oAuth2RefreshToken.get());
        } else {
            return null;
        }
    }

    @Override
    public OAuth2Authentication readAuthenticationForRefreshToken(OAuth2RefreshToken token) {
        Optional<io.gravitee.am.repository.oauth2.model.OAuth2Authentication> oAuth2Authentication
                = tokenRepository.readAuthenticationForRefreshToken(convert(token));

        if (oAuth2Authentication.isPresent()) {
            return convert(oAuth2Authentication.get());
        } else {
            return null;
        }
    }

    @Override
    public void removeRefreshToken(OAuth2RefreshToken token) {
        tokenRepository.removeRefreshToken(convert(token));
    }

    @Override
    public void removeAccessTokenUsingRefreshToken(OAuth2RefreshToken refreshToken) {
        tokenRepository.removeAccessTokenUsingRefreshToken(convert(refreshToken));
    }

    @Override
    public OAuth2AccessToken getAccessToken(OAuth2Authentication authentication) {
        Optional<io.gravitee.am.repository.oauth2.model.OAuth2AccessToken> oAuth2AccessToken = tokenRepository.getAccessToken(convert(authentication));

        if (oAuth2AccessToken.isPresent()) {
            return convert(oAuth2AccessToken.get());
        } else {
            return null;
        }
    }

    @Override
    public Collection<OAuth2AccessToken> findTokensByClientIdAndUserName(String clientId, String userName) {
        Collection<io.gravitee.am.repository.oauth2.model.OAuth2AccessToken> accessTokens =
                tokenRepository.findTokensByClientIdAndUserName(clientId, userName);

        if (accessTokens != null) {
            return accessTokens.stream().map(this::convert).collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public Collection<OAuth2AccessToken> findTokensByClientId(String clientId) {
        Collection<io.gravitee.am.repository.oauth2.model.OAuth2AccessToken> accessTokens =
                tokenRepository.findTokensByClientId(clientId);

        if (accessTokens != null) {
            return accessTokens.stream().map(this::convert).collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    private io.gravitee.am.repository.oauth2.model.OAuth2AccessToken convert(OAuth2AccessToken _oAuth2AccessToken) {
        io.gravitee.am.repository.oauth2.model.OAuth2AccessToken oAuth2AccessToken =
                new io.gravitee.am.repository.oauth2.model.OAuth2AccessToken(_oAuth2AccessToken.getValue());
        oAuth2AccessToken.setAdditionalInformation(_oAuth2AccessToken.getAdditionalInformation());
        oAuth2AccessToken.setExpiration(_oAuth2AccessToken.getExpiration());
        oAuth2AccessToken.setScope(_oAuth2AccessToken.getScope());
        oAuth2AccessToken.setTokenType(_oAuth2AccessToken.getTokenType());

        // refresh token
        OAuth2RefreshToken _oAuth2RefreshToken = _oAuth2AccessToken.getRefreshToken();
        if (_oAuth2RefreshToken != null) {
            io.gravitee.am.repository.oauth2.model.OAuth2RefreshToken oAuth2RefreshToken =
                    new io.gravitee.am.repository.oauth2.model.OAuth2RefreshToken(_oAuth2AccessToken.getRefreshToken().getValue());
            if (_oAuth2RefreshToken instanceof DefaultExpiringOAuth2RefreshToken) {
                Date expiration = ((DefaultExpiringOAuth2RefreshToken) _oAuth2RefreshToken).getExpiration();
                oAuth2RefreshToken.setExpiration(expiration);
            }
            oAuth2AccessToken.setRefreshToken(oAuth2RefreshToken);
        }

        return oAuth2AccessToken;
    }

    private OAuth2AccessToken convert(io.gravitee.am.repository.oauth2.model.OAuth2AccessToken _oAuth2AccessToken) {
        DefaultOAuth2AccessToken oAuth2AccessToken = new DefaultOAuth2AccessToken(_oAuth2AccessToken.getValue());
        oAuth2AccessToken.setAdditionalInformation(_oAuth2AccessToken.getAdditionalInformation());
        oAuth2AccessToken.setExpiration(_oAuth2AccessToken.getExpiration());
        oAuth2AccessToken.setScope(_oAuth2AccessToken.getScope());
        oAuth2AccessToken.setTokenType(_oAuth2AccessToken.getTokenType());

        // refresh token
        io.gravitee.am.repository.oauth2.model.OAuth2RefreshToken _oAuth2RefreshToken = _oAuth2AccessToken.getRefreshToken();
        if (_oAuth2RefreshToken != null) {
            DefaultExpiringOAuth2RefreshToken oAuth2RefreshToken =
                    new DefaultExpiringOAuth2RefreshToken(_oAuth2AccessToken.getRefreshToken().getValue(), _oAuth2AccessToken.getRefreshToken().getExpiration());
            oAuth2AccessToken.setRefreshToken(oAuth2RefreshToken);
        }

        return oAuth2AccessToken;
    }

    private io.gravitee.am.repository.oauth2.model.OAuth2RefreshToken convert(OAuth2RefreshToken _oAuth2RefreshToken) {
        io.gravitee.am.repository.oauth2.model.OAuth2RefreshToken oAuth2RefreshToken = new io.gravitee.am.repository.oauth2.model.OAuth2RefreshToken(_oAuth2RefreshToken.getValue());
        if (_oAuth2RefreshToken instanceof DefaultExpiringOAuth2RefreshToken) {
            oAuth2RefreshToken.setExpiration(((DefaultExpiringOAuth2RefreshToken) _oAuth2RefreshToken).getExpiration());
        }
        return oAuth2RefreshToken;
    }

    private OAuth2RefreshToken convert(io.gravitee.am.repository.oauth2.model.OAuth2RefreshToken _oAuth2RefreshToken) {
        return new DefaultExpiringOAuth2RefreshToken(_oAuth2RefreshToken.getValue(), _oAuth2RefreshToken.getExpiration());
    }

    private io.gravitee.am.repository.oauth2.model.OAuth2Authentication convert(OAuth2Authentication _oAuth2Authentication) {
        // oauth2 request
        OAuth2Request _oAuth2Request = _oAuth2Authentication.getOAuth2Request();
        io.gravitee.am.repository.oauth2.model.request.OAuth2Request oAuth2Request =
                new io.gravitee.am.repository.oauth2.model.request.OAuth2Request();
        oAuth2Request.setRequestParameters(_oAuth2Request.getRequestParameters());
        oAuth2Request.setClientId(_oAuth2Request.getClientId());
        oAuth2Request.setAuthorities(convert(_oAuth2Request.getAuthorities()));
        oAuth2Request.setApproved(_oAuth2Request.isApproved());
        oAuth2Request.setScope(_oAuth2Request.getScope());
        oAuth2Request.setResourceIds(_oAuth2Request.getResourceIds());
        oAuth2Request.setRedirectUri(_oAuth2Request.getRedirectUri());
        oAuth2Request.setResponseTypes(_oAuth2Request.getResponseTypes());
        oAuth2Request.setExtensions(_oAuth2Request.getExtensions());

        // user authentication
        io.gravitee.am.repository.oauth2.model.authentication.Authentication userAuthentication = null;
        Authentication _userAuthentication = _oAuth2Authentication.getUserAuthentication();
        if (_userAuthentication != null) {
            Object principal = _userAuthentication.getPrincipal();
            Object credentials = _userAuthentication.getCredentials();
            Collection<? extends io.gravitee.am.repository.oauth2.model.authority.GrantedAuthority> authorities = convert(_userAuthentication.getAuthorities());
            userAuthentication =
                    new io.gravitee.am.repository.oauth2.model.authentication.UsernamePasswordAuthenticationToken(principal, credentials, authorities);
        }

        io.gravitee.am.repository.oauth2.model.OAuth2Authentication oAuth2Authentication =
                new io.gravitee.am.repository.oauth2.model.OAuth2Authentication(oAuth2Request, userAuthentication);

        return oAuth2Authentication;
    }

    private OAuth2Authentication convert(io.gravitee.am.repository.oauth2.model.OAuth2Authentication _oAuth2Authentication) {
        // oauth2 request
        io.gravitee.am.repository.oauth2.model.request.OAuth2Request _oAuth2Request = _oAuth2Authentication.getOAuth2Request();

        Map<String, String> requestParameters = _oAuth2Request.getRequestParameters();
        String clientId = _oAuth2Request.getClientId();
        Collection<? extends GrantedAuthority> authorities = map(_oAuth2Request.getAuthorities());
        boolean approved = _oAuth2Request.isApproved();
        Set<String> scope = _oAuth2Request.getScope();
        Set<String> resourceIds = _oAuth2Request.getResourceIds();
        String redirectUri = _oAuth2Request.getRedirectUri();
        Set<String> responseTypes = _oAuth2Request.getResponseTypes();
        Map<String, Serializable> extensions = _oAuth2Request.getExtensions();

        OAuth2Request oAuth2Request =
                new OAuth2Request(requestParameters, clientId, authorities, approved, scope, resourceIds, redirectUri, responseTypes, extensions);

        // user authentication
        Authentication userAuthentication = null;
        io.gravitee.am.repository.oauth2.model.authentication.Authentication _userAuthentication = _oAuth2Authentication.getUserAuthentication();

        if (_userAuthentication != null && _userAuthentication instanceof io.gravitee.am.repository.oauth2.model.authentication.UsernamePasswordAuthenticationToken) {
            Object principal = _userAuthentication.getPrincipal();
            Object credentials = _userAuthentication.getCredentials();
            Collection<? extends GrantedAuthority> userAuthorities = map(_userAuthentication.getAuthorities());
            userAuthentication =
                    new UsernamePasswordAuthenticationToken(principal, credentials, userAuthorities);
        }

        OAuth2Authentication oAuth2Authentication =
                new OAuth2Authentication(oAuth2Request, userAuthentication);

        return oAuth2Authentication;
    }

    private Collection<? extends GrantedAuthority> map(Collection<? extends io.gravitee.am.repository.oauth2.model.authority.GrantedAuthority> _authorities) {
        if (_authorities == null) {
            return null;
        }
        return _authorities.stream().map(a -> new SimpleGrantedAuthority(a.getAuthority())).collect(Collectors.toList());
    }

    private Collection<? extends io.gravitee.am.repository.oauth2.model.authority.GrantedAuthority> convert(Collection<? extends GrantedAuthority> _authorities) {
        if (_authorities == null) {
            return null;
        }
        return _authorities.stream().map(a -> new io.gravitee.am.repository.oauth2.model.authority.SimpleGrantedAuthority(a.getAuthority())).collect(Collectors.toList());
    }
}
