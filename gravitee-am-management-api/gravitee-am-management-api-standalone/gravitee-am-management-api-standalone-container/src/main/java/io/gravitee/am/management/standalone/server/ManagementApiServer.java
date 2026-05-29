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

import io.gravitee.am.management.handlers.automation.AutomationApiApplication;
import io.gravitee.am.management.handlers.automation.spring.AutomationConfiguration;
import io.gravitee.am.management.handlers.automation.swagger.AutomationApiDefinition;
import io.gravitee.am.management.handlers.management.api.ManagementApplication;
import io.gravitee.am.management.handlers.management.api.spring.ManagementConfiguration;
import io.gravitee.node.jetty.JettyHttpServer;
import jakarta.servlet.DispatcherType;
import org.eclipse.jetty.ee10.servlet.FilterHolder;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.filter.ForwardedHeaderFilter;
import org.springframework.web.servlet.DispatcherServlet;

import java.util.ArrayList;
import java.util.EnumSet;

/**
 * Configures and starts the Management API server.
 * <p>
 * Each API surface (Management, Automation) gets its own {@link ServletContextHandler}
 * with an isolated Spring child context and security filter chain, following the APIM
 * pattern of per-API context isolation via {@link ContextHandlerCollection}.
 *
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ManagementApiServer extends JettyHttpServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManagementApiServer.class);

    @Value("${http.api.entrypoint:/management}")
    private String entrypoint;

    @Value("${http.api.automation.enabled:false}")
    private boolean startAutomationAPI;

    @Value("${http.api.automation.entrypoint:" + AutomationApiDefinition.DEFAULT_AUTOMATION_ENTRYPOINT + "}")
    private String automationEntrypoint;

    @Autowired
    private ApplicationContext applicationContext;

    public ManagementApiServer() {
        super();
    }

    public void attachHandlers() {
        var contexts = new ArrayList<ContextHandler>();

        if (startAutomationAPI) {
            LOGGER.info("Automation API is enabled, registering at {}", automationEntrypoint);
            contexts.add(configureAutomationAPI());
        }

        contexts.add(configureManagementAPI());

        server.setHandler(new ContextHandlerCollection(contexts.toArray(new ContextHandler[0])));
    }

    private ServletContextHandler configureManagementAPI() {
        final ServletContextHandler context = new SafeClassLoaderServletContextHandler(entrypoint, ServletContextHandler.NO_SESSIONS);

        final ServletHolder servletHolder = createServletHolder(
                "gravitee-am-management-api",
                ManagementApplication.class.getName(),
                "management-api",
                "io.gravitee.am.management.handlers.management.api.resources"
            );

        AnnotationConfigWebApplicationContext webApplicationContext = new AnnotationConfigWebApplicationContext();
        webApplicationContext.setEnvironment((ConfigurableEnvironment) applicationContext.getEnvironment());
        webApplicationContext.setParent(applicationContext);
        webApplicationContext.setServletContext(context.getServletContext());
        webApplicationContext.register(ManagementConfiguration.class);

        context.addEventListener(new ContextLoaderListener(webApplicationContext));
        context.addServlet(servletHolder, "/*");
        context.addServlet(new ServletHolder(new DispatcherServlet(webApplicationContext)), "/auth/*");

        context.addFilter(ForwardedHeaderFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
        context.addFilter(new FilterHolder(new DelegatingFilterProxy("springSecurityFilterChain")), "/*", EnumSet.allOf(DispatcherType.class));

        return context;
    }

    private ServletContextHandler configureAutomationAPI() {
        final ServletContextHandler context = new SafeClassLoaderServletContextHandler(automationEntrypoint, ServletContextHandler.NO_SESSIONS);

        final ServletHolder servletHolder = createServletHolder(
            "gravitee-am-automation-api",
            AutomationApiApplication.class.getName(),
            "automation-api",
            "io.gravitee.am.management.handlers.automation"
        );

        AnnotationConfigWebApplicationContext webApplicationContext = new AnnotationConfigWebApplicationContext();
        webApplicationContext.setEnvironment((ConfigurableEnvironment) applicationContext.getEnvironment());
        webApplicationContext.setParent(applicationContext);
        webApplicationContext.setServletContext(context.getServletContext());
        webApplicationContext.register(AutomationConfiguration.class);

        context.addEventListener(new ContextLoaderListener(webApplicationContext));
        context.addServlet(servletHolder, "/*");

        context.addFilter(ForwardedHeaderFilter.class, "/*", EnumSet.allOf(DispatcherType.class));
        context.addFilter(new FilterHolder(new DelegatingFilterProxy("springSecurityFilterChain")), "/*", EnumSet.allOf(DispatcherType.class));

        return context;
    }

    private static ServletHolder createServletHolder(
            String name,
            String applicationClass,
            String openApiContextId,
            String resourcePackages
    ) {
        ServletHolder servletHolder = new ServletHolder(ServletContainer.class);
        servletHolder.setName(name);
        servletHolder.setInitParameter("jakarta.ws.rs.Application", applicationClass);

        // Identifies the OpenAPI context for this API.
        servletHolder.setInitParameter("openapi.context.id", openApiContextId);

        // Limits classpath scanning to the API's JAX-RS resources. Required when using a dedicated openapi.context.id.
        servletHolder.setInitParameter("openApi.configuration.resourcePackages", resourcePackages);

        servletHolder.setInitOrder(1);
        servletHolder.setAsyncSupported(true);

        return servletHolder;
    }
}
