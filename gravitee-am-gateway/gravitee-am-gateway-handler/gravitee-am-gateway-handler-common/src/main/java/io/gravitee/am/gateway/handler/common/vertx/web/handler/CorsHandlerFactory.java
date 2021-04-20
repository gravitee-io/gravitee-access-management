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
package io.gravitee.am.gateway.handler.common.vertx.web.handler;

import static java.util.Arrays.asList;

import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.ext.web.handler.CorsHandler;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CorsHandlerFactory implements FactoryBean<CorsHandler> {

    @Autowired
    private Environment environment;

    @Override
    public CorsHandler getObject() {
        return CorsHandler.newInstance(
            io.vertx.ext.web.handler.CorsHandler
                .create(environment.getProperty("http.cors.allow-origin", String.class, "*"))
                .allowedHeaders(
                    getStringPropertiesAsList(
                        "http.cors.allow-headers",
                        "Cache-Control, Pragma, Origin, Authorization, Content-Type, X-Requested-With, If-Match, x-xsrf-token"
                    )
                )
                .allowedMethods(getHttpMethodPropertiesAsList("http.cors.allow-methods", "GET, POST"))
                .maxAgeSeconds(environment.getProperty("http.cors.max-age", Integer.class, 86400))
        );
    }

    @Override
    public Class<?> getObjectType() {
        return CorsHandler.class;
    }

    private Set<String> getStringPropertiesAsList(final String propertyKey, final String defaultValue) {
        String property = environment.getProperty(propertyKey);
        if (property == null) {
            property = defaultValue;
        }
        return new HashSet<>(asList(property.replaceAll("\\s+", "").split(",")));
    }

    private Set<HttpMethod> getHttpMethodPropertiesAsList(final String propertyKey, final String defaultValue) {
        String property = environment.getProperty(propertyKey);
        if (property == null) {
            property = defaultValue;
        }

        return asList(property.replaceAll("\\s+", "").split(","))
            .stream()
            .map(method -> HttpMethod.valueOf(method))
            .collect(Collectors.toSet());
    }
}
