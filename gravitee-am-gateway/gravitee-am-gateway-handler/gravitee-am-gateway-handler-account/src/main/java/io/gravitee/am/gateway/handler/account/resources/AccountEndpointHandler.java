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

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.gateway.handler.account.resources.util.AccountRoutes;
import io.gravitee.am.gateway.handler.account.resources.util.ContextPathParamUtil;
import io.gravitee.am.gateway.handler.account.services.AccountService;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.RedirectHandler;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Donald Courtney (donald.courtney at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccountEndpointHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountEndpointHandler.class);
    private final AccountService accountService;

    public AccountEndpointHandler(AccountService accountService) {
        this.accountService = accountService;
    }

    public void getUser(RoutingContext routingContext) {
        JWT token = routingContext.get(ConstantKeys.TOKEN_CONTEXT_KEY);
        accountService.get(token.getSub())
                .subscribe(
                        user -> {
                            routingContext.put(ConstantKeys.USER_CONTEXT_KEY, user);
                            routingContext.next();
                        },
                        error -> {
                            LOGGER.error("Unable to retrieve user for Id {}", token.getSub());
                            routingContext.fail(error);
                        },
                        () -> routingContext.fail(new UserNotFoundException(token.getSub()))
                );
    }

    public void getProfile(RoutingContext routingContext) {
        AccountResponseHandler.handleGetProfileResponse(routingContext, routingContext.get(ConstantKeys.USER_CONTEXT_KEY));
    }

    public void getActivity(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        final AuditReportableCriteria criteria = new AuditReportableCriteria.Builder().user(user.getUsername()).build();
        final int page = ContextPathParamUtil.getPageNumber(routingContext);
        final int size = ContextPathParamUtil.getPageSize(routingContext);

        accountService.getActivity(user, criteria, page, size)
                .subscribe(
                        activities -> AccountResponseHandler.handleDefaultResponse(routingContext, activities),
                        error -> routingContext.fail(error)
                );
    }

    public void redirectForgotPassword(RoutingContext routingContext) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final String path = AccountRoutes.CHANGE_PASSWORD_REDIRECT.getRoute() + "?client_id=" + client.getClientId();
        RedirectHandler.create(path).handle(routingContext);
    }

    public void updateProfile(RoutingContext routingContext) {
        User user = getUserFromContext(routingContext);
        User updatedUser = mapRequestToUser(user, routingContext);
        if (Objects.equals(user.getId(), updatedUser.getId())) {
            accountService.update(user)
                    .doOnSuccess(nestedResult -> AccountResponseHandler.handleUpdateUserResponse(routingContext))
                    .doOnError(er -> AccountResponseHandler.handleUpdateUserResponse(routingContext, er.getMessage()))
                    .subscribe();
        } else {
            AccountResponseHandler.handleUpdateUserResponse(routingContext, "Mismatched user IDs", 401);
        }
    }

    private User mapRequestToUser(User user, RoutingContext routingContext) {
        JsonObject bodyAsJson = routingContext.getBodyAsJson();
        user.setFirstName(bodyAsJson.getString(StandardClaims.GIVEN_NAME));
        user.setLastName(bodyAsJson.getString(StandardClaims.FAMILY_NAME));
        user.setMiddleName(bodyAsJson.getString(StandardClaims.MIDDLE_NAME));
        user.setNickName(bodyAsJson.getString(StandardClaims.NICKNAME));
        user.setProfile(bodyAsJson.getString(StandardClaims.PROFILE));
        user.setPicture(bodyAsJson.getString(StandardClaims.PICTURE));
        user.setWebsite(bodyAsJson.getString(StandardClaims.WEBSITE));
        user.setEmail(bodyAsJson.getString(StandardClaims.EMAIL));
        user.setBirthdate(bodyAsJson.getString(StandardClaims.BIRTHDATE));
        user.setZoneInfo(bodyAsJson.getString(StandardClaims.ZONEINFO));
        user.setLocale(bodyAsJson.getString(StandardClaims.LOCALE));
        user.setPhoneNumber(bodyAsJson.getString(StandardClaims.PHONE_NUMBER));
        final JsonObject address = bodyAsJson.getJsonObject(StandardClaims.ADDRESS);
        if (address != null) {
            user.setAddress(convertClaim(address));
        }
        return user;
    }

    private Map<String, Object> convertClaim(JsonObject addressClaim) {
        Map<String, Object> address = new HashMap<>();
        address.put("street_address", addressClaim.getString("street_address"));
        address.put("locality", addressClaim.getString("locality"));
        address.put("region", addressClaim.getString("region"));
        address.put("postal_code", addressClaim.getString("postal_code"));
        address.put("country", addressClaim.getString("country"));
        return address;
    }

    private static User getUserFromContext(RoutingContext routingContext) {
        return routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
    }
}
