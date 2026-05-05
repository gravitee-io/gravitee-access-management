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
package io.gravitee.am.gateway.handler.oauth2.resources.endpoint.authorization.consent;

import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.client.cimd.ClientIds;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.net.URI;
import java.util.Map;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Adds optional consent template attributes for URL-shaped (CIMD) OAuth clients.
 */
public final class CimdConsentPageAttributes {

    static final String CIMD_CLIENT_ID_HOSTNAME_KEY = "cimdClientIdHostname";
    static final String CIMD_DOMAIN_VERIFIED_KEY = "cimdDomainVerified";
    static final String CIMD_LOGO_URL_KEY = "cimdLogoUrl";

    private CimdConsentPageAttributes() {}

    /**
     * When the client is resolved via CIMD (URL-shaped {@code client_id} and CIMD enabled on the domain),
     * exposes hostname, verified-domain messaging context, and a same-origin logo URL when {@code logo_uri} is set.
     */
    public static void putIfApplicable(RoutingContext routingContext, Domain domain, Client client) {
        if (client == null
                || !ClientIds.isUrlShaped(client.getClientId())
                || domain == null
                || domain.getOidc() == null
                || domain.getOidc().getCimdSettings() == null
                || !domain.getOidc().getCimdSettings().isEnabled()) {
            return;
        }

        final String canonicalClientId = ClientIds.canonicalize(client.getClientId());
        final String host;
        try {
            URI uri = UriBuilder.fromHttpUrl(canonicalClientId).build();
            host = uri.getHost();
        } catch (Exception e) {
            return;
        }
        if (host == null || host.isBlank()) {
            return;
        }

        routingContext.put(CIMD_CLIENT_ID_HOSTNAME_KEY, host);
        routingContext.put(CIMD_DOMAIN_VERIFIED_KEY, Boolean.TRUE);

        if (client.getLogoUri() != null && !client.getLogoUri().isBlank()) {
            final String contextPath = routingContext.get(CONTEXT_PATH) != null ? routingContext.get(CONTEXT_PATH) : "";
            final String logoUrl = UriBuilderRequest.resolveProxyRequest(
                    routingContext.request(),
                    contextPath + "/cimd/logo",
                    Map.of("clientId", canonicalClientId),
                    true);
            routingContext.put(CIMD_LOGO_URL_KEY, logoUrl);
        }
    }
}
