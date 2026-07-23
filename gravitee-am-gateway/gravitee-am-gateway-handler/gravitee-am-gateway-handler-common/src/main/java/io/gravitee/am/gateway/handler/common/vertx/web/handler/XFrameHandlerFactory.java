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

import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.xframe.NoXFrameHandler;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.xframe.XFrameHandlerImpl;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.webprotection.XFrameSettings;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.util.Locale;

import static com.google.common.base.Strings.isNullOrEmpty;
import lombok.CustomLog;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@CustomLog
public class XFrameHandlerFactory implements FactoryBean<XFrameHandler> {

    private static final String HTTP_XFRAME_ACTION = "http.xframe.action";

    @Autowired
    private Environment environment;

    @Autowired
    private Domain domain;

    @Override
    public XFrameHandler getObject() {
        return switch (WebProtectionDomainSettings.xframeResolution(domain)) {
            case DISABLED -> new NoXFrameHandler();
            case ENABLED -> createFromDomainSettings(WebProtectionDomainSettings.xframe(domain));
            case INHERIT -> createFromEnvironment();
        };
    }

    private XFrameHandler createFromDomainSettings(XFrameSettings settings) {
        if (isNullOrEmpty(settings.getAction())) {
            return new NoXFrameHandler();
        }
        return new XFrameHandlerImpl(settings.getAction().trim().toUpperCase(Locale.ROOT));
    }

    private XFrameHandler createFromEnvironment() {
        log.debug("Using gravitee.yml X-Frame-Options configuration for domain: {}", domain.getName());
        var action = environment.getProperty(HTTP_XFRAME_ACTION, String.class, "DENY");
        if (isNullOrEmpty(action)) {
            return new NoXFrameHandler();
        }
        return new XFrameHandlerImpl(action.trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public Class<?> getObjectType() {
        return XFrameHandler.class;
    }
}
