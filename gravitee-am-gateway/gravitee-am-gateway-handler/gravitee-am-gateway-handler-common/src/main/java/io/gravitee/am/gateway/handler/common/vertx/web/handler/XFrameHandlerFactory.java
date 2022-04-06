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
import org.springframework.beans.factory.FactoryBean;
import org.springframework.core.env.Environment;

import java.util.Locale;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.isNull;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class XFrameHandlerFactory implements FactoryBean<XFrameHandler> {

    private static final String HTTP_XFRAME_ACTION = "http.xframe.action";
    private final Environment environment;

    private static XFrameHandler INSTANCE;

    public XFrameHandlerFactory(Environment environment) {
        this.environment = environment;
    }

    @Override
    public XFrameHandler getObject() {
        if (isNull(INSTANCE)) {
            var action = environment.getProperty(HTTP_XFRAME_ACTION, String.class);
            if (isNullOrEmpty(action)) {
                INSTANCE = new NoXFrameHandler();
            } else {
                INSTANCE = new XFrameHandlerImpl(action.trim().toUpperCase(Locale.ROOT));
            }
        }
        return INSTANCE;
    }

    @Override
    public Class<?> getObjectType() {
        return XFrameHandler.class;
    }
}
