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
import io.gravitee.am.gateway.handler.root.RootProvider;
import io.gravitee.am.model.Domain;
import io.gravitee.am.plugins.protocol.core.ProtocolPluginManager;
import io.gravitee.common.service.AbstractService;
import io.vertx.reactivex.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.Arrays;
import java.util.List;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class VertxSecurityDomainHandler extends AbstractService<VertxSecurityDomainHandler> {

    private static final Logger logger = LoggerFactory.getLogger(VertxSecurityDomainHandler.class);
    private static final List<String> PROTOCOLS = Arrays.asList("openid-connect", "scim", "users");

    @Autowired
    private Domain domain;

    @Autowired
    private ProtocolPluginManager protocolPluginManager;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Router router;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // start root protocol with required routes (login page, register, ...)
        startRootProtocol();

        // start security domain protocols (openid-connect, scim, ...)
        startSecurityDomainProtocols();
    }

    public Router router() {
        return router;
    }

    public String contextPath() {
        return '/' + domain.getPath();
    }

    private void startRootProtocol() {
        logger.info("Start security domain root protocol");

        try {
            ProtocolProvider protocolProvider = applicationContext.getBean(RootProvider.class);
            protocolProvider.start();
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
                logger.info("\t Protocol {} loaded", protocol);
            } catch (Exception e) {
                logger.error("\t An error occurs while loading {} protocol", protocol, e);
            }
        });
    }

}
