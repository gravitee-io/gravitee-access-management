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

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceSyncService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.handler.ClientAuthHandler;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.provider.ClientAuthProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.ProtectedResourceService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.ClientAuthAuditBuilder;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.common.web.UriBuilder.decodeURIComponent;
import static io.gravitee.am.gateway.handler.oauth2.resources.auth.provider.CertificateUtils.extractPeerCertificate;
import static io.gravitee.am.gateway.handler.oauth2.resources.auth.provider.CertificateUtils.getThumbprint;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientAuthHandlerImpl implements Handler<RoutingContext> {
    private static final String INVALID_CLIENT_MESSAGE = "Invalid client: missing or unsupported authentication method";
    private static final String CERTIFICATE_ERROR = "Missing or invalid peer certificate";
    private final ClientSyncService clientSyncService;
    private final List<ClientAuthProvider> clientAuthProviders;
    private final Domain domain;
    private final String certificateHeader;
    private final AuditService auditService;
    private final ProtectedResourceSyncService protectedResourceSyncService;

    public ClientAuthHandlerImpl(ClientSyncService clientSyncService, List<ClientAuthProvider> clientAuthProviders, Domain domain, String certificateHeader, AuditService auditService, ProtectedResourceSyncService protectedResourceSyncService) {
        this.clientSyncService = clientSyncService;
        this.clientAuthProviders = clientAuthProviders;
        this.domain = domain;
        this.certificateHeader = certificateHeader;
        this.auditService = auditService;
        this.protectedResourceSyncService = protectedResourceSyncService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final HttpServerRequest request = routingContext.request();

        // fetch client
        resolveClient(request, handler -> {
            if (handler.failed()) {
                Throwable cause = handler.cause();
                auditService.report(AuditBuilder.builder(ClientAuthAuditBuilder.class)
                        .reference(Reference.domain(domain.getId()))
                        .ipAddress(routingContext)
                        .userAgent(routingContext)
                        .throwable(cause));
                routingContext.fail(cause);
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
                    auditService.report(AuditBuilder.builder(ClientAuthAuditBuilder.class)
                            .reference(Reference.domain(domain.getId()))// client may be null, we have to provide the domainId
                            .clientActor(client)
                            .ipAddress(routingContext)
                            .userAgent(routingContext)
                            .throwable(throwable));
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
                        var error = new InvalidClientException(CERTIFICATE_ERROR);
                        auditService.report(AuditBuilder.builder(ClientAuthAuditBuilder.class).clientActor(client)
                                .ipAddress(routingContext)
                                .userAgent(routingContext)
                                .throwable(error));
                        routingContext.fail(error);
                        return;
                    }
                } catch (SSLPeerUnverifiedException | NoSuchAlgorithmException | CertificateException ce ) {
                    if (authenticatedClient.isTlsClientCertificateBoundAccessTokens() || domain.usePlainFapiProfile()) {
                        var error = new InvalidClientException(CERTIFICATE_ERROR);
                        auditService.report(AuditBuilder.builder(ClientAuthAuditBuilder.class).clientActor(client)
                                .ipAddress(routingContext)
                                .userAgent(routingContext)
                                .throwable(error));
                        routingContext.fail(error);
                        return;
                    }
                }

                auditService.report(AuditBuilder.builder(ClientAuthAuditBuilder.class).clientActor(client)
                        .ipAddress(routingContext)
                        .userAgent(routingContext)
                );
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
                    .orElseThrow(() -> new InvalidClientException(INVALID_CLIENT_MESSAGE))
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
            // get client - first try regular client, then fallback to protected resource
            clientSyncService
                    .findByClientId(decodeURIComponent(clientId))
                    .switchIfEmpty(protectedResourceSyncService.findByClientId(decodeURIComponent(clientId)))
                    .subscribe(
                            client -> handler.handle(Future.succeededFuture(client)),
                            error -> handler.handle(Future.failedFuture(error)),
                            () -> handler.handle(Future.failedFuture(new InvalidClientException(ClientAuthHandler.GENERIC_ERROR_MESSAGE)))
                    );

        });
    }

    private void parseClientId(HttpServerRequest request, Handler<AsyncResult<String>> handler) {
        final String authorization = request.headers().get(HttpHeaders.AUTHORIZATION);
        final String clientAssertion = request.getParam(Parameters.CLIENT_ASSERTION);
        final String clientAssertionType = request.getParam(Parameters.CLIENT_ASSERTION_TYPE);
        String clientId;
        try {
            if (authorization != null) {
                // authorization header has been found check the value
                int idx = authorization.indexOf(' ');
                if (idx <= 0) {
                    handler.handle(Future.failedFuture(new InvalidClientException(INVALID_CLIENT_MESSAGE)));
                    return;
                }
                if (!"Basic".equalsIgnoreCase(authorization.substring(0, idx))) {
                    handler.handle(Future.failedFuture(new InvalidClientException(INVALID_CLIENT_MESSAGE)));
                    return;
                }
                String clientAuthentication = new String(Base64.getDecoder().decode(authorization.substring(idx + 1)));
                int colonIdx = clientAuthentication.indexOf(":");
                if (colonIdx != -1) {
                    clientId = clientAuthentication.substring(0, colonIdx);
                } else {
                    clientId = clientAuthentication;
                }
            } else if(clientAssertion != null && clientAssertionType != null) {
                JWT jwt = JWTParser.parse(clientAssertion);
                clientId = jwt.getJWTClaimsSet().getSubject();
            } else {
                // if no authorization header found, check client_id via the query parameter
                clientId = request.getParam(Parameters.CLIENT_ID);
                if (clientId == null) {
                    handler.handle(Future.failedFuture(new InvalidClientException(INVALID_CLIENT_MESSAGE)));
                    return;
                }
            }
            handler.handle(Future.succeededFuture(clientId));

        } catch (ParseException | RuntimeException e) {
            handler.handle(Future.failedFuture(new InvalidClientException(INVALID_CLIENT_MESSAGE)));
        }
    }
}
