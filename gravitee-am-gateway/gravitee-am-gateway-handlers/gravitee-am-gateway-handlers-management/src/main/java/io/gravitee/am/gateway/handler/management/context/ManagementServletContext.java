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

import io.gravitee.am.gateway.handler.management.api.spring.ManagementConfiguration;
import io.gravitee.am.handler.spring.SpringServletContext;
import org.glassfish.jersey.servlet.ServletContainer;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import java.util.Collections;
import java.util.EventListener;
import java.util.List;
import java.util.Set;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
class ManagementServletContext extends SpringServletContext<Object> {

    @Override
    public List<Servlet> servlets() {
        return Collections.singletonList(new ServletContainer());
    }

    @Override
    public List<Filter> filters() {
        return Collections.emptyList(); //Collections.singletonList(new DelegatingFilterProxy("springSecurityFilterChain"));
    }

    @Override
    public List<EventListener> listeners() {
        return Collections.singletonList(new ManagementContextLoaderListener(applicationContext()));
    }

    @Override
    protected Set<Class<?>> annotatedClasses() {
        return Collections.singleton(ManagementConfiguration.class);
    }

    @Override
    public Object deployable() {
        return null;
    }

    static Builder create() {
        return new Builder();
    }

    static class Builder {

        SpringServletContext<Object> build() {
            return new ManagementServletContext();
        }
    }
}
