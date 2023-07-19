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
package io.gravitee.am.management.standalone.server;

import io.gravitee.am.management.handlers.management.api.ManagementApplication;
import io.gravitee.am.management.handlers.management.api.spring.ManagementConfiguration;
import io.gravitee.node.jetty.JettyHttpServer;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.servlet.ServletContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.servlet.DispatcherServlet;

import java.util.EnumSet;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ManagementApiServer extends JettyHttpServer {

    @Value("${http.api.entrypoint:/management}")
    private String entrypoint;

    @Autowired
    private ApplicationContext applicationContext;

    public ManagementApiServer() {
        super();
    }

    public void attachHandlers() {

        // Create the servlet context
        final ServletContextHandler context = new ServletContextHandler(this.server, entrypoint, ServletContextHandler.NO_SESSIONS);

        // REST configuration
        final ServletHolder servletHolder = new ServletHolder(ServletContainer.class);
        servletHolder.setInitParameter("jakarta.ws.rs.Application", ManagementApplication.class.getName());
        servletHolder.setInitOrder(1);
        servletHolder.setAsyncSupported(true);

        AnnotationConfigWebApplicationContext webApplicationContext = new AnnotationConfigWebApplicationContext();
        webApplicationContext.setEnvironment((ConfigurableEnvironment) applicationContext.getEnvironment());
        webApplicationContext.setParent(applicationContext);
        webApplicationContext.setServletContext(context.getServletContext());

        webApplicationContext.register(ManagementConfiguration.class);

        context.addEventListener(new ContextLoaderListener(webApplicationContext));
        context.addServlet(servletHolder, "/*");
        context.addServlet(new ServletHolder(new DispatcherServlet(webApplicationContext)), "/auth/*");

        // X-Forwarded-* support
        context.addFilter(ForwardedHeaderFilter.class, "/*", EnumSet.allOf(DispatcherType.class));

        // Spring Security filter
        context.addFilter(new FilterHolder(new DelegatingFilterProxy("springSecurityFilterChain")), "/*", EnumSet.allOf(DispatcherType.class));
    }
}