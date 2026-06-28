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
package io.gravitee.am.authdevice.notifier.api.model;

import io.gravitee.am.common.oidc.ClientAuthenticationMethod;

/**
 * Transient OIDC connection details resolved by the gateway from the referenced OpenID Connect IdP
 * and passed to the notifier per request. NEVER persisted or logged (the clientSecret is sensitive).
 * Vendor-neutral: all fields are generic OIDC/OAuth2 connection attributes.
 */
public record FederatedConnection(String clientId, String clientSecret, String scope, String wellKnownUri,
                                  String clientAuthMethod) {

    /** Convenience: a connection with no explicit client-auth method defaults to client_secret_post
     *  (matching the OIDC IdP config default). */
    public FederatedConnection(String clientId, String clientSecret, String scope, String wellKnownUri) {
        this(clientId, clientSecret, scope, wellKnownUri, ClientAuthenticationMethod.CLIENT_SECRET_POST);
    }

    @Override
    public String toString() {
        return "FederatedConnection[clientId=" + clientId
                + ", clientSecret=REDACTED, scope=" + scope + ", wellKnownUri=" + wellKnownUri
                + ", clientAuthMethod=" + clientAuthMethod + "]";
    }
}
