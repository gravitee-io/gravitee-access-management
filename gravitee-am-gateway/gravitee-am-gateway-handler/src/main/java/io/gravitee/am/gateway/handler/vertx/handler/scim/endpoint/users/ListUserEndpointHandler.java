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
package io.gravitee.am.gateway.handler.vertx.handler.scim.endpoint.users;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.gateway.handler.scim.UserService;
import io.gravitee.am.gateway.handler.vertx.utils.UriBuilderRequest;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.Handler;
import io.vertx.reactivex.core.http.HttpServerRequest;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ListUserEndpointHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(ListUserEndpointHandler.class);
    private static final int MAX_ITEMS_PER_PAGE = 100;
    private static final int DEFAULT_START_INDEX = 1;
    private UserService userService;
    private ObjectMapper objectMapper;

    public ListUserEndpointHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(RoutingContext context) {
        // Pagination (https://tools.ietf.org/html/rfc7644#section-3.4.2.4)
        Integer page = DEFAULT_START_INDEX;
        Integer size = MAX_ITEMS_PER_PAGE;


        // The 1-based index of the first query result.
        // A value less than 1 SHALL be interpreted as 1.
        try {
            final String startIndex = context.request().getParam("startIndex");
            page = Integer.valueOf(startIndex);
        } catch (Exception ex) {
        }
        // Non-negative integer. Specifies the desired  results per page, e.g., 10.
        // A negative value SHALL be interpreted as "0".
        // A value of "0"  indicates that no resource results are to be returned except for "totalResults".
        try {
            final String count = context.request().getParam("count");
            size = Integer.min(Integer.valueOf(count), MAX_ITEMS_PER_PAGE);
        } catch (Exception ex) {

        }

        // user service use 0-based index
        userService.list(page - 1, size, location(context.request()))
                .subscribe(
                        users -> context.response()
                                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
                                .end(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(users)),
                        error -> context.fail(error));
    }

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public static ListUserEndpointHandler create(UserService userService) {
        return new ListUserEndpointHandler(userService);
    }

    private String location(HttpServerRequest request) {
        try {
            return UriBuilderRequest.resolveProxyRequest(request, request.path(), null);
        } catch (URISyntaxException e) {
            logger.error("An error occurs while decoding SCIM Users location URI", e);
            return "";
        }
    }
}
