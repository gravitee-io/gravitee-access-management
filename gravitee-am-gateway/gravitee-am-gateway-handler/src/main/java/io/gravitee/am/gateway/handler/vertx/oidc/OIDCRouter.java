package io.gravitee.am.gateway.handler.vertx.oidc;

import io.gravitee.am.gateway.handler.oidc.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.vertx.oidc.endpoint.ProviderConfigurationEndpoint;
import io.gravitee.am.gateway.handler.vertx.oidc.endpoint.UserInfoEndpoint;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OIDCRouter {

    @Autowired
    private OpenIDDiscoveryService discoveryService;

    public void route(Router router) {
        Handler<RoutingContext> openIDProviderConfigurationEndpoint = new ProviderConfigurationEndpoint();
        ((ProviderConfigurationEndpoint) openIDProviderConfigurationEndpoint).setDiscoveryService(discoveryService);
        router
                .route(HttpMethod.GET, "/.well-known/openid-configuration")
                .handler(openIDProviderConfigurationEndpoint);

        Handler<RoutingContext> userInfoEndpoint = new UserInfoEndpoint();
        //((ProviderConfigurationEndpoint) userInfoEndpoint).setDiscoveryService(discoveryService);
        router
                .route(HttpMethod.GET, "/userinfo")
                .handler(userInfoEndpoint);
    }
}
