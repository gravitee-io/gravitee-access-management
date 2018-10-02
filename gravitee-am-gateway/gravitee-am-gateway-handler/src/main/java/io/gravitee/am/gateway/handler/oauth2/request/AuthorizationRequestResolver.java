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
package io.gravitee.am.gateway.handler.oauth2.request;

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.exception.RedirectMismatchException;
import io.gravitee.am.gateway.handler.oauth2.exception.UnauthorizedClientException;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.reactivex.Single;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationRequestResolver extends AbstractRequestResolver<AuthorizationRequest> {

    public Single<AuthorizationRequest> resolve(AuthorizationRequest authorizationRequest, Client client, User endUser) {
        return resolveAuthorizedGrantTypes(authorizationRequest, client)
                .flatMap(request -> resolveAuthorizedScopes(request, client, endUser))
                .flatMap(request -> resolveRedirectUri(request, client));
    }

    /**
     * To request an access token, the client obtains authorization from the resource owner.
     * The authorization is expressed in the form of an authorization grant, which the client uses to request the access token.
     *
     * See <a href="https://tools.ietf.org/html/rfc6749#section-4">4.  Obtaining Authorization</a>
     *
     * Authorization endpoint implies that the client should at least have authorization_code ou implicit grant types.
     *
     * @param authorizationRequest the authorization request to resolve
     * @param client the client which trigger the request
     * @return the authorization request
     */
    private Single<AuthorizationRequest> resolveAuthorizedGrantTypes(AuthorizationRequest authorizationRequest, Client client) {
        List<String> authorizedGrantTypes = client.getAuthorizedGrantTypes();
        if (authorizedGrantTypes == null || authorizedGrantTypes.isEmpty()) {
            return Single.error(new UnauthorizedClientException("Client should at least have one authorized grand type"));
        }
        if (!containsGrantType(authorizedGrantTypes)) {
            return Single.error(new UnauthorizedClientException("Client must at least have authorization_code or implicit grant type enable"));
        }
        return Single.just(authorizationRequest);
    }

    /**
     * redirect_uri request parameter is OPTIONAL, but the RFC (rfc6749) assumes that
     * the request fails due to a missing, invalid, or mismatching redirection URI.
     * If no redirect_uri request parameter is supplied, the client must at least have one registered redirect uri
     *
     * See <a href="https://tools.ietf.org/html/rfc6749#section-4.1.2.1">4.1.2.1. Error Response</a>
     *
     * @param authorizationRequest the authorization request to resolve
     * @param client the client which trigger the request
     * @return the authorization request
     */
    public Single<AuthorizationRequest> resolveRedirectUri(AuthorizationRequest authorizationRequest, Client client) {
        String redirectUri = authorizationRequest.getRedirectUri();
        List<String> registeredClientRedirectUris = client.getRedirectUris();
        try {
            if (registeredClientRedirectUris != null && !registeredClientRedirectUris.isEmpty()) {
                redirectUri = obtainMatchingRedirect(registeredClientRedirectUris, redirectUri);
                authorizationRequest.setRedirectUri(redirectUri);
                return Single.just(authorizationRequest);
            } else if (redirectUri != null && !redirectUri.isEmpty()) {
                return Single.just(authorizationRequest);
            } else {
                return Single.error(new InvalidRequestException("A redirect_uri must be supplied."));
            }
        } catch (Exception e) {
            return Single.error(e);
        }
    }

    private boolean containsGrantType(List<String> authorizedGrantTypes) {
        return authorizedGrantTypes.stream()
                .anyMatch(authorizedGrantType -> OAuth2Constants.AUTHORIZATION_CODE.equals(authorizedGrantType)
                        || OAuth2Constants.IMPLICIT.equals(authorizedGrantType));
    }

    private String obtainMatchingRedirect(List<String> redirectUris, String requestedRedirect) {
        // no redirect_uri parameter supplied, return the first client registered redirect uri
        if (requestedRedirect == null) {
            return redirectUris.iterator().next();
        }

        for (String redirectUri : redirectUris) {
            if (redirectMatches(requestedRedirect, redirectUri)) {
                return requestedRedirect;
            }
        }
        throw new RedirectMismatchException("The redirect_uri MUST match the registered callback URL for this application");
    }

    private boolean redirectMatches(String requestedRedirect, String redirectUri) {
        try {
            URL req = new URL(requestedRedirect);
            URL reg = new URL(redirectUri);

            int requestedPort = req.getPort() != -1 ? req.getPort() : req.getDefaultPort();
            int registeredPort = reg.getPort() != -1 ? reg.getPort() : reg.getDefaultPort();

            boolean portsMatch = registeredPort == requestedPort;

            if (reg.getProtocol().equals(req.getProtocol()) &&
                    reg.getHost().equals(req.getHost()) &&
                    portsMatch) {
                return req.getPath().startsWith(reg.getPath());
            }
        } catch (MalformedURLException e) {

        }

        return requestedRedirect.equals(redirectUri);
    }
}
