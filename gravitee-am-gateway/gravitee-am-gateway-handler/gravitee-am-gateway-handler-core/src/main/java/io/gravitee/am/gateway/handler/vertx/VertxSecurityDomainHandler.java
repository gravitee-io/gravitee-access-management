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
package io.gravitee.am.gateway.handler.vertx;

import io.gravitee.am.gateway.handler.api.ProtocolProvider;
import io.gravitee.am.gateway.handler.manager.botdetection.BotDetectionManager;
import io.gravitee.am.gateway.handler.common.alert.AlertEventProcessor;
import io.gravitee.am.gateway.handler.common.audit.AuditReporterManager;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.auth.listener.AuthenticationEventListener;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.common.client.ClientManager;
import io.gravitee.am.gateway.handler.common.email.EmailManager;
import io.gravitee.am.gateway.handler.common.flow.FlowManager;
import io.gravitee.am.gateway.handler.manager.domain.CrossDomainManager;
import io.gravitee.am.gateway.handler.manager.factor.FactorManager;
import io.gravitee.am.gateway.handler.manager.form.FormManager;
import io.gravitee.am.gateway.handler.root.RootProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.plugins.protocol.core.ProtocolPluginManager;
import io.gravitee.common.component.LifecycleComponent;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpHeadersValues;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.service.AbstractService;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class VertxSecurityDomainHandler extends AbstractService<VertxSecurityDomainHandler> {

    private static final Logger logger = LoggerFactory.getLogger(VertxSecurityDomainHandler.class);
    private static final List<String> PROTOCOLS = Arrays.asList("discovery", "openid-connect", "scim", "users", "saml2", "account");
    private List<ProtocolProvider> protocolProviders = new ArrayList<>();

    @Autowired
    private Domain domain;

    @Autowired
    private ProtocolPluginManager protocolPluginManager;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Router router;

    @Autowired
    private Environment environment;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // start root protocol with required routes (login page, register, ...)
        startRootProtocol();

        // start security domain protocols (openid-connect, scim, ...)
        startSecurityDomainProtocols();

        // set default 404 handler
        router.route().last().handler(this::sendNotFound);
    }

    @Override
    protected void doStop() throws Exception {
        logger.info("Security domain ["+ domain.getName() + "] handler is now stopping, closing context...");

        stopComponents();
        stopProtocols();

        super.doStop();
        logger.info("Security domain [" + domain.getName() + "] handler is now stopped", domain);
    }

    public Router router() {
        return router;
    }

    public Domain getDomain() {
        return domain;
    }

    private void startRootProtocol() {
        logger.info("Start security domain root protocol");

        try {
            ProtocolProvider protocolProvider = applicationContext.getBean(RootProvider.class);
            protocolProvider.start();
            protocolProviders.add(protocolProvider);
            logger.info("\t Protocol root loaded");
        } catch (Exception e) {
            logger.error("\t An error occurs while loading root protocol", e);
            throw new IllegalStateException(e);
        }
    }

    private void startSecurityDomainProtocols() {
        logger.info("Start security domain protocols");

        PROTOCOLS.forEach(protocol -> {
            try {
                ProtocolProvider protocolProvider = protocolPluginManager.create(protocol, applicationContext);
                protocolProvider.start();
                protocolProviders.add(protocolProvider);
                logger.info("\t Protocol {} loaded", protocol);
            } catch (Exception e) {
                logger.error("\t An error occurs while loading {} protocol", protocol, e);
            }
        });
    }

    private void stopProtocols() {
        protocolProviders.forEach(protocolProvider -> {
            try {
                protocolProvider.stop();
                logger.info("\t Protocol {} stopped", protocolProvider.path());
            } catch (Exception e) {
                logger.error("\t An error occurs while stopping {} protocol", protocolProvider.path(), e);
            }
        });
    }

    private void stopComponents() {
        List<Class<? extends LifecycleComponent>> components = new ArrayList<>();
        components.add(IdentityProviderManager.class);
        components.add(FormManager.class);
        components.add(EmailManager.class);
        components.add(AuditReporterManager.class);
        components.add(FlowManager.class);
        components.add(AuthenticationEventListener.class);
        components.add(AlertEventProcessor.class);
        components.add(FactorManager.class);
        components.add(BotDetectionManager.class);
        components.add(CrossDomainManager.class);
        components.add(ClientManager.class);
        components.add(CertificateManager.class);

        components.forEach(componentClass -> {
            LifecycleComponent lifecyclecomponent = applicationContext.getBean(componentClass);
            try {
                lifecyclecomponent.stop();
            } catch (Exception e) {
                logger.error("An error occurs while stopping component {}", componentClass.getSimpleName(), e);
            }
        });
    }

    private void sendNotFound(RoutingContext context) {
        // Send a NOT_FOUND HTTP status code (404)  if domain's sub url didn't match any route.
        HttpServerResponse serverResponse = context.response();
        serverResponse.setStatusCode(HttpStatusCode.NOT_FOUND_404);

        String message = environment.getProperty("http.domain.errors[404].message", "No endpoint matches the request URI.");
        serverResponse.headers().set(HttpHeaders.CONTENT_LENGTH, Integer.toString(message.length()));
        serverResponse.headers().set(HttpHeaders.CONTENT_TYPE, "text/plain");
        serverResponse.headers().set(HttpHeaders.CONNECTION, HttpHeadersValues.CONNECTION_CLOSE);
        serverResponse.write(Buffer.buffer(message));

        serverResponse.end();
    }
}
