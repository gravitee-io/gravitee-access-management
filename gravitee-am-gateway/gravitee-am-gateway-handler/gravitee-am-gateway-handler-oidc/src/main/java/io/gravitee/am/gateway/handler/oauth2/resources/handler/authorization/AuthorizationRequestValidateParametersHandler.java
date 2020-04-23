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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.oauth2.GrantType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.oauth2.exception.RedirectMismatchException;
import io.gravitee.am.gateway.handler.oauth2.exception.UnauthorizedClientException;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;


/**
 * The authorization server must ensure that the client is using grant flow, redirect_uri which are defined
 * from its configuration.
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationRequestValidateParametersHandler implements Handler<RoutingContext> {

    private static final String CLIENT_CONTEXT_KEY = "client";
    private Domain domain;

    public AuthorizationRequestValidateParametersHandler(Domain domain) {
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext context) {
        final String redirectUri = context.request().getParam(Parameters.REDIRECT_URI);
        final String responseType = context.request().getParam(Parameters.RESPONSE_TYPE);

        Client client = context.get(CLIENT_CONTEXT_KEY);

        // Additional check
        try {
            checkGrantTypes(client);
            checkResponseType(responseType, client);
            checkRedirectUri(redirectUri, client);

            context.next();
        } catch (Exception ex) {
            context.fail(ex);
        }
    }

    private void checkGrantTypes(Client client) {
        // Authorization endpoint implies that the client should at least have authorization_code ou implicit grant types.
        List<String> authorizedGrantTypes = client.getAuthorizedGrantTypes();
        if (authorizedGrantTypes == null || authorizedGrantTypes.isEmpty()) {
            throw new UnauthorizedClientException("Client should at least have one authorized grant type");
        }
        if (!containsGrantType(authorizedGrantTypes)) {
            throw new UnauthorizedClientException("Client must at least have authorization_code or implicit grant type enable");
        }
    }

    private boolean containsGrantType(List<String> authorizedGrantTypes) {
        return authorizedGrantTypes.stream()
                .anyMatch(authorizedGrantType -> GrantType.AUTHORIZATION_CODE.equals(authorizedGrantType)
                        || GrantType.IMPLICIT.equals(authorizedGrantType));
    }

    private void checkResponseType(String responseType, Client client) {
        // Authorization endpoint implies that the client should have response_type
        if (client.getResponseTypes() == null) {
            throw new UnauthorizedClientException("Client should have response_type.");
        }
        if(!Arrays.stream(responseType.split("\\s")).allMatch(type -> client.getResponseTypes().contains(type))) {
            throw new UnauthorizedClientException("Client should have all requested response_type");
        }
    }

    private void checkRedirectUri(String requestedRedirectUri, Client client) {
        final List<String> registeredClientRedirectUris = client.getRedirectUris();
        final boolean hasRegisteredClientRedirectUris = registeredClientRedirectUris != null && !registeredClientRedirectUris.isEmpty();
        final boolean hasRequestedRedirectUri = requestedRedirectUri != null && !requestedRedirectUri.isEmpty();

        // if no requested redirect_uri and no registered client redirect_uris
        // throw invalid request exception
        if (!hasRegisteredClientRedirectUris && !hasRequestedRedirectUri) {
            throw new InvalidRequestException("A redirect_uri must be supplied");
        }

        // if no requested redirect_uri and more than one registered client redirect_uris
        // throw invalid request exception
        if (!hasRequestedRedirectUri && (registeredClientRedirectUris != null && registeredClientRedirectUris.size() > 1)) {
            throw new InvalidRequestException("Unable to find suitable redirect_uri, a redirect_uri must be supplied");
        }

        // if requested redirect_uri doesn't match registered client redirect_uris
        // throw redirect mismatch exception
        if (hasRequestedRedirectUri && hasRegisteredClientRedirectUris) {
            checkMatchingRedirectUri(requestedRedirectUri, registeredClientRedirectUris);
        }
    }

    private void checkMatchingRedirectUri(String requestedRedirect, List<String> registeredClientRedirectUris) {
        if (registeredClientRedirectUris
                .stream()
                .noneMatch(registeredClientUri -> redirectMatches(requestedRedirect, registeredClientUri))) {
            throw new RedirectMismatchException("The redirect_uri MUST match the registered callback URL for this application");
        }
    }

    private boolean redirectMatches(String requestedRedirect, String registeredClientUri) {
        // if redirect_uri strict matching mode is enabled, do string matching
        if (this.domain.isRedirectUriStrictMatching()) {
            return requestedRedirect.equals(registeredClientUri);
        }

        // nominal case
        try {
            URL req = new URL(requestedRedirect);
            URL reg = new URL(registeredClientUri);

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

        return requestedRedirect.equals(registeredClientUri);
    }
}
