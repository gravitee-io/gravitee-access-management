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
package io.gravitee.am.management.handlers.management.api.spring.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.management.handlers.management.api.authentication.BearerTokenAuthenticator;
import io.gravitee.am.management.handlers.management.api.authentication.csrf.CookieCsrfSignedTokenRepository;
import io.gravitee.am.management.handlers.management.api.authentication.filter.BearerAuthenticationFilter;
import io.gravitee.am.management.handlers.management.api.authentication.web.Http401UnauthorizedEntryPoint;
import io.gravitee.am.management.handlers.management.api.spring.security.filter.ManagementSecurityConfiguration;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs requests through the real management Spring Security filter chain to check the CORS behaviour.
 */
class SecurityConfigurationCorsTest {

    private static final String ORIGIN = "https://console.example";
    private static final String OTHER_ORIGIN = "https://other.example";
    private static final String CSRF_HEADER = "X-Xsrf-Token";

    private GenericWebApplicationContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void defaultSettings_anyOriginIsAllowedWithCredentials() throws Exception {
        MockHttpServletResponse response = get(new MockEnvironment(), OTHER_ORIGIN);

        assertEquals(200, response.getStatus());
        assertEquals(OTHER_ORIGIN, response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
        assertTrue(response.getHeader("Access-Control-Expose-Headers").contains(CSRF_HEADER));
    }

    @Test
    void allowedOrigin_actualRequest() throws Exception {
        MockHttpServletResponse response = get(restricted(), ORIGIN);

        assertEquals(200, response.getStatus());
        assertEquals(ORIGIN, response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
        String exposed = response.getHeader("Access-Control-Expose-Headers");
        assertTrue(exposed.contains("ETag"));
        assertTrue(exposed.contains(CSRF_HEADER));
    }

    @Test
    void disallowedOrigin_actualRequestIsRejectedAndNothingIsExposed() throws Exception {
        MockHttpServletResponse response = get(restricted(), OTHER_ORIGIN);

        assertEquals(403, response.getStatus());
        assertNull(response.getHeader("Access-Control-Allow-Origin"));
        assertNull(response.getHeader("Access-Control-Allow-Credentials"));
        assertNull(response.getHeader("Access-Control-Expose-Headers"));
        assertNull(response.getHeader(CSRF_HEADER));
    }

    @Test
    void allowedOrigin_preflight() throws Exception {
        MockHttpServletResponse response = preflight(restricted(), ORIGIN);

        assertEquals(200, response.getStatus());
        assertEquals(ORIGIN, response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
    }

    @Test
    void disallowedOrigin_preflightIsRejected() throws Exception {
        MockHttpServletResponse response = preflight(restricted(), OTHER_ORIGIN);

        assertEquals(403, response.getStatus());
        assertNull(response.getHeader("Access-Control-Allow-Origin"));
        assertNull(response.getHeader("Access-Control-Allow-Credentials"));
    }

    @Test
    void credentialsDisabled_actualRequest() throws Exception {
        MockHttpServletResponse response = get(restricted().withProperty("http.cors.allow-credentials", "false"), ORIGIN);

        assertEquals(200, response.getStatus());
        assertEquals(ORIGIN, response.getHeader("Access-Control-Allow-Origin"));
        assertNull(response.getHeader("Access-Control-Allow-Credentials"));
    }

    @Test
    void credentialsDisabled_preflight() throws Exception {
        MockHttpServletResponse response = preflight(new MockEnvironment().withProperty("http.cors.allow-credentials", "false"), ORIGIN);

        assertEquals(200, response.getStatus());
        assertFalse("true".equals(response.getHeader("Access-Control-Allow-Credentials")));
    }

    private static MockEnvironment restricted() {
        return new MockEnvironment().withProperty("http.cors.allow-origin", ORIGIN);
    }

    private MockHttpServletResponse get(MockEnvironment environment, String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user");
        request.setServletPath("/user");
        request.addHeader("Origin", origin);
        request.addHeader("Authorization", "Bearer token");
        return execute(environment, request);
    }

    private MockHttpServletResponse preflight(MockEnvironment environment, String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/user");
        request.setServletPath("/user");
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "GET");
        return execute(environment, request);
    }

    private MockHttpServletResponse execute(MockEnvironment environment, MockHttpServletRequest request) throws Exception {
        context = new GenericWebApplicationContext(new MockServletContext());
        context.setEnvironment(environment);
        new AnnotatedBeanDefinitionReader(context).register(TestConfiguration.class);
        // registered as a singleton so that the @Autowired fields of the real repository are not processed
        context.getBeanFactory().registerSingleton("csrfTokenRepository", csrfTokenRepository());
        context.refresh();

        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain terminal = (req, res) -> ((HttpServletResponse) res).setStatus(200);
        context.getBean("springSecurityFilterChain", FilterChainProxy.class).doFilter(request, response, terminal);
        return response;
    }

    private static CookieCsrfSignedTokenRepository csrfTokenRepository() {
        CookieCsrfSignedTokenRepository repository = mock(CookieCsrfSignedTokenRepository.class);
        CsrfToken token = new DefaultCsrfToken(CSRF_HEADER, "_csrf", "token");
        when(repository.generateToken(any())).thenReturn(token);
        when(repository.loadDeferredToken(any(), any())).thenReturn(new DeferredCsrfToken() {
            @Override
            public CsrfToken get() {
                return token;
            }

            @Override
            public boolean isGenerated() {
                return false;
            }
        });
        return repository;
    }

    @Configuration
    @EnableWebSecurity
    @Import(ManagementSecurityConfiguration.class)
    static class TestConfiguration {

        @Bean
        public CorsConfigurationSource corsConfigurationSource(Environment environment) {
            SecurityConfiguration securityConfiguration = new SecurityConfiguration();
            ReflectionTestUtils.setField(securityConfiguration, "environment", environment);
            return securityConfiguration.corsConfigurationSource();
        }

        @Bean
        public Http401UnauthorizedEntryPoint http401UnauthorizedEntryPoint() {
            return new Http401UnauthorizedEntryPoint(new ObjectMapper());
        }

        @Bean
        public BearerTokenAuthenticator bearerTokenAuthenticator() {
            BearerTokenAuthenticator authenticator = mock(BearerTokenAuthenticator.class);
            UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated("admin", "n/a", java.util.List.of());
            when(authenticator.authenticate(any(), any())).thenReturn(authentication);
            return authenticator;
        }

        @Bean
        public BearerAuthenticationFilter bearerAuthenticationFilter() {
            return new BearerAuthenticationFilter(request -> true);
        }
    }
}
