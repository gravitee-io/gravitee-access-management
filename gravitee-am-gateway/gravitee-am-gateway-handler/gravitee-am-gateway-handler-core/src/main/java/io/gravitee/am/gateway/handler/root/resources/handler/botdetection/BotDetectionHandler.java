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
package io.gravitee.am.gateway.handler.root.resources.handler.botdetection;

import io.gravitee.am.botdetection.api.BotDetectionContext;
import io.gravitee.am.gateway.handler.manager.botdetection.BotDetectionManager;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.exception.BotDetectedException;
import io.gravitee.am.service.exception.TechnicalManagementException;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.common.http.HttpStatusCode.BAD_REQUEST_400;
import static io.gravitee.common.http.HttpStatusCode.INTERNAL_SERVER_ERROR_500;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class BotDetectionHandler implements Handler<RoutingContext> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BotDetectionHandler.class);
    public static final String DEFAULT_ERROR_MSG = "Something goes wrong. Please try again.";

    private final Domain domain;

    private final BotDetectionManager botDetectionManager;

    public BotDetectionHandler(Domain domain, BotDetectionManager botDetectionManager) {
        this.domain = domain;
        this.botDetectionManager = botDetectionManager;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        Client client = routingContext.get(CLIENT_CONTEXT_KEY);
        AccountSettings accountSettings = AccountSettings.getInstance(domain, client);

        if (accountSettings == null || !accountSettings.isUseBotDetection()) {
            routingContext.next();
            return;
        }

        if (StringUtils.isEmpty(accountSettings.getBotDetectionPlugin())) {
            LOGGER.error("Bot Detection enable without plugin identifier for domain '{}' and application '{}'", domain.getId(), client.getId());
            routingContext.fail(INTERNAL_SERVER_ERROR_500, new TechnicalManagementException(DEFAULT_ERROR_MSG));
            return;
        }

        final MultiMap headers = routingContext.request().headers();
        final MultiMap params = routingContext.request().params();
        BotDetectionContext context = new BotDetectionContext(accountSettings.getBotDetectionPlugin(), headers, params);

        botDetectionManager
                .validate(context)
                .subscribe(
                        (isValid) -> {
                            if (isValid) {
                                LOGGER.debug("No bot detected for domain '{}' and client '{}'", domain.getId(), client.getId());
                                routingContext.next();
                            } else {
                                LOGGER.warn("Bot detected for domain '{}' and client '{}'", domain.getId(), client.getId());
                                routingContext.fail(BAD_REQUEST_400, new BotDetectedException(DEFAULT_ERROR_MSG));
                            }
                        },
                        (error) -> {
                            LOGGER.error("BotDetection failed for domain '{}' and client '{}'", domain.getId(), client.getId(), error);
                            routingContext.fail(INTERNAL_SERVER_ERROR_500, new TechnicalManagementException(DEFAULT_ERROR_MSG));
                        });
    }
}
