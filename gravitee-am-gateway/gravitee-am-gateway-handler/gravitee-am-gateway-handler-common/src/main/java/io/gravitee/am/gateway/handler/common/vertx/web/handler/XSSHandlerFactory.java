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
import org.springframework.beans.factory.FactoryBean;
import org.springframework.core.env.Environment;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.isNull;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class XSSHandlerFactory implements FactoryBean<XSSHandler> {

    private static final String HTTP_XSS_ENABLED = "http.xss.enabled";
    private static final String HTTP_XSS_ACTION = "http.xss.action";
    private final Environment environment;

    private static XSSHandler INSTANCE;

    public XSSHandlerFactory(Environment environment) {
        this.environment = environment;
    }

    @Override
    public XSSHandler getObject() {
        if (isNull(INSTANCE)) {
            var action = environment.getProperty(HTTP_XSS_ACTION, String.class, "1; mode=block");
            final boolean notEnabled = !environment.getProperty(HTTP_XSS_ENABLED, Boolean.class, false);
            if (isNullOrEmpty(action) || notEnabled) {
                INSTANCE = new NoXSSHandler();
            } else {
                INSTANCE = new XSSHandlerImpl(action.trim());
            }
        }
        return INSTANCE;
    }

    @Override
    public Class<?> getObjectType() {
        return XSSHandler.class;
    }
}
