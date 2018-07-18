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
package io.gravitee.am.gateway.handler.vertx.handler.oidc;

import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.oidc.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.vertx.handler.oidc.endpoint.ProviderConfigurationEndpoint;
import io.gravitee.am.gateway.handler.vertx.handler.oidc.endpoint.UserInfoEndpoint;
import io.gravitee.am.gateway.handler.vertx.handler.oidc.handler.UserInfoRequestParseHandler;
import io.gravitee.am.gateway.service.UserService;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.CorsHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OIDCRouter {

    @Autowired
    private OpenIDDiscoveryService discoveryService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserService userService;

    @Autowired
    private Environment environment;

    public void route(Router router) {
        // OpenID Provider Configuration Information Endpoint
        Handler<RoutingContext> openIDProviderConfigurationEndpoint = new ProviderConfigurationEndpoint();
        ((ProviderConfigurationEndpoint) openIDProviderConfigurationEndpoint).setDiscoveryService(discoveryService);
        router
                .route(HttpMethod.GET, "/.well-known/openid-configuration")
                .handler(openIDProviderConfigurationEndpoint);

        // UserInfo Endpoint
        Handler<RoutingContext> userInfoEndpoint = new UserInfoEndpoint(userService);
        Handler<RoutingContext> userInfoRequestParseHandler = new UserInfoRequestParseHandler(tokenService);
        router.route("/userinfo").handler(CorsHandler.newInstance(corsHandler()));
        router
                .route(HttpMethod.GET, "/userinfo")
                .handler(userInfoRequestParseHandler)
                .handler(userInfoEndpoint);
        router
                .route(HttpMethod.POST, "/userinfo")
                .handler(userInfoRequestParseHandler)
                .handler(userInfoEndpoint);
    }

    private io.vertx.ext.web.handler.CorsHandler corsHandler() {
        return io.vertx.ext.web.handler.CorsHandler
                .create(environment.getProperty("http.cors.allow-origin", String.class, "*"))
                .allowedHeaders(getStringPropertiesAsList("http.cors.allow-headers", "Cache-Control, Pragma, Origin, Authorization, Content-Type, X-Requested-With, If-Match, x-xsrf-token"))
                .allowedMethods(getHttpMethodPropertiesAsList("http.cors.allow-methods", "GET, POST"))
                .maxAgeSeconds(environment.getProperty("http.cors.max-age", Integer.class, 86400));
    }

    private Set<String> getStringPropertiesAsList(final String propertyKey, final String defaultValue) {
        String property = environment.getProperty(propertyKey);
        if (property == null) {
            property = defaultValue;
        }
        return new HashSet<>(asList(property.replaceAll("\\s+","").split(",")));
    }

    private Set<HttpMethod> getHttpMethodPropertiesAsList(final String propertyKey, final String defaultValue) {
        String property = environment.getProperty(propertyKey);
        if (property == null) {
            property = defaultValue;
        }

        return asList(property.replaceAll("\\s+","").split(","))
                .stream()
                .map(method -> HttpMethod.valueOf(method))
                .collect(Collectors.toSet());
    }
}
