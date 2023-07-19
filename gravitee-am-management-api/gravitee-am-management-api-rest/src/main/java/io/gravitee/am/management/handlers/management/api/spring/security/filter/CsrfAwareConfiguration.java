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
package io.gravitee.am.management.handlers.management.api.spring.security.filter;

import io.gravitee.am.management.handlers.management.api.authentication.csrf.CookieCsrfSignedTokenRepository;
import io.gravitee.am.management.handlers.management.api.authentication.csrf.CsrfRequestMatcher;
import io.gravitee.am.management.handlers.management.api.authentication.filter.CsrfIncludeFilter;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.csrf.CsrfFilter;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class CsrfAwareConfiguration {

    protected static final String DEFAULT_COOKIE_JWT_NAME = "Auth-Graviteeio-AM";
    protected static final String PROP_JWT_COOKIE_NAME = "jwt.cookie-name";
    protected static final String PROP_HTTP_CSRF_ENABLED = "http.csrf.enabled";

    protected final Environment environment;

    protected CsrfAwareConfiguration(Environment environment) {
        this.environment = environment;
    }

    protected HttpSecurity applyCsrf(HttpSecurity security, CookieCsrfSignedTokenRepository csrfTokenRepository) throws Exception {
        var csrfEnabled = environment.getProperty(PROP_HTTP_CSRF_ENABLED, Boolean.class, true);

        return csrfEnabled ? security.csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .requireCsrfProtectionMatcher(getRequireCsrfProtectionMatcher()))
                .addFilterAfter(new CsrfIncludeFilter(), CsrfFilter.class)
                : security.csrf(csrf -> csrf.disable());
    }

    protected CsrfRequestMatcher getRequireCsrfProtectionMatcher() {
        return new CsrfRequestMatcher(environment.getProperty(PROP_JWT_COOKIE_NAME, DEFAULT_COOKIE_JWT_NAME));
    }
}
