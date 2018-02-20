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
package io.gravitee.am.management.handlers.oauth2.context;

import io.gravitee.am.management.core.context.servlet.ServletContext;
import io.gravitee.am.management.core.context.servlet.ServletContextFactory;
import io.gravitee.am.management.handlers.spring.SpringServletContext;
import io.gravitee.am.model.Domain;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2ContextFactory implements ServletContextFactory<Domain>, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public boolean canHandle(Domain domain) {
        return domain != null; // && domain.getType() == Type.OAUTH2;
    }

    @Override
    public ServletContext<Domain> create(Domain domain) {
        SpringServletContext servletContext = OAuth2SpringServletContext.create(domain).build();
        servletContext.setRootApplicationContext(applicationContext);

        return servletContext;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
