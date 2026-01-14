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

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.root.resources.handler.login.PostLoginActionCallbackParseHandler;
import io.gravitee.am.gateway.handler.root.resources.handler.login.PostLoginActionHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.PostLoginAction;
import io.gravitee.am.model.oidc.Client;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

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
            // Parse and verify the external service's response JWT
            SignedJWT signedJWT = SignedJWT.parse(responseToken);

            // Verify signature using configured public key
            JWSVerifier verifier = createVerifier(settings.getResponsePublicKey());
            if (!signedJWT.verify(verifier)) {
                logger.error("Invalid signature on response JWT");
                handleFailure(context, stateJwt, "invalid_response_signature", "Invalid response signature");
                return;
            }

            // Check expiration
            if (signedJWT.getJWTClaimsSet().getExpirationTime() != null) {
                if (Instant.now().isAfter(signedJWT.getJWTClaimsSet().getExpirationTime().toInstant())) {
                    logger.error("Response JWT has expired");
                    handleFailure(context, stateJwt, "response_expired", "Response has expired");
                    return;
                }
            }

            // Extract claims using configured claim names
            String statusClaim = settings.getSuccessClaim() != null ? settings.getSuccessClaim() : PostLoginAction.DEFAULT_SUCCESS_CLAIM;
            String successValue = settings.getSuccessValue() != null ? settings.getSuccessValue() : PostLoginAction.DEFAULT_SUCCESS_VALUE;
            String errorClaim = settings.getErrorClaim() != null ? settings.getErrorClaim() : PostLoginAction.DEFAULT_ERROR_CLAIM;
            String dataClaim = settings.getDataClaim() != null ? settings.getDataClaim() : PostLoginAction.DEFAULT_DATA_CLAIM;

            Object statusValue = signedJWT.getJWTClaimsSet().getClaim(statusClaim);
            Object errorValue = signedJWT.getJWTClaimsSet().getClaim(errorClaim);
            Object dataValue = signedJWT.getJWTClaimsSet().getClaim(dataClaim);

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

    private JWSVerifier createVerifier(String publicKeyPem) throws Exception {
        // Remove PEM headers and decode
        String publicKeyContent = publicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                .replace("-----END RSA PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(publicKeyContent);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);

        // Try RSA first, then EC
        try {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            RSAPublicKey rsaPublicKey = (RSAPublicKey) kf.generatePublic(spec);
            return new RSASSAVerifier(rsaPublicKey);
        } catch (Exception e) {
            // Try EC
            KeyFactory kf = KeyFactory.getInstance("EC");
            ECPublicKey ecPublicKey = (ECPublicKey) kf.generatePublic(spec);
            return new ECDSAVerifier(ecPublicKey);
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
}
