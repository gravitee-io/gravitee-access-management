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
package io.gravitee.am.gateway.handler.root.resources.endpoint.login;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.JWTProcessor;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.root.resources.handler.login.PostLoginActionCallbackParseHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.login.PostLoginActionHandler;
import io.gravitee.am.identityprovider.common.oauth2.jwt.jwks.ecdsa.ECDSAJWKSourceResolver;
import io.gravitee.am.identityprovider.common.oauth2.jwt.jwks.rsa.RSAJWKSourceResolver;
import io.gravitee.am.identityprovider.common.oauth2.jwt.processor.AbstractKeyProcessor;
import io.gravitee.am.identityprovider.common.oauth2.jwt.processor.RSAKeyProcessor;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.PostLoginAction;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Endpoint that processes the callback from the external post login action service.
 * Validates the external service's JWT response and decides whether to continue or block login.
 *
 * @author GraviteeSource Team
 */
public class PostLoginActionCallbackEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(PostLoginActionCallbackEndpoint.class);

    public static final String POST_LOGIN_ACTION_DATA_KEY = "postLoginActionData";
    public static final String POST_LOGIN_ACTION_COMPLETED_KEY = "postLoginActionCompleted";

    private final Domain domain;

    public PostLoginActionCallbackEndpoint(Domain domain) {
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext context) {
        final Client client = context.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final JWT stateJwt = context.get(PostLoginActionCallbackParseHandler.POST_LOGIN_ACTION_STATE_KEY);
        final String responseToken = context.get(PostLoginActionCallbackParseHandler.POST_LOGIN_ACTION_RESPONSE_TOKEN_KEY);
        final PostLoginAction settings = PostLoginAction.getInstance(domain, client);

        if (settings == null || !settings.isEnabled()) {
            logger.error("Post login action is not enabled but callback was received");
            context.fail(new InvalidRequestException("Post login action not enabled"));
            return;
        }

        try {
            JWTClaimsSet claimsSet;

            // Validate signature if public key is configured
            if (settings.getResponsePublicKey() != null && !settings.getResponsePublicKey().trim().isEmpty()) {
                try {
                    // Create JWT processor using the same mechanism as AbstractOpenIDConnectAuthenticationProvider
                    JWTProcessor jwtProcessor = createJWTProcessor(settings.getResponsePublicKey());

                    // Process and validate the JWT token (validates signature, exp, nbf)
                    claimsSet = jwtProcessor.process(responseToken, null);
                    logger.debug("Response token signature validated successfully");
                } catch (Exception e) {
                    logger.error("Failed to validate response token signature", e);
                    handleFailure(context, stateJwt, "signature_validation_error", "Failed to validate response token signature: " + e.getMessage());
                    return;
                }
            } else {
                logger.warn("No public key configured for response token validation - signature not verified");
                // Parse without validation if no public key configured
                com.nimbusds.jwt.SignedJWT signedJWT = com.nimbusds.jwt.SignedJWT.parse(responseToken);
                claimsSet = signedJWT.getJWTClaimsSet();
            }

            // Extract claims using configured claim names
            String statusClaim = settings.getSuccessClaim() != null ? settings.getSuccessClaim() : PostLoginAction.DEFAULT_SUCCESS_CLAIM;
            String successValue = settings.getSuccessValue() != null ? settings.getSuccessValue() : PostLoginAction.DEFAULT_SUCCESS_VALUE;
            String errorClaim = settings.getErrorClaim() != null ? settings.getErrorClaim() : PostLoginAction.DEFAULT_ERROR_CLAIM;
            String dataClaim = settings.getDataClaim() != null ? settings.getDataClaim() : PostLoginAction.DEFAULT_DATA_CLAIM;

            Object statusValue = claimsSet.getClaim(statusClaim);
            Object errorValue = claimsSet.getClaim(errorClaim);
            Object dataValue = claimsSet.getClaim(dataClaim);

            // Check if status indicates success
            if (successValue.equals(statusValue) || successValue.equals(String.valueOf(statusValue))) {
                handleSuccess(context, stateJwt, dataValue);
            } else {
                String errorMessage = errorValue != null ? String.valueOf(errorValue) : "External service denied the login";
                handleFailure(context, stateJwt, "post_login_action_denied", errorMessage);
            }

        } catch (Exception e) {
            logger.error("Failed to process response JWT", e);
            handleFailure(context, stateJwt, "invalid_response", "Failed to process response");
        }
    }

    private void handleSuccess(RoutingContext context, JWT stateJwt, Object data) {
        logger.debug("Post login action approved, continuing login flow");

        // Store data from external service in session if available
        if (data != null && context.session() != null) {
            context.session().put(POST_LOGIN_ACTION_DATA_KEY, String.valueOf(data));
        }

        // Mark post login action as completed
        if (context.session() != null) {
            context.session().put(POST_LOGIN_ACTION_COMPLETED_KEY, true);
        }

        // Redirect to original return URL to continue the flow
        String returnUrl = (String) stateJwt.get(PostLoginActionHandler.CLAIM_RETURN_URL);
        if (returnUrl == null || returnUrl.isEmpty()) {
            // Fallback to authorize endpoint
            returnUrl = UriBuilderRequest.resolveProxyRequest(
                    context.request(),
                    context.get(CONTEXT_PATH) + "/oauth/authorize"
            );
        }

        doRedirect(context, returnUrl);
    }

    private void handleFailure(RoutingContext context, JWT stateJwt, String error, String errorDescription) {
        logger.warn("Post login action denied: {} - {}", error, errorDescription);

        // Clear session since login is denied
        if (context.session() != null) {
            context.session().destroy();
        }
        context.clearUser();

        // Redirect to login page with error
        String loginUrl = UriBuilderRequest.resolveProxyRequest(
                context.request(),
                context.get(CONTEXT_PATH) + "/login"
        );

        // Extract original client_id from state to include in error redirect
        String clientId = (String) stateJwt.get(PostLoginActionHandler.CLAIM_CLIENT_ID);

        String errorUrl = UriBuilder.fromHttpUrl(loginUrl)
                .addParameter("error", error)
                .addParameter("error_description", errorDescription)
                .addParameter("client_id", clientId)
                .buildString();

        doRedirect(context, errorUrl);
    }

    private void doRedirect(RoutingContext context, String url) {
        context.response()
                .putHeader(HttpHeaders.LOCATION, url)
                .setStatusCode(302)
                .end();
    }

    /**
     * Creates a JWTProcessor to validate JWT signatures using the provided PEM public key or certificate.
     * Uses the same mechanism as AbstractOpenIDConnectAuthenticationProvider.
     * Supports both PEM certificates (BEGIN CERTIFICATE) and PEM public keys (BEGIN PUBLIC KEY).
     *
     * @param pemPublicKeyOrCert PEM-encoded public key or certificate string
     * @return JWTProcessor configured for signature validation
     * @throws Exception if key parsing or processor creation fails
     */
    private JWTProcessor createJWTProcessor(String pemPublicKeyOrCert) throws Exception {
        // Try RSA first (most common case)
        try {
            AbstractKeyProcessor keyProcessor = new RSAKeyProcessor<>();
            // RSAJWKSourceResolver can parse both PEM certificates and PEM public keys
            keyProcessor.setJwkSourceResolver(new RSAJWKSourceResolver<>(pemPublicKeyOrCert));
            // Default to RS256 for RSA keys
            return keyProcessor.create(SignatureAlgorithm.RS256);
        } catch (IllegalArgumentException | IllegalStateException rsaException) {
            // If RSA parsing fails, try EC
            try {
                AbstractKeyProcessor keyProcessor = new RSAKeyProcessor<>(); // RSAKeyProcessor also handles EC keys
                // ECDSAJWKSourceResolver can parse both PEM certificates and PEM public keys
                keyProcessor.setJwkSourceResolver(new ECDSAJWKSourceResolver<>(pemPublicKeyOrCert));
                // Default to ES256 for EC keys
                return keyProcessor.create(SignatureAlgorithm.ES256);
            } catch (Exception ecException) {
                logger.error("Failed to parse public key/certificate as RSA or EC", ecException);
                throw new Exception("Invalid certificate or public key. Must be PEM-encoded RSA or EC certificate/key.", ecException);
            }
        }
    }
}
