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
package io.gravitee.am.management.jetty.handler;

import io.gravitee.am.management.core.context.servlet.ServletContext;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import java.util.EnumSet;
import java.util.EventListener;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ManagementContextHandler extends ServletContextHandler {

    private static final String MAPPING_URL = "/*";

    private ServletContext<Object> context;

    public ManagementContextHandler(ServletContext<Object> context) {
        super(SESSIONS);
        this.context = context;
        this.init();
    }

    public void init() {
        setContextPath("/management");

        // REST configuration
        Servlet servlet = context.servlets().iterator().next();
        final ServletHolder servletHolder = new ServletHolder(servlet);
        servletHolder.setInitParameter("javax.ws.rs.Application", "io.gravitee.am.management.handlers.management.api.ManagementApplication");
        servletHolder.setInitOrder(0);
        addServlet(servletHolder, "/*");

        // Attach event listeners
        for(EventListener listener : context.listeners()) {
            addEventListener(listener);
        }

        // Attach filters
        for(Filter filter : context.filters()) {
            addFilter(new FilterHolder(filter), MAPPING_URL, EnumSet.allOf(DispatcherType.class));
        }
    }
}
