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

import io.vertx.core.http.HttpMethod;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserBodyRequestParseHandler extends UserRequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(UserBodyRequestParseHandler.class);
    public static final String ERROR_PARAM = "error";
    private List<String> requiredParams;

    public UserBodyRequestParseHandler(List<String> params) {
        this.requiredParams = params;
    }

    @Override
    public void handle(RoutingContext context) {
        HttpServerRequest req = context.request();
        if (req.method() != HttpMethod.POST) {
            context.fail(405); // Must be a POST
        } else {
            if (!req.isExpectMultipart()) {
                throw new IllegalStateException("Form body not parsed - do you forget to include a BodyHandler?");
            }
            // check required parameters
            MultiMap params = req.formAttributes();
            Optional<String> missingParameter = requiredParams.stream().filter(param -> {
                String paramValue = params.get(param);
                if (paramValue == null) {
                    logger.warn("No {} provided in form - did you forget to include a BodyHandler?", param);
                    return true;
                }
                return false;
            }).findFirst();

            if (missingParameter.isPresent()) {
                redirectToPage(context, Collections.singletonMap(ERROR_PARAM, "missing_required_parameters"));
            } else {
                context.next();
            }
        }

    }
}
