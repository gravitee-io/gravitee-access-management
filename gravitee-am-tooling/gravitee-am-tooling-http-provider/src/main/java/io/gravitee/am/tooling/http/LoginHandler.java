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
package io.gravitee.am.tooling.http;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;


public class LoginHandler implements Handler<RoutingContext> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginHandler.class);

    private final boolean comparePassword;

    public LoginHandler(boolean comparePassword) {
        this.comparePassword = comparePassword;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        try {
            final var usersBundle = ResourceBundle.getBundle("users");

            final var payload = routingContext.body().asJsonObject();
            final var username = payload.getString("username");
            final var passwordRef = usersBundle.getString(username +".password");

            if (passwordRef == null || "".equals(passwordRef.trim()) || (comparePassword && !passwordRef.equals(payload.getString("password")))) {
                routingContext.response()
                        .putHeader("content-type", "application/json")
                        .setStatusCode(401).end();
            }

            if (!comparePassword && username.equalsIgnoreCase("user03")) {
                routingContext.response()
                        .putHeader("content-type", "application/json")
                        .setStatusCode(500).end(JsonObject.of("error", "Service Not Available").encodePrettily());
            }

            final JsonObject responsePayload = new JsonObject();
            responsePayload.put("username", username);
            responsePayload.put("sub", username);
            Optional.ofNullable(usersBundle.getString(username +".preferred_username")).ifPresent(value -> responsePayload.put("preferred_username", value));
            Optional.ofNullable(usersBundle.getString(username +".given_name")).ifPresent(value -> responsePayload.put("given_name", value));
            Optional.ofNullable(usersBundle.getString(username +".first_name")).ifPresent(value -> responsePayload.put("first_name", value));
            Optional.ofNullable(usersBundle.getString(username +".email")).ifPresent(value -> responsePayload.put("email", value));

            //response ok
            final int statusCode = 200;
            routingContext.response()
                    .putHeader("content-type", "application/json")
                    .setStatusCode(statusCode)
                    .end(responsePayload.encodePrettily()); // return JWT as JSON object
        } catch (MissingResourceException e) {
            LOGGER.info("Username not found", e);
            routingContext.fail(401, e);
        } catch (Exception e) {
            LOGGER.warn("Unable to process the FAPI resource request", e);
            routingContext.fail(500, e);
        }
    }
}
