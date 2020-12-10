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

import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.CSRFHandlerImpl;
import io.vertx.reactivex.ext.web.handler.CSRFHandler;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Value;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CSRFHandlerFactory implements FactoryBean<CSRFHandler> {

    @Value("${http.csrf.secret:s3cR3t4grAv1t3310AMS1g1ingDftK3y}")
    private String csrfSecret;

    @Override
    public CSRFHandler getObject() {
        return CSRFHandler.newInstance(new CSRFHandlerImpl(csrfSecret));
    }

    @Override
    public Class<?> getObjectType() {
        return CSRFHandler.class;
    }
}
