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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal;

import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.Session;
import io.vertx.reactivex.ext.web.handler.FormLoginHandler;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * If a user is signed in via the auto login feature (after a reset password or a registration)
 * we must ensure that the original url (most of the time /oauth/authorize) is stored in the session
 *
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AutoLoginStep extends AuthenticationFlowStep {

    private static final Logger logger = LoggerFactory.getLogger(AutoLoginStep.class);

    public AutoLoginStep() {
        super(null);
    }

    @Override
    void execute(RoutingContext routingContext, AuthenticationFlowChain flow) {
        final Session session = routingContext.session();
        final HttpServerRequest request = routingContext.request();
        if (session != null) {
            if (routingContext.user() != null) {
                if (session.get(FormLoginHandler.DEFAULT_RETURN_URL_PARAM) == null) {
                    // set return url
                    try {
                        Map<String, String> requestParameters = request
                            .params()
                            .entries()
                            .stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                        session.put(
                            FormLoginHandler.DEFAULT_RETURN_URL_PARAM,
                            UriBuilderRequest.resolveProxyRequest(request, request.path(), requestParameters)
                        );
                    } catch (Exception ex) {
                        logger.warn("Failed to decode original redirect url", ex);
                    }
                }
            }
        }
        flow.doNext(routingContext);
    }
}
