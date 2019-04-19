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
package io.gravitee.am.gateway.handler.oauth2.resources.auth.provider;

import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.oauth2.exception.BadClientCredentialsException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.resources.auth.user.Client;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.ClientAssertionService;
import io.reactivex.Maybe;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientAssertionAuthenticationProvider implements AuthProvider {

    private final Logger logger = LoggerFactory.getLogger(ClientAssertionAuthenticationProvider.class);
    public static final String BASE_PATH_PARAM = "BASE_PATH";

    private ClientAssertionService clientAssertionService;

    public ClientAssertionAuthenticationProvider() {
    }

    public ClientAssertionAuthenticationProvider(ClientAssertionService clientAssertionService) {
        this.clientAssertionService = clientAssertionService;
    }

    @Override
    public void authenticate(JsonObject jsonObject, Handler<AsyncResult<User>> handler) {
        String clientAssertionType = jsonObject.getString(Parameters.CLIENT_ASSERTION_TYPE);
        String clientAssertion = jsonObject.getString(Parameters.CLIENT_ASSERTION);
        String clientId = jsonObject.getString(Parameters.CLIENT_ID);
        String basePath = jsonObject.getString(BASE_PATH_PARAM);

        clientAssertionService.assertClient(clientAssertionType, clientAssertion, basePath)
                .flatMap(client -> {
                    //clientId is optional, but if provided we must ensure it is the same than the logged client.
                    if(clientId != null && !clientId.equals(client.getClientId())) {
                        return Maybe.error(new InvalidClientException("client_id parameter does not match with assertion"));
                    }
                    return Maybe.just(client);
                })
                .subscribe(
                        client -> handler.handle(Future.succeededFuture(new Client(client))),
                        throwable -> {
                            if (throwable instanceof InvalidClientException) {
                                logger.debug("Failed to authenticate client with assertion method", throwable);
                            } else {
                                logger.error("Failed to authenticate client with assertion method", throwable);
                            }
                            handler.handle(Future.failedFuture(throwable));
                        },
                        () -> handler.handle(Future.failedFuture(new BadClientCredentialsException())));

    }

    public void setClientAssertionService(ClientAssertionService clientAssertionService) {
        this.clientAssertionService = clientAssertionService;
    }
}
