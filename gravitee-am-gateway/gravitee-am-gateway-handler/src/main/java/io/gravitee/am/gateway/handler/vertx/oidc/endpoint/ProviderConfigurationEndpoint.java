package io.gravitee.am.gateway.handler.vertx.oidc.endpoint;

import io.gravitee.am.gateway.handler.oidc.discovery.OpenIDDiscoveryService;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ProviderConfigurationEndpoint implements Handler<RoutingContext> {

    private OpenIDDiscoveryService discoveryService;

    @Override
    public void handle(RoutingContext context) {
        context.response()
                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                .end(Json.encodePrettily(discoveryService.getConfiguration()));
    }

    public OpenIDDiscoveryService getDiscoveryService() {
        return discoveryService;
    }

    public void setDiscoveryService(OpenIDDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }
}
