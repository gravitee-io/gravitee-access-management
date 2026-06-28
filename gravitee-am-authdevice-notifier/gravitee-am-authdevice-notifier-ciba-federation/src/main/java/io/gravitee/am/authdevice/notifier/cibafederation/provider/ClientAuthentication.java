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
package io.gravitee.am.authdevice.notifier.cibafederation.provider;

import io.gravitee.am.common.oidc.ClientAuthenticationMethod;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpRequest;

import java.net.URLEncoder;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Client-authentication wire-format for the CIBA-federation notifier's outgoing requests.
 * Supports client_secret_post and client_secret_basic only — matching AM's own outbound OIDC
 * client (AbstractOpenIDConnectAuthenticationProvider). Any other method is rejected upstream;
 * this helper treats an unknown method as post so a validated caller has one code path.
 */
final class ClientAuthentication {

    // OAuth 2.0 / OIDC token-endpoint client-authentication method identifiers — sourced from
    // core's ClientAuthenticationMethod so the notifier and the gateway agree on one definition.
    static final String CLIENT_SECRET_POST = ClientAuthenticationMethod.CLIENT_SECRET_POST;
    static final String CLIENT_SECRET_BASIC = ClientAuthenticationMethod.CLIENT_SECRET_BASIC;

    private ClientAuthentication() {}

    static String normalize(String method) {
        return (method == null || method.isBlank()) ? CLIENT_SECRET_POST : method;
    }

    static boolean isSupported(String method) {
        String m = normalize(method);
        return CLIENT_SECRET_POST.equals(m) || CLIENT_SECRET_BASIC.equals(m);
    }

    static String unsupportedMessage(String method) {
        return "CIBA federation supports client_secret_post and client_secret_basic; '" + method + "' is not supported";
    }

    /** RFC 6749 §2.3.1: form-url-encode client_id and client_secret (space as %20) before base64. */
    static String basicHeader(String clientId, String clientSecret) {
        String creds = enc(clientId) + ":" + enc(clientSecret);
        return "Basic " + Base64.getEncoder().encodeToString(creds.getBytes(UTF_8));
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, UTF_8).replace("+", "%20");
    }

    /**
     * Applies the configured client authentication to an outgoing token/backchannel request.
     * The caller must have already set {@code client_id} in {@code form}. For basic, sets the
     * Authorization header; for post (and any non-basic value), adds {@code client_secret} to the body.
     * The {@code client_secret} is sent by exactly one mechanism.
     */
    static HttpRequest<Buffer> applied(HttpRequest<Buffer> req, MultiMap form, String method,
                                       String clientId, String clientSecret) {
        if (CLIENT_SECRET_BASIC.equals(normalize(method))) {
            req.putHeader("Authorization", basicHeader(clientId, clientSecret));
        } else {
            form.set("client_secret", clientSecret);
        }
        return req;
    }
}
