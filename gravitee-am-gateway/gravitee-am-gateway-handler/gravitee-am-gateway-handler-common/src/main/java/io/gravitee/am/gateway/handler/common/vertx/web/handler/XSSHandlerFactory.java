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

import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.xss.NoXSSHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.xss.XSSHandlerImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.webprotection.XssProtectionSettings;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import static com.google.common.base.Strings.isNullOrEmpty;
import lombok.CustomLog;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@CustomLog
public class XSSHandlerFactory implements FactoryBean<XSSHandler> {

    private static final String HTTP_XSS_ENABLED = "http.xss.enabled";
    private static final String HTTP_XSS_ACTION = "http.xss.action";

    @Autowired
    private Environment environment;

    @Autowired
    private Domain domain;

    @Override
    public XSSHandler getObject() {
        return switch (WebProtectionDomainSettings.xssResolution(domain)) {
            case DISABLED -> new NoXSSHandler();
            case ENABLED -> createFromDomainSettings(WebProtectionDomainSettings.xss(domain));
            case INHERIT -> createFromEnvironment();
        };
    }

    private XSSHandler createFromDomainSettings(XssProtectionSettings settings) {
        if (isNullOrEmpty(settings.getAction())) {
            return new NoXSSHandler();
        }
        return new XSSHandlerImpl(settings.getAction().trim());
    }

    private XSSHandler createFromEnvironment() {
        log.debug("Using gravitee.yml X-XSS-Protection configuration for domain: {}", domain.getName());
        var action = environment.getProperty(HTTP_XSS_ACTION, String.class, "1; mode=block");
        final boolean notEnabled = !environment.getProperty(HTTP_XSS_ENABLED, Boolean.class, true);
        if (isNullOrEmpty(action) || notEnabled) {
            return new NoXSSHandler();
        }
        return new XSSHandlerImpl(action.trim());
    }

    @Override
    public Class<?> getObjectType() {
        return XSSHandler.class;
    }
}
