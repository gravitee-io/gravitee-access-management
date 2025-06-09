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
package io.gravitee.am.gateway.handler.root.resources.handler.user;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.gateway.handler.root.resources.endpoint.AbstractEndpoint;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.gravitee.common.http.HttpHeaders;
import io.vertx.core.Handler;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.common.utils.ConstantKeys.ERROR_PARAM_KEY;
import static io.gravitee.am.service.dataplane.user.activity.utils.ConsentUtils.canSaveIp;
import static io.gravitee.am.service.dataplane.user.activity.utils.ConsentUtils.canSaveUserAgent;
import static java.util.Optional.ofNullable;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class UserRequestHandler extends AbstractEndpoint implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(UserRequestHandler.class);

    @Override
    public abstract void handle(RoutingContext event);

    protected void redirectToPage(RoutingContext context, MultiMap queryParams, Throwable... exceptions) {
        try {
            if (exceptions != null && exceptions.length > 0) {
                logger.debug("Error user actions : {}", queryParams.get(ERROR_PARAM_KEY), exceptions[0]);
            }
            String uri = UriBuilderRequest.resolveProxyRequest(context.request(), context.request().path(), queryParams, true);
            doRedirect(context.response(), uri);
        } catch (Exception ex) {
            logger.error("An error occurs while redirecting to {}", context.request().absoluteURI(), ex);
            context.fail(503);
        }
    }

    protected User getAuthenticatedUser(RoutingContext routingContext) {
        io.gravitee.am.model.User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        if (user != null) {
            DefaultUser authenticatedUser = new DefaultUser(user.getUsername());
            authenticatedUser.setId(user.getId());
            Map<String, Object> additionalInformation = user.getAdditionalInformation() != null ? new HashMap<>(user.getAdditionalInformation()) : new HashMap<>();
            // add ip address and user agent
            if (canSaveIp(routingContext)) {
                additionalInformation.put(Claims.IP_ADDRESS, RequestUtils.remoteAddress(routingContext.request()));
            }

            if(canSaveUserAgent(routingContext)){
                additionalInformation.put(Claims.USER_AGENT, RequestUtils.userAgent(routingContext.request()));
            }

            if(user.getReferenceType() == ReferenceType.DOMAIN) {
                additionalInformation.put(Claims.DOMAIN, user.getReferenceId());
            }

            authenticatedUser.setAdditionalInformation(additionalInformation);
            return authenticatedUser;
        }
        return null;
    }

    private void doRedirect(HttpServerResponse response, String url) {
        final HttpServerResponse httpServerResponse = response.putHeader(HttpHeaders.LOCATION, url).setStatusCode(302);
        httpServerResponse.end();
    }

    protected io.gravitee.am.model.User convert(MultiMap params, Client client) {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        user.setUsername(params.get("username"));
        user.setFirstName(params.get("firstName"));
        user.setLastName(params.get("lastName"));
        user.setEmail(params.get("email"));
        user.setPassword(params.get("password"));
        ofNullable(client).ifPresent(cli -> user.setClient(client.getId()));
        return user;
    }
}
