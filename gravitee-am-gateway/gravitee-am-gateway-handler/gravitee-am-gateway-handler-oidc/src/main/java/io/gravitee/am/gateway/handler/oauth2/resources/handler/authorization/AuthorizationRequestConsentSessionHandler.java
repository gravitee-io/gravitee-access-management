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

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.vertx.core.Handler;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.Session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.gravitee.am.common.utils.ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.root.resources.endpoint.ParamUtils.getOAuthParameter;

/**
 * Discards the consent decisions in the session that were made for another authorization request. The request
 * replayed after the consent screens has the same parameters, so it keeps them.
 */
public class AuthorizationRequestConsentSessionHandler implements Handler<RoutingContext> {

    private static final List<String> REQUEST_PARAMETERS = List.of(
            Parameters.CLIENT_ID,
            Parameters.REDIRECT_URI,
            Parameters.CODE_CHALLENGE,
            Parameters.CODE_CHALLENGE_METHOD,
            Parameters.RESPONSE_TYPE,
            Parameters.SCOPE,
            Parameters.STATE,
            io.gravitee.am.common.oidc.Parameters.NONCE);

    private static final List<String> CONSENT_KEYS = List.of(
            ConstantKeys.USER_CONSENT_COMPLETED_KEY,
            ConstantKeys.USER_CONSENT_APPROVED_KEY,
            ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PENDING_KEY,
            ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_PRESENTED_KEY,
            ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_COMPLETED_KEY,
            ConstantKeys.AUTHORIZATION_DETAILS_CONSENT_APPROVED_KEY);

    @Override
    public void handle(RoutingContext routingContext) {
        final Session session = routingContext.session();
        final String requestDigest = digestOf(routingContext);
        if (!requestDigest.equals(session.get(ConstantKeys.CONSENT_REQUEST_DIGEST_KEY))) {
            CONSENT_KEYS.forEach(session::remove);
            session.put(ConstantKeys.CONSENT_REQUEST_DIGEST_KEY, requestDigest);
            final AuthorizationRequest authorizationRequest = routingContext.get(AUTHORIZATION_REQUEST_CONTEXT_KEY);
            // the request was built from the discarded USER_CONSENT_APPROVED_KEY
            authorizationRequest.setApproved(false);
        }
        routingContext.next();
    }

    private static String digestOf(RoutingContext routingContext) {
        final String parameters = REQUEST_PARAMETERS.stream()
                .map(name -> name + "=" + Objects.toString(getOAuthParameter(routingContext, name), ""))
                .collect(Collectors.joining("\n"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(parameters.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
