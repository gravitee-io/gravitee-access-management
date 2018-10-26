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
package io.gravitee.am.gateway.vertx;

import io.gravitee.common.component.AbstractLifecycleComponent;
import io.gravitee.node.vertx.verticle.factory.SpringVerticleFactory;
import io.reactivex.Single;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.VertxOptions;
import io.vertx.reactivex.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * @author David BRASSELY (david at graviteesource.com)
 * @author GraviteeSource Team
 */
public class VertxEmbeddedContainer extends AbstractLifecycleComponent<VertxEmbeddedContainer> {

    /**
     * Logger.
     */
    private final Logger logger = LoggerFactory.getLogger(VertxEmbeddedContainer.class);

    @Value("${http.instances:0}")
    private int instances;

    @Autowired
    private Vertx vertx;

    private String deploymentId;

    @Override
    protected void doStart() {
        instances = (instances < 1) ? VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE : instances;
        logger.info("Starting Vertx container and deploy Gateway Verticles [{} instance(s)]", instances);

        DeploymentOptions options = new DeploymentOptions().setInstances(instances);

        Single<String> deployment = vertx.rxDeployVerticle(SpringVerticleFactory.VERTICLE_PREFIX + ':' + GraviteeVerticle.class.getName(), options);

        deployment.subscribe(id -> {
            // Deployed
            deploymentId = id;
        }, err -> {
            // Could not deploy
            logger.error("Unable to start HTTP server", err.getCause());

            // HTTP Server is a required component. Shutdown if not available
            Runtime.getRuntime().exit(1);
        });
    }

    @Override
    protected void doStop() {
        if (deploymentId != null) {
            vertx.undeploy(deploymentId);
        }
    }
}
