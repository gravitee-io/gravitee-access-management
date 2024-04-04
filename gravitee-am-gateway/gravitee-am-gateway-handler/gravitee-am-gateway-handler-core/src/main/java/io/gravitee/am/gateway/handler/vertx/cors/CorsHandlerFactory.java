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
package io.gravitee.am.gateway.handler.vertx.cors;

import io.gravitee.am.model.CorsSettings;
import io.gravitee.am.model.Domain;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.ext.web.handler.CorsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CorsHandlerFactory implements FactoryBean<CorsHandler> {
    protected static final String DEFAULT_ORIGIN_KEY = "http.cors.allow-origin";
    protected static final String DEFAULT_ALLOWED_HEADERS_KEY = "http.cors.allow-headers";
    protected static final String DEFAULT_HTTP_METHODS_KEY = "http.cors.allow-methods";
    protected static final String DEFAULT_MAX_AGE_KEY = "http.cors.max-age";
    protected static final String DEFAULT_ALLOW_CREDENTIAL_KEY = "http.cors.allow-credentials";

    protected static final String DEFAULT_ORIGIN_VALUE = ".*";
    protected static final String DEFAULT_ALLOWED_HEADERS_VALUE = "Cache-Control, Pragma, Origin, Authorization, Content-Type, X-Requested-With, If-Match, x-xsrf-token";
    protected static final String DEFAULT_HTTP_METHODS_VALUE = "GET, POST, PUT, PATCH, DELETE";
    protected static final int DEFAULT_MAX_AGE_VALUE = 86400;
    protected static final boolean DEFAULT_ALLOW_CREDENTIAL_VALUE = false;

    private static final Logger logger = LoggerFactory.getLogger(CorsHandlerFactory.class);

    @Autowired
    private Environment environment;

    @Autowired
    private Domain domain;

    @Override
    public CorsHandler getObject() {
        try {
            final CorsSettings settings = getDomainCorsSettings();
            logger.info("Creating CORS Handler for Domain: {} with CORS settings: {} ", domain.getName(), settings);
            return createCorsHandler(settings);
        } catch (Exception ex) {
            logger.error("Could not create CORS handler with given settings", ex);
            final CorsSettings defaultSettings = createDefaultCorsSettings();
            logger.info("Creating CORS Handler for Domain: {} with default CORS settings: {} ", domain.getName(), defaultSettings);
            return createCorsHandler(defaultSettings);
        }
    }

    @Override
    public Class<?> getObjectType() {
        return CorsHandler.class;
    }

    private Set<String> getProperties(final String propertyKey, final String defaultValue) {
        final String property = Optional.ofNullable(environment.getProperty(propertyKey)).orElse(defaultValue);
        return new HashSet<>(asList(property.replaceAll("\\s+", "").split(",")));
    }

    private Set<HttpMethod> getHttpMethods(Set<String> httpMethods) {
        return httpMethods.stream().map(HttpMethod::valueOf).collect(Collectors.toSet());
    }

    private CorsSettings getDomainCorsSettings() {
        return ofNullable(domain.getCorsSettings())
                .filter(CorsSettings::isEnabled)
                .map(corsSettings -> {
                    if (corsSettings.getAllowedOrigins().isEmpty() || corsSettings.getAllowedOrigins().stream().anyMatch(origin -> origin.equals("*"))) {
                        corsSettings.setAllowedOrigins(Set.of(DEFAULT_ORIGIN_VALUE));
                    }
                    return corsSettings;
                })
                .orElseGet(this::createDefaultCorsSettings);
    }

    private CorsSettings createDefaultCorsSettings() {
        final CorsSettings settings = new CorsSettings();
        settings.setAllowedOrigins(Set.of(environment.getProperty(DEFAULT_ORIGIN_KEY, DEFAULT_ORIGIN_VALUE)));
        settings.setAllowedHeaders(getProperties(DEFAULT_ALLOWED_HEADERS_KEY, DEFAULT_ALLOWED_HEADERS_VALUE));
        settings.setAllowedMethods(getProperties(DEFAULT_HTTP_METHODS_KEY, DEFAULT_HTTP_METHODS_VALUE));
        settings.setMaxAge(environment.getProperty(DEFAULT_MAX_AGE_KEY, Integer.class, DEFAULT_MAX_AGE_VALUE));
        settings.setAllowCredentials(environment.getProperty(DEFAULT_ALLOW_CREDENTIAL_KEY, Boolean.class, DEFAULT_ALLOW_CREDENTIAL_VALUE));
        return settings;
    }

    private CorsHandler createCorsHandler(CorsSettings settings) {
        return CorsHandler
                .newInstance(io.vertx.ext.web.handler.CorsHandler
                        .create()
                        .allowedHeaders(settings.getAllowedHeaders())
                        .allowedMethods(getHttpMethods(settings.getAllowedMethods()))
                        .maxAgeSeconds(settings.getMaxAge())
                        .addRelativeOrigins(List.copyOf(settings.getAllowedOrigins())))
                .allowCredentials(settings.isAllowCredentials());
    }
}
