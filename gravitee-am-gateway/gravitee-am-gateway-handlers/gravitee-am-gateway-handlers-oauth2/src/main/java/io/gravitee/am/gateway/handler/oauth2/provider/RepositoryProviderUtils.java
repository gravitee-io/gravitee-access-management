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
package io.gravitee.am.gateway.handler.oauth2.provider;

import io.gravitee.am.repository.oauth2.model.authority.GrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.OAuth2Request;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
 public class RepositoryProviderUtils {

    public static io.gravitee.am.repository.oauth2.model.OAuth2Authentication convert(OAuth2Authentication _oAuth2Authentication) {
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
            Collection<? extends GrantedAuthority> authorities = convert(_userAuthentication.getAuthorities());
            userAuthentication =
                    new io.gravitee.am.repository.oauth2.model.authentication.UsernamePasswordAuthenticationToken(
                            _userAuthentication.getName(), principal, credentials, authorities);
        }

        io.gravitee.am.repository.oauth2.model.OAuth2Authentication oAuth2Authentication =
                new io.gravitee.am.repository.oauth2.model.OAuth2Authentication(oAuth2Request, userAuthentication);

        return oAuth2Authentication;
    }

    public static OAuth2Authentication convert(io.gravitee.am.repository.oauth2.model.OAuth2Authentication _oAuth2Authentication) {
        // oauth2 request
        io.gravitee.am.repository.oauth2.model.request.OAuth2Request _oAuth2Request = _oAuth2Authentication.getOAuth2Request();

        Map<String, String> requestParameters = _oAuth2Request.getRequestParameters();
        String clientId = _oAuth2Request.getClientId();
        Collection<? extends org.springframework.security.core.GrantedAuthority> authorities = map(_oAuth2Request.getAuthorities());
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
            Collection<? extends org.springframework.security.core.GrantedAuthority> userAuthorities = map(_userAuthentication.getAuthorities());
            userAuthentication =
                    new UsernamePasswordAuthenticationToken(principal, credentials, userAuthorities);
        }

        OAuth2Authentication oAuth2Authentication =
                new OAuth2Authentication(oAuth2Request, userAuthentication);

        return oAuth2Authentication;
    }

    private static Collection<? extends org.springframework.security.core.GrantedAuthority> map(Collection<? extends io.gravitee.am.repository.oauth2.model.authority.GrantedAuthority> _authorities) {
        if (_authorities == null) {
            return null;
        }
        return _authorities.stream().map(a -> new SimpleGrantedAuthority(a.getAuthority())).collect(Collectors.toList());
    }

    private static Collection<? extends io.gravitee.am.repository.oauth2.model.authority.GrantedAuthority> convert(Collection<? extends org.springframework.security.core.GrantedAuthority> _authorities) {
        if (_authorities == null) {
            return null;
        }
        return _authorities.stream().map(a -> new io.gravitee.am.repository.oauth2.model.authority.SimpleGrantedAuthority(a.getAuthority())).collect(Collectors.toList());
    }
}
