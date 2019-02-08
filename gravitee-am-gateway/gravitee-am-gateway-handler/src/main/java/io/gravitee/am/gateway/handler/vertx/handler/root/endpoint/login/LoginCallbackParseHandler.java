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
package io.gravitee.am.gateway.handler.vertx.handler.root.endpoint.login;

import io.gravitee.am.gateway.handler.oauth2.client.ClientSyncService;
import io.gravitee.am.gateway.handler.oauth2.exception.BadClientCredentialsException;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.gravitee.am.gateway.handler.vertx.auth.handler.RedirectAuthHandler;
import io.gravitee.am.service.utils.UriBuilder;
import io.vertx.core.Handler;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class LoginCallbackParseHandler implements Handler<RoutingContext> {

    public static final Logger logger = LoggerFactory.getLogger(LoginCallbackParseHandler.class);
    private ClientSyncService clientSyncService;

    public LoginCallbackParseHandler(ClientSyncService clientSyncService) {
        this.clientSyncService = clientSyncService;
    }

    @Override
    public void handle(RoutingContext context) {
        try {
            final String clientId = getQueryParams(context.session().get(RedirectAuthHandler.DEFAULT_RETURN_URL_PARAM)).get(OAuth2Constants.CLIENT_ID);
            clientSyncService.findByClientId(clientId)
                    .subscribe(
                            client -> {
                                context.put(OAuth2Constants.CLIENT_ID, client.getId());
                                context.next();
                            },
                            ex -> {
                                logger.error("An error occurs while getting client {}", clientId, ex);
                                context.fail(new BadClientCredentialsException());
                            },
                            () -> {
                                logger.error("Unknown client {}", clientId);
                                context.fail(new BadClientCredentialsException());
                            }
                    );

        } catch (Exception e) {
            logger.error("Failed to parseAuthorization for OAuth 2.0 provider", e);
            context.fail(new BadClientCredentialsException());
        }

    }

    private Map<String, String> getQueryParams(String url) throws URISyntaxException {
        URI uri = UriBuilder.fromHttpUrl(url).build();
        Map<String, String> query_pairs = new LinkedHashMap<>();
        String query = uri.getQuery();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            query_pairs.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
        return query_pairs;
    }
}
