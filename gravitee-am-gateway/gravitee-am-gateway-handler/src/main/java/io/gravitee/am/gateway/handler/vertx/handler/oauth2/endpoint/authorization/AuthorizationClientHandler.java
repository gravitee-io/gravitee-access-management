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
package io.gravitee.am.gateway.handler.vertx.handler.oauth2.endpoint.authorization;

import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.exception.RedirectMismatchException;
import io.gravitee.am.gateway.handler.oauth2.exception.ServerErrorException;
import io.gravitee.am.gateway.handler.oauth2.exception.UnauthorizedClientException;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.model.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * The authorization server must ensure that the client used for the Authorization Request is registered and
 * should not redirect to login page if the client does not exist
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationClientHandler implements Handler<RoutingContext> {

    private static final String CLIENT_CONTEXT_KEY = "client";
    private ClientService clientService;

    public AuthorizationClientHandler(ClientService clientService) {
        this.clientService = clientService;
    }

    @Override
    public void handle(RoutingContext context) {
        final String clientId = context.request().getParam(OAuth2Constants.CLIENT_ID);
        final String redirectUri = context.request().getParam(OAuth2Constants.REDIRECT_URI);

        authenticate(clientId, resultHandler -> {
            if (resultHandler.failed()) {
                context.fail(resultHandler.cause());
                return;
            }
            // put client in the execution context
            Client client = resultHandler.result();
            context.put(CLIENT_CONTEXT_KEY, client);

            // additional check
            try {
                checkGrantTypes(client);
                checkRedirectUri(redirectUri, client);
                context.next();
            } catch (Exception ex) {
                context.fail(ex);
            }
        });
    }

    private void authenticate(String clientId, Handler<AsyncResult<Client>> authHandler) {
        clientService
                .findByClientId(clientId)
                .subscribe(
                        client -> authHandler.handle(Future.succeededFuture(client)),
                        error -> authHandler.handle(Future.failedFuture(new ServerErrorException("Server error: unable to find client with client_id " + clientId))),
                        () -> authHandler.handle(Future.failedFuture(new InvalidRequestException("No client found for client_id " + clientId)))
                );
    }

    private void checkGrantTypes(Client client) {
        // Authorization endpoint implies that the client should at least have authorization_code ou implicit grant types.
        List<String> authorizedGrantTypes = client.getAuthorizedGrantTypes();
        if (authorizedGrantTypes == null || authorizedGrantTypes.isEmpty()) {
            throw new UnauthorizedClientException("Client should at least have one authorized grand type");
        }
        if (!containsGrantType(authorizedGrantTypes)) {
            throw new UnauthorizedClientException("Client must at least have authorization_code or implicit grant type enable");
        }
    }

    private boolean containsGrantType(List<String> authorizedGrantTypes) {
        return authorizedGrantTypes.stream()
                .anyMatch(authorizedGrantType -> OAuth2Constants.AUTHORIZATION_CODE.equals(authorizedGrantType)
                        || OAuth2Constants.IMPLICIT.equals(authorizedGrantType));
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
