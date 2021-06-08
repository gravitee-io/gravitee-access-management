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
package io.gravitee.am.gateway.handler.account.resources.account;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.gateway.handler.account.resources.account.util.AccountRoutes;
import io.gravitee.am.gateway.handler.account.resources.account.util.ContextPathParamUtil;
import io.gravitee.am.gateway.handler.account.services.AccountManagementUserService;
import io.gravitee.am.gateway.handler.account.services.ActivityAuditService;
import io.gravitee.am.gateway.handler.common.client.ClientSyncService;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.RedirectHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.common.Page;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.service.FactorService;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class AccountEndpointHandler {
    private final UserService userService;
    private final FactorService factorService;
    private final ActivityAuditService activityAuditService;
    private final AccountManagementUserService accountManagementUserService;
    private final Domain domain;
    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    public AccountEndpointHandler(UserService userService,
                                  FactorService factorService,
                                  ActivityAuditService activityAuditService,
                                  AccountManagementUserService accountManagementUserService,
                                  Domain domain) {
        this.userService = userService;
        this.factorService = factorService;
        this.activityAuditService = activityAuditService;
        this.accountManagementUserService = accountManagementUserService;
        this.domain = domain;
    }

    //
    public void getAccount(RoutingContext routingContext) {
        //TODO: wip mounted angular
        User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        AccountResponseHandler.handleWIP(routingContext);
    }

    //static assets
    public void getAsset(RoutingContext routingContext) {
        //TODO: wip static web assets
        User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        AccountResponseHandler.handleWIP(routingContext);
    }

    public void updateUserFactors(RoutingContext routingContext) {
        //TODO: wip update user factors
        User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        AccountResponseHandler.handleWIP(routingContext);
    }


    public void getUser(RoutingContext routingContext) {
        JWT token = routingContext.get(ConstantKeys.TOKEN_CONTEXT_KEY);
        userService.findById(token.getSub()).doOnError(err -> {
            LOGGER.error("Unable to retrieve user for Id {}", token.getSub(), err);
        }).toSingle().subscribe(user -> {
            routingContext.put(ConstantKeys.USER_CONTEXT_KEY, user);
            routingContext.next();
        });
    }

    public void getProfile(RoutingContext routingContext) {
        AccountResponseHandler.handleGetProfileResponse(routingContext, routingContext.get(ConstantKeys.USER_CONTEXT_KEY));
    }

    public void getActivity(RoutingContext routingContext) {
        getActivityAudit(routingContext, routingContext.get(ConstantKeys.USER_CONTEXT_KEY))
                .subscribe(result -> AccountResponseHandler.handleDefaultResponse(routingContext, result));
    }

    public void getUserFactors(RoutingContext routingContext) {
        collectFactors(routingContext.get(ConstantKeys.USER_CONTEXT_KEY))
                .subscribe(factors -> AccountResponseHandler.handleDefaultResponse(routingContext, factors));
    }

    public void updateProfile(RoutingContext routingContext) {
        User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        User updatedUser = mapRequestToUser(user, routingContext);
        if (Objects.equals(user.getId(), updatedUser.getId())) {
            accountManagementUserService.update(user)
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
        address.put("street_address",addressClaim.getString("street_address"));
        address.put("locality",addressClaim.getString("locality"));
        address.put("region",addressClaim.getString("region"));
        address.put("postal_code",addressClaim.getString("postal_code"));
        address.put("country",addressClaim.getString("country"));
        return address;
    }

    private Single<Page<Audit>> getActivityAudit(RoutingContext routingContext, User user) {
        return activityAuditService.search(
                ReferenceType.DOMAIN,
                domain.getId(),
                new AuditReportableCriteria.Builder().user(user.getUsername()).build(),
                ContextPathParamUtil.getPageNumber(routingContext),
                ContextPathParamUtil.getPageSize(routingContext));
    }

    private Single<List<EnrolledFactor>> collectFactors(User user) {
        if (user.getFactors() == null) {
            return Single.just(Collections.emptyList());
        }
        return Single.just(user.getFactors());
    }

    public void redirectForgotPassword(RoutingContext routingContext) {
        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final String path = AccountRoutes.CHANGE_PASSWORD_REDIRECT.getRoute() + "?client_id=" + client.getClientId();
        RedirectHandler.create(path).handle(routingContext);
    }
}
