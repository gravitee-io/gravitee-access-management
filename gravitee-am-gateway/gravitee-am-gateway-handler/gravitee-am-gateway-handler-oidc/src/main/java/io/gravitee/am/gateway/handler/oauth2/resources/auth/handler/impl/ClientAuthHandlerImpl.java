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
package io.gravitee.am.gateway.handler.oauth2.resources.auth.handler.impl;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.handler.ClientAuthHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.provider.ClientAuthProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.oauth2.resources.auth.provider.CertificateUtils.extractPeerCertificate;
import static io.gravitee.am.gateway.handler.oauth2.resources.auth.provider.CertificateUtils.getThumbprint;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientAuthHandlerImpl implements Handler<RoutingContext> {
    private final ClientSyncService clientSyncService;
    private final List<ClientAuthProvider> clientAuthProviders;
    private final Domain domain;
    private final String certificateHeader;

    public ClientAuthHandlerImpl(ClientSyncService clientSyncService, List<ClientAuthProvider> clientAuthProviders, Domain domain, String certificateHeader) {
        this.clientSyncService = clientSyncService;
        this.clientAuthProviders = clientAuthProviders;
        this.domain = domain;
        this.certificateHeader = certificateHeader;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final HttpServerRequest request = routingContext.request();

        // fetch client
        resolveClient(request, handler -> {
            if (handler.failed()) {
                routingContext.fail(handler.cause());
                return;
            }
            // authenticate client
            Client client = handler.result();
            authenticateClient(client, routingContext, authHandler -> {
                if (authHandler.failed()) {
                    Throwable throwable = authHandler.cause();
                    if (throwable instanceof InvalidClientException) {
                        String authenticateHeader = ((InvalidClientException) throwable).getAuthenticateHeader();
                        if (authenticateHeader != null) {
                            routingContext.response().putHeader("WWW-Authenticate", authenticateHeader);
                        }
                    }
                    routingContext.fail(authHandler.cause());
                    return;
                }

                // the client might has been upgraded after authentication process, get the new value
                Client authenticatedClient = authHandler.result();

                // get SSL certificate thumbprint to bind with access token
                try {
                    Optional<X509Certificate> peerCertificate = extractPeerCertificate(routingContext, this.certificateHeader);
                    if (peerCertificate.isPresent()) {
                        routingContext.put(ConstantKeys.PEER_CERTIFICATE_THUMBPRINT, getThumbprint(peerCertificate.get(), "SHA-256"));
                    } else if (authenticatedClient.isTlsClientCertificateBoundAccessTokens() || domain.usePlainFapiProfile()) {
                        routingContext.fail(new InvalidClientException("Missing or invalid peer certificate"));
                        return;
                    }
                } catch (SSLPeerUnverifiedException | NoSuchAlgorithmException | CertificateException ce ) {
                    if (authenticatedClient.isTlsClientCertificateBoundAccessTokens() || domain.usePlainFapiProfile()) {
                        routingContext.fail(new InvalidClientException("Missing or invalid peer certificate"));
                        return;
                    }
                }

                // put client in context and continue
                routingContext.put(CLIENT_CONTEXT_KEY, authenticatedClient);
                routingContext.next();
            });

        });
    }

    private void authenticateClient(Client client, RoutingContext context, Handler<AsyncResult<Client>> handler) {
        try {
            clientAuthProviders
                    .stream()
                    .filter(clientAuthProvider -> clientAuthProvider.canHandle(client, context))
                    .findFirst()
                    .orElseThrow(() -> new InvalidClientException("Invalid client: missing or unsupported authentication method"))
                    .handle(client, context, handler);
        } catch (Exception ex) {
            handler.handle(Future.failedFuture(ex));
        }
    }

    private void resolveClient(HttpServerRequest request, Handler<AsyncResult<Client>> handler) {
        // client_id can be retrieved via query parameter or Basic Authorization
        parseClientId(request, h -> {
            if (h.failed()) {
                handler.handle(Future.failedFuture(h.cause()));
                return;
            }
            final String clientId = h.result();
            // client_id can be null if client authentication method is private_jwt
            if (clientId == null) {
                handler.handle(Future.succeededFuture());
                return;
            }
            // get client
            clientSyncService
                    .findByClientId(clientId)
                    .subscribe(
                            client -> handler.handle(Future.succeededFuture(client)),
                            error -> handler.handle(Future.failedFuture(error)),
                            () -> handler.handle(Future.failedFuture(new InvalidClientException(ClientAuthHandler.GENERIC_ERROR_MESSAGE)))
                    );

        });
    }

    private void parseClientId(HttpServerRequest request, Handler<AsyncResult<String>> handler) {
        final String authorization = request.headers().get(HttpHeaders.AUTHORIZATION);
        String clientId = null;
        try {
            if (authorization != null) {
                // authorization header has been found check the value
                int idx = authorization.indexOf(' ');
                if (idx <= 0) {
                    handler.handle(Future.failedFuture(new InvalidClientException("Invalid client: missing or unsupported authentication method")));
                    return;
                }
                if (!"Basic".equalsIgnoreCase(authorization.substring(0, idx))) {
                    handler.handle(Future.failedFuture(new InvalidClientException("Invalid client: missing or unsupported authentication method")));
                    return;
                }
                String clientAuthentication = new String(Base64.getDecoder().decode(authorization.substring(idx + 1)));
                int colonIdx = clientAuthentication.indexOf(":");
                if (colonIdx != -1) {
                    clientId = clientAuthentication.substring(0, colonIdx);
                } else {
                    clientId = clientAuthentication;
                }
                handler.handle(Future.succeededFuture(clientId));
            } else {
                // if no authorization header found, check client_id via the query parameter
                clientId = request.getParam(Parameters.CLIENT_ID);
                // client_id can be null if client authentication method is private_jwt
                if (clientId == null && request.getParam(Parameters.CLIENT_ASSERTION_TYPE) == null && request.getParam(Parameters.CLIENT_ASSERTION) == null) {
                    handler.handle(Future.failedFuture(new InvalidClientException("Invalid client: missing or unsupported authentication method")));
                    return;
                }
                handler.handle(Future.succeededFuture(clientId));
            }
        } catch (RuntimeException e) {
            handler.handle(Future.failedFuture(new InvalidClientException("Invalid client: missing or unsupported authentication method")));
        }
    }
}
