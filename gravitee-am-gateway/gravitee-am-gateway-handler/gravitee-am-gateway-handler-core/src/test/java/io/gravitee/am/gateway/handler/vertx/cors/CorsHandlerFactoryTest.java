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
import io.vertx.ext.web.handler.impl.CorsHandlerImpl;
import io.vertx.rxjava3.ext.web.handler.CorsHandler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.regex.Pattern;

import static io.gravitee.am.gateway.handler.vertx.cors.CorsHandlerFactory.DEFAULT_ALLOWED_HEADERS_VALUE;
import static io.gravitee.am.gateway.handler.vertx.cors.CorsHandlerFactory.DEFAULT_ALLOW_CREDENTIAL_KEY;
import static io.gravitee.am.gateway.handler.vertx.cors.CorsHandlerFactory.DEFAULT_ALLOW_CREDENTIAL_VALUE;
import static io.gravitee.am.gateway.handler.vertx.cors.CorsHandlerFactory.DEFAULT_HTTP_METHODS_VALUE;
import static io.gravitee.am.gateway.handler.vertx.cors.CorsHandlerFactory.DEFAULT_MAX_AGE_KEY;
import static io.gravitee.am.gateway.handler.vertx.cors.CorsHandlerFactory.DEFAULT_MAX_AGE_VALUE;
import static io.gravitee.am.gateway.handler.vertx.cors.CorsHandlerFactory.DEFAULT_ORIGIN_KEY;
import static io.gravitee.am.gateway.handler.vertx.cors.CorsHandlerFactory.DEFAULT_ORIGIN_VALUE;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class CorsHandlerFactoryTest {
    @InjectMocks
    private CorsHandlerFactory factory = new CorsHandlerFactory();

    @Mock
    private Domain domain;

    @Mock
    private Environment environment;

    @Before
    public void setUp() throws Exception {
        when(environment.getProperty(DEFAULT_ORIGIN_KEY, DEFAULT_ORIGIN_VALUE)).thenReturn(DEFAULT_ORIGIN_VALUE);
        when(environment.getProperty(DEFAULT_MAX_AGE_KEY, Integer.class, DEFAULT_MAX_AGE_VALUE)).thenReturn(DEFAULT_MAX_AGE_VALUE);
        when(environment.getProperty(DEFAULT_ALLOW_CREDENTIAL_KEY, Boolean.class, DEFAULT_ALLOW_CREDENTIAL_VALUE)).thenReturn(DEFAULT_ALLOW_CREDENTIAL_VALUE);
    }

    @Test
    public void shouldCreate_default_cors_settings() {
        when(domain.getCorsSettings()).thenReturn(null);

        final CorsHandler handler = factory.getObject();
        final CorsHandlerImpl handlerObject = (CorsHandlerImpl) handler.getDelegate();

        assertTrue(validateCorsProperties(handlerObject, createDefaultCorsSettings()));
    }

    @Test
    public void shouldCreate_default_cors_settings_when_settings_disabled() {
        final CorsSettings settings = new CorsSettings();
        settings.setEnabled(false);
        when(domain.getCorsSettings()).thenReturn(settings);

        final CorsHandler handler = factory.getObject();
        final CorsHandlerImpl handlerObject = (CorsHandlerImpl) handler.getDelegate();

        assertTrue(validateCorsProperties(handlerObject, createDefaultCorsSettings()));
    }

    @Test
    public void shouldCreate_custom_cors_settings() {
        final CorsSettings corsSettings = new CorsSettings();
        corsSettings.setAllowedOrigins(Set.of("http.foo.com", "https.bar.com/*"));
        corsSettings.setAllowedHeaders(Set.of("Authorization", "custom-header"));
        corsSettings.setAllowedMethods(Set.of("DELETE", "PUT"));
        corsSettings.setMaxAge(50);
        corsSettings.setEnabled(true);

        when(domain.getCorsSettings()).thenReturn(corsSettings);

        final CorsHandler handler = factory.getObject();
        final CorsHandlerImpl handlerObject = (CorsHandlerImpl) handler.getDelegate();

        assertTrue(validateCorsProperties(handlerObject, corsSettings));
    }

    @Test
    public void shouldCreate_default_cors_settings_exception() {
        final CorsSettings invalidSettings = new CorsSettings();
        when(domain.getCorsSettings()).thenReturn(invalidSettings);

        final CorsHandler handler = factory.getObject();
        final CorsHandlerImpl handlerObject = (CorsHandlerImpl) handler.getDelegate();

        assertTrue(validateCorsProperties(handlerObject, createDefaultCorsSettings()));
    }

    private Boolean validateCorsProperties(CorsHandlerImpl corsHandler, CorsSettings corsSettings) {
        final String maxAgeSeconds = (String) ReflectionTestUtils.getField(corsHandler, "maxAgeSeconds");
        assertEquals(Integer.valueOf(corsSettings.getMaxAge()), Integer.valueOf(maxAgeSeconds));

        final Boolean allowCredentials = (Boolean) ReflectionTestUtils.getField(corsHandler, "allowCredentials");
        assertEquals(corsSettings.isAllowCredentials(), allowCredentials);

        final Set<String> allowedMethods = (Set<String>) ReflectionTestUtils.getField(corsHandler, "allowedMethods");
        assertEquals(corsSettings.getAllowedMethods(), allowedMethods);

        final Set<String> allowedHeaders = (Set<String>) ReflectionTestUtils.getField(corsHandler, "allowedHeaders");
        assertEquals(corsSettings.getAllowedHeaders(), allowedHeaders);

        final Set<Pattern> relativeOrigins = (Set<Pattern>) ReflectionTestUtils.getField(corsHandler, "regexOrigins");
        // Note that regarding:
        // io/vertx/vertx-web/4.4.4/vertx-web-4.4.4-sources.jar!/io/vertx/ext/web/handler/impl/CorsHandlerImpl.java:104
        // default relative origin ".*" is like null (allow everything).
        if (relativeOrigins != null) {
            assertEquals(corsSettings.getAllowedOrigins().toString(), relativeOrigins.toString());
        }

        return true;
    }

    private CorsSettings createDefaultCorsSettings() {
        final CorsSettings settings = new CorsSettings();

        settings.setAllowedOrigins(strToSet(DEFAULT_ORIGIN_VALUE));
        settings.setAllowedHeaders(strToSet(DEFAULT_ALLOWED_HEADERS_VALUE));
        settings.setAllowedMethods(strToSet(DEFAULT_HTTP_METHODS_VALUE));
        settings.setMaxAge(environment.getProperty(DEFAULT_MAX_AGE_KEY, Integer.class, DEFAULT_MAX_AGE_VALUE));
        settings.setAllowCredentials(environment.getProperty(DEFAULT_ALLOW_CREDENTIAL_KEY, Boolean.class, DEFAULT_ALLOW_CREDENTIAL_VALUE));

        return settings;
    }

    private Set<String> strToSet(String property) {
        return stream(property.replaceAll("\\s+", "").split(",")).collect(toSet());
    }
}
