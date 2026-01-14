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
package io.gravitee.am.gateway.handler.root.resources.handler.login;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.PostLoginAction;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Handler that redirects to an external service after successful login.
 * The external service can approve or deny the login via a signed JWT response.
 *
 * @author GraviteeSource Team
 */
public class PostLoginActionHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(PostLoginActionHandler.class);

    public static final String CLAIM_TRANSACTION_ID = "tid";
    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_CLIENT_ID = "cid";
    public static final String CLAIM_QUERY_PARAMS = "q";
    public static final String CLAIM_RETURN_URL = "return_url";

    private final Domain domain;
    private final JWTService jwtService;
    private final CertificateManager certificateManager;

    public PostLoginActionHandler(Domain domain, JWTService jwtService, CertificateManager certificateManager) {
        this.domain = domain;
        this.jwtService = jwtService;
        this.certificateManager = certificateManager;
    }

    @Override
    public void handle(RoutingContext context) {
        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final PostLoginAction settings = PostLoginAction.getInstance(domain, client);

        // If not enabled, continue to next handler
        if (settings == null || !settings.isEnabled()) {
            context.next();
            return;
        }

        // Check if URL is configured
        if (settings.getUrl() == null || settings.getUrl().isEmpty()) {
            logger.warn("Post login action is enabled but URL is not configured");
            context.next();
            return;
        }

        // Check if public key is configured for response validation
        if (settings.getResponsePublicKey() == null || settings.getResponsePublicKey().isEmpty()) {
            logger.warn("Post login action is enabled but response public key is not configured");
            context.next();
            return;
        }

        final User user = context.get(ConstantKeys.USER_CONTEXT_KEY);
        if (user == null) {
            logger.warn("No user found in context for post login action");
            context.next();
            return;
        }

        // Build state JWT
        final JWT stateJwt = buildStateJwt(context, client, user, settings);

        // Encode JWT and redirect
        jwtService.encode(stateJwt, certificateManager.defaultCertificateProvider())
                .subscribe(
                        encodedState -> {
                            String redirectUrl = buildExternalServiceUrl(context, settings, encodedState);
                            logger.debug("Redirecting to post login action URL: {}", settings.getUrl());
                            doRedirect(context, redirectUrl);
                        },
                        error -> {
                            logger.error("Failed to encode state JWT for post login action", error);
                            context.fail(error);
                        }
                );
    }

    private JWT buildStateJwt(RoutingContext context, Client client, User user, PostLoginAction settings) {
        final JWT stateJwt = new JWT();
        stateJwt.setJti(SecureRandomString.generate());

        // Transaction ID from session if available
        String transactionId = context.session() != null ? context.session().get(ConstantKeys.TRANSACTION_ID_KEY) : null;
        if (transactionId == null) {
            transactionId = SecureRandomString.generate();
        }
        stateJwt.put(CLAIM_TRANSACTION_ID, transactionId);

        // User and client info
        stateJwt.put(CLAIM_USER_ID, user.getId());
        stateJwt.put(CLAIM_CLIENT_ID, client.getClientId());

        // Original query parameters
        String query = context.request().query();
        if (query != null) {
            stateJwt.put(CLAIM_QUERY_PARAMS, query);
        }

        // Return URL - where to continue after post login action callback
        String returnUrl = buildReturnUrl(context);
        stateJwt.put(CLAIM_RETURN_URL, returnUrl);

        // Timestamps
        long now = Instant.now().getEpochSecond();
        stateJwt.setIat(now);
        stateJwt.setExp(now + (settings.getTimeout() / 1000));

        return stateJwt;
    }

    private String buildReturnUrl(RoutingContext context) {
        // The return URL is where the flow should continue after the post login action
        // This is typically the authorize endpoint or the original request
        String returnUrl = context.session() != null ? context.session().get(ConstantKeys.RETURN_URL_KEY) : null;
        if (returnUrl == null) {
            // Default to authorize endpoint with original params
            returnUrl = UriBuilderRequest.resolveProxyRequest(context.request(), context.get(CONTEXT_PATH) + "/oauth/authorize");
            String query = context.request().query();
            if (query != null && !query.isEmpty()) {
                returnUrl = returnUrl + "?" + query;
            }
        }
        return returnUrl;
    }

    private String buildExternalServiceUrl(RoutingContext context, PostLoginAction settings, String encodedState) {
        // Build callback URL for AM
        String callbackUrl = UriBuilderRequest.resolveProxyRequest(
                context.request(),
                context.get(CONTEXT_PATH) + "/login/postaction/callback"
        );

        // Build external service URL with state and callback_url
        return UriBuilder.fromHttpUrl(settings.getUrl())
                .addParameter("state", encodedState)
                .addParameter("callback_url", callbackUrl)
                .buildString();
    }

    private void doRedirect(RoutingContext context, String url) {
        context.response()
                .putHeader(HttpHeaders.LOCATION, url)
                .setStatusCode(302)
                .end();
    }
}
