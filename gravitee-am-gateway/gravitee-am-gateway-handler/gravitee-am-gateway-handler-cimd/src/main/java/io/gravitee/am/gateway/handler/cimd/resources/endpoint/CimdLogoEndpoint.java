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
package io.gravitee.am.gateway.handler.cimd.resources.endpoint;

import io.gravitee.am.gateway.handler.common.client.cimd.CachedLogo;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdLogoCacheService;
import io.gravitee.am.gateway.handler.common.client.cimd.CimdMetadataDocumentManager;
import io.gravitee.am.common.oauth2.ClientIds;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.CIMDSettings;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.Optional;

/**
 * Serves a CIMD client logo from the in-memory cache, or fetches it synchronously on cache miss when
 * non-expired metadata for the client is present and {@code logo_uri} is set.
 *
 * <p>The caller supplies {@code clientId} (the canonical metadata URL) as a query parameter.
 * Remote {@code logo_uri} values are never served directly to the browser; they are fetched by the
 * gateway using the same trust rules as metadata logo pre-fetch.</p>
 */
public class CimdLogoEndpoint implements Handler<RoutingContext> {

    private static final String PARAM_CLIENT_ID = "clientId";

    private final Domain domain;
    private final CimdMetadataDocumentManager cimdMetadataDocumentManager;
    private final CimdLogoCacheService cimdLogoCacheService;

    public CimdLogoEndpoint(
            Domain domain,
            CimdMetadataDocumentManager cimdMetadataDocumentManager,
            CimdLogoCacheService cimdLogoCacheService) {
        this.domain = domain;
        this.cimdMetadataDocumentManager = cimdMetadataDocumentManager;
        this.cimdLogoCacheService = cimdLogoCacheService;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final String clientId = routingContext.request().getParam(PARAM_CLIENT_ID);
        if (clientId == null || clientId.isBlank()) {
            routingContext.response().setStatusCode(400).end();
            return;
        }

        final String canonicalId = ClientIds.canonicalize(clientId);

        final Optional<CachedLogo> cachedLogo = cimdMetadataDocumentManager.getLogoByClientId(canonicalId);
        if (cachedLogo.isPresent()) {
            endWithLogo(routingContext, cachedLogo.get());
            return;
        }

        cimdMetadataDocumentManager.resolve(canonicalId)
                .flatMap(doc -> {
                    if (doc.isEmpty()) {
                        return Single.just(Optional.<CachedLogo>empty());
                    }
                    final String logoUri = doc.get().getLogoUri();
                    if (logoUri == null || logoUri.isBlank()) {
                        return Single.just(Optional.<CachedLogo>empty());
                    }
                    final CIMDSettings settings = resolveCimdSettings();
                    if (settings == null || !settings.isEnabled()) {
                        return Single.just(Optional.<CachedLogo>empty());
                    }
                    return cimdLogoCacheService.fetchAndCacheNow(
                            canonicalId, logoUri, CimdMetadataDocumentManager.remainingTtlSeconds(doc.get()), settings);
                })
                .subscribe(
                        opt -> {
                            if (opt.isEmpty()) {
                                routingContext.response().setStatusCode(404).end();
                            } else {
                                endWithLogo(routingContext, opt.get());
                            }
                        },
                        routingContext::fail);
    }

    private static void endWithLogo(RoutingContext routingContext, CachedLogo cachedLogo) {
        routingContext
                .response()
                .putHeader("Content-Type", cachedLogo.contentType())
                .putHeader("Cache-Control", "max-age=" + cachedLogo.maxAgeSeconds())
                .setStatusCode(200)
                .end(Buffer.buffer(cachedLogo.bytes()));
    }

    private CIMDSettings resolveCimdSettings() {
        if (domain == null || domain.getOidc() == null) {
            return null;
        }
        return domain.getOidc().getCimdSettings();
    }

}
