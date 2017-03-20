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
package io.gravitee.am.gateway.jetty;

import io.gravitee.am.gateway.core.context.Context;
import io.gravitee.am.gateway.core.context.ContextFactoryRegistry;
import io.gravitee.am.gateway.core.context.servlet.ServletContext;
import io.gravitee.am.gateway.core.http.AbstractHttpServer;
import io.gravitee.am.gateway.jetty.handler.ManagementContextHandler;
import io.gravitee.am.gateway.jetty.handler.security.SecurityDomainHandlerCollection;
import io.gravitee.am.gateway.jetty.handler.NoContentOutputErrorHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class JettyHttpServer extends AbstractHttpServer<JettyHttpServer, Server> {

    /**
     * Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(JettyHttpServer.class);

    @Autowired
    private Server server;

    @Autowired
    private SecurityDomainHandlerCollection domainHandlerCollection;

    @Autowired
    private ContextFactoryRegistry contextFactoryRegistry;

    @Override
    protected void doStart() throws Exception {
        attachNoContentHandler();
        attachHandlers();

        server.setStopAtShutdown(true);

        try {
            server.join();

            // Start HTTP server...
            server.start();

            logger.info("HTTP Server is now started and listening on port {}",
                    ((ServerConnector) server.getConnectors()[0]).getPort());
        } catch (InterruptedException ex) {
            logger.error("An error occurs while trying to initialize HTTP server", ex);
        }
    }

    private void attachNoContentHandler() {
        AbstractHandler noContentHandler = new NoContentOutputErrorHandler();

        // This part is needed to avoid WARN while starting container.
        noContentHandler.setServer(server);
        server.addBean(noContentHandler);
    }

    private void attachHandlers() {
        Context context = contextFactoryRegistry.create(null);

        if (context instanceof ServletContext) {
            ManagementContextHandler managementContextHandler = new ManagementContextHandler((ServletContext) context);
            ((ContextHandlerCollection)domainHandlerCollection.getHandler()).addHandler(managementContextHandler);
            ((ContextHandlerCollection)domainHandlerCollection.getHandler()).manage(managementContextHandler);
        }

        server.setHandler(domainHandlerCollection);
    }

    @Override
    protected void doStop() throws Exception {
        server.stop();
    }

    @Override
    public Server nativeServer() {
        return server;
    }
}