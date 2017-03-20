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
package io.gravitee.am.gateway.handler.management.context;

import io.gravitee.am.gateway.core.context.servlet.ServletContext;
import io.gravitee.am.gateway.core.context.servlet.ServletContextFactory;
import io.gravitee.am.handler.spring.SpringServletContext;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ManagementContextFactory implements ServletContextFactory<Object>, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public boolean canHandle(Object object) {
        // There is no specific context object for ManagementContext so we are just checking that the
        // parameter is null.
        return object == null;
    }

    @Override
    public ServletContext<Object> create(Object object) {
        SpringServletContext servletContext = ManagementServletContext.create().build();
        servletContext.setRootApplicationContext(applicationContext);

        return servletContext;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}