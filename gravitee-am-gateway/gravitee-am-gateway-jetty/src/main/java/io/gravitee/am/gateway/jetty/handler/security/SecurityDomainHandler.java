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
package io.gravitee.am.gateway.jetty.handler.security;

import io.gravitee.am.gateway.core.context.servlet.ServletContext;
import io.gravitee.am.model.Domain;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import java.text.Normalizer;
import java.util.EnumSet;
import java.util.EventListener;
import java.util.regex.Pattern;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SecurityDomainHandler extends ServletContextHandler {

    private static final String MAPPING_URL = "/*";

    private ServletContext<Domain> context;

    SecurityDomainHandler(ServletContext<Domain> context) {
        super(SESSIONS);
        this.context = context;
        setContextPath('/' + context.deployable().getPath());
        this.init();
    }

    public void init() {
        // Attach servlets
        for(Servlet servlet : context.servlets()) {
            final ServletHolder servletHolder = new ServletHolder(servlet);
            addServlet(servletHolder, MAPPING_URL);
        }

        // Attach event listeners
        for(EventListener listener : context.listeners()) {
            addEventListener(listener);
        }

        // Attach filters
        for(Filter filter : context.filters()) {
            addFilter(new FilterHolder(filter), MAPPING_URL, EnumSet.allOf(DispatcherType.class));
        }
    }

    public Domain getDomain() {
        return context.deployable();
    }
}
