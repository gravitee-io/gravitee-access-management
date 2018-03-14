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
package io.gravitee.am.management.handlers.admin.context;

import io.gravitee.am.management.handlers.admin.spring.AdminConfiguration;
import io.gravitee.am.management.handlers.spring.SpringServletContext;
import io.gravitee.am.model.Domain;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.servlet.DispatcherServlet;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import java.util.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AdminSpringServletContext extends SpringServletContext<Domain> {

    private final Domain domain;

    private AdminSpringServletContext(Domain domain) {
        this.domain = domain;
    }

    @Override
    public List<Servlet> servlets() {
        return Collections.singletonList(new DispatcherServlet(applicationContext()));
    }

    @Override
    public List<Filter> filters() {
        return Collections.singletonList(new DelegatingFilterProxy("springSecurityFilterChain"));
    }

    @Override
    public List<EventListener> listeners() {
        return Collections.singletonList(new AdminContextLoaderListener(applicationContext()));
    }

    @Override
    public Domain deployable() {
        return domain;
    }

    @Override
    protected Set<Class<?>> annotatedClasses() {
        return new HashSet<>(Arrays.asList(AdminConfiguration.class));
    }

    @Override
    protected Set<? extends BeanFactoryPostProcessor> beanFactoryPostProcessors() {
        return Collections.singleton(new AdminDomainBeanFactoryPostProcessor((Domain) deployable()));
    }

    static Builder create(Domain domain) {
        return new Builder(domain);
    }

    static class Builder {

        private final Domain domain;

        private Builder(Domain domain) {
            this.domain = domain;
        }

        SpringServletContext<Domain> build() {
            return new AdminSpringServletContext(domain);
        }
    }
}
