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
package io.gravitee.am.gateway.handler.root.resources.handler.login;

import io.gravitee.am.common.web.UriBuilder;
import io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest;
import io.gravitee.am.model.Domain;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;

/**
 * Failure handler for post login action callback errors.
 * Redirects to login page with appropriate error message.
 *
 * @author GraviteeSource Team
 */
public class PostLoginActionCallbackFailureHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(PostLoginActionCallbackFailureHandler.class);

    private final Domain domain;

    public PostLoginActionCallbackFailureHandler(Domain domain) {
        this.domain = domain;
    }

    @Override
    public void handle(RoutingContext context) {
        if (context.failed()) {
            Throwable throwable = context.failure();
            logger.error("Post login action callback failed", throwable);

            // Clear session
            if (context.session() != null) {
                context.session().destroy();
            }
            context.clearUser();

            // Determine error message
            String error = "post_login_action_error";
            String errorDescription = throwable != null ? throwable.getMessage() : "An error occurred during post login action";

            // Build login URL with error
            String loginUrl = UriBuilderRequest.resolveProxyRequest(
                    context.request(),
                    context.get(CONTEXT_PATH) + "/login"
            );

            String errorUrl = UriBuilder.fromHttpUrl(loginUrl)
                    .addParameter("error", error)
                    .addParameter("error_description", errorDescription)
                    .buildString();

            // Redirect to login page
            context.response()
                    .putHeader(HttpHeaders.LOCATION, errorUrl)
                    .setStatusCode(302)
                    .end();
        }
    }
}
