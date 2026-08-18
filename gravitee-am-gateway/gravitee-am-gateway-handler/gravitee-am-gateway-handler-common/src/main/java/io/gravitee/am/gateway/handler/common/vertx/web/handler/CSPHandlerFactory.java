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

import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.csp.CspHandlerImpl;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.csp.NoOpCspHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.webprotection.CspDirective;
import io.gravitee.am.model.webprotection.CspSettings;
import io.gravitee.am.model.webprotection.WebProtectionResolution;
import org.slf4j.Logger;
import io.gravitee.node.logging.NodeLoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.lang.Boolean.TRUE;
import static java.util.Objects.isNull;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CSPHandlerFactory implements FactoryBean<CSPHandler> {
    public final Logger logger = NodeLoggerFactory.getLogger(this.getClass());

    private static final String HTTP_CSP_ENABLED = "http.csp.enabled";
    private static final String HTTP_CSP_REPORT_ONLY = "http.csp.reportOnly";
    private static final String HTTP_CSP_DIRECTIVES = "http.csp.directives[%d]";
    private static final String HTTP_CSP_SCRIPT_INLINE_NONCE = "http.csp.script-inline-nonce";

    @Autowired
    private Environment environment;

    @Autowired
    private Domain domain;

    @Override
    public CSPHandler getObject() {
        return switch (WebProtectionDomainSettings.cspResolution(domain)) {
            case DISABLED -> new NoOpCspHandler();
            case ENABLED -> createFromDomainSettings(WebProtectionDomainSettings.csp(domain));
            case INHERIT -> createFromEnvironment();
        };
    }

    private CSPHandler createFromDomainSettings(CspSettings settings) {
        final List<String> directives = resolveDirectives(settings.getDirectives());
        if (directives == null || directives.isEmpty()) {
            logger.warn("Domain CSP is enabled but no directives are available for domain: {}, falling back to gravitee.yml",
                    domain.getName());
            return createFromEnvironment();
        }
        if (missingReportTarget(settings.isReportOnly(), directives)) {
            return new NoOpCspHandler();
        }
        return new CspHandlerImpl(settings.isReportOnly(), directives, settings.isScriptInlineNonce());
    }

    private CSPHandler createFromEnvironment() {
        logger.debug("Using gravitee.yml CSP configuration for domain: {}", domain.getName());
        var reportOnly = environment.getProperty(HTTP_CSP_REPORT_ONLY, Boolean.class);
        var directives = getEnvironmentDirectives();
        var scriptInlineNonce = environment.getProperty(HTTP_CSP_SCRIPT_INLINE_NONCE, Boolean.class, true);
        final boolean notEnabled = !environment.getProperty(HTTP_CSP_ENABLED, Boolean.class, true);
        if ((isNull(reportOnly) && isNull(directives) && !scriptInlineNonce) || notEnabled) {
            return new NoOpCspHandler();
        }
        if (missingReportTarget(TRUE.equals(reportOnly), directives)) {
            return new NoOpCspHandler();
        }
        return new CspHandlerImpl(reportOnly, directives, scriptInlineNonce);
    }

    /**
     * Vert.x fails the request with HTTP 500 when report-only is set and the policy carries neither
     * {@code report-uri} nor {@code report-to}, so send no CSP header at all to avoid breaking the login page.
     */
    private boolean missingReportTarget(boolean reportOnly, List<String> directives) {
        if (!reportOnly) {
            return false;
        }
        final boolean hasReportTarget = directives != null && directives.stream()
                .map(CspDirective::parse)
                .filter(Objects::nonNull)
                .anyMatch(directive -> directive.isReportTarget() && directive.hasValue());
        if (!hasReportTarget) {
            logger.error("CSP is report-only for domain: {} but no report-uri or report-to directive is configured. " +
                    "No Content-Security-Policy header will be sent. Add a report target or disable report-only.",
                    domain.getName());
            return true;
        }
        return false;
    }

    private List<String> resolveDirectives(List<String> domainDirectives) {
        if (domainDirectives != null && !domainDirectives.isEmpty()) {
            return domainDirectives;
        }
        final List<String> environmentDirectives = readEnvironmentDirectives();
        if (environmentDirectives != null && !environmentDirectives.isEmpty()) {
            return environmentDirectives;
        }
        return loadDefaultDirectives();
    }

    private List<String> getEnvironmentDirectives() {
        return resolveDirectives(null);
    }

    private List<String> readEnvironmentDirectives() {
        List<String> directives = null;
        for (int i = 0; true; i++) {
            var propertyKey = String.format(HTTP_CSP_DIRECTIVES, i);
            var value = environment.getProperty(propertyKey, String.class);
            if (isNull(value)) {
                break;
            }
            if (isNull(directives)) {
                directives = new ArrayList<>();
            }
            directives.add(value);
        }
        return directives;
    }

    private List<String> loadDefaultDirectives() {
        try (var reader = new BufferedReader(new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream("default-csp-directives.properties")))) {
            return reader.lines().collect(Collectors.toList());
        } catch (IOException e) {
            logger.warn("Unable to load default CSP directives from the classpath: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Class<?> getObjectType() {
        return CSPHandler.class;
    }
}
