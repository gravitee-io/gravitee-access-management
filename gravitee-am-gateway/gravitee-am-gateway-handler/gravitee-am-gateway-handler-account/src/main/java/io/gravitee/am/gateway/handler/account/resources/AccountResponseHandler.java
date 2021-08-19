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
package io.gravitee.am.gateway.handler.account.resources;

import io.gravitee.am.model.User;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.MediaType;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.RoutingContext;

/**
 * @author Donald Courtney (donald.courtney at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccountResponseHandler {

    public static void handleDefaultResponse(RoutingContext routingContext, Object obj){
        buildDefaultHeader(routingContext).end(Json.encodePrettily(obj));
    }

    public static void handleGetProfileResponse(RoutingContext routingContext, User user){
        JsonObject userJson = JsonObject.mapFrom(user);
        userJson.remove("factors");
        buildDefaultHeader(routingContext).end(userJson.encodePrettily());
    }

    public static void handleUpdateUserResponse(RoutingContext routingContext) {
        buildDefaultHeader(routingContext).end(getUpdateUserResponseJson());
    }

    public static void handleUpdateUserResponse(RoutingContext routingContext, String message) {
        handleUpdateUserResponse(routingContext, message, 500);
    }

    public static void handleUpdateUserResponse(RoutingContext routingContext, String message, Integer statusCode) {
        buildDefaultHeader(routingContext).setStatusCode(statusCode).end(getUpdateUserResponseFailureJson(message));
    }

    private static HttpServerResponse buildDefaultHeader(RoutingContext routingContext) {
        return routingContext.response()
                .putHeader(HttpHeaders.CACHE_CONTROL, "no-store")
                .putHeader(HttpHeaders.PRAGMA, "no-cache")
                .putHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);
    }

    private static String getUpdateUserResponseFailureJson(String message) {
        return new JsonObject().put("status", "KO").put("errorMessage", message).toString();
    }

    private static String getUpdateUserResponseJson() {
        return new JsonObject().put("status", "OK").toString();
    }

    public static void handleNoBodyResponse(RoutingContext routingContext) {
        routingContext.response().setStatusCode(204).end();
    }
}
