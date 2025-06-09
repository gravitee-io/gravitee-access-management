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

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.account.model.UpdateUsername;
import io.gravitee.am.gateway.handler.account.resources.util.AccountRoutes;
import io.gravitee.am.gateway.handler.account.resources.util.ContextPathParamUtil;
import io.gravitee.am.gateway.handler.account.services.AccountService;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.RedirectHandler;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.reporter.api.audit.AuditReportableCriteria;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.am.service.exception.UserProviderNotFoundException;
import io.gravitee.am.service.utils.vertx.RequestUtils;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static io.gravitee.am.gateway.handler.account.resources.AccountResponseHandler.handleGetProfileResponse;
import static io.gravitee.am.gateway.handler.account.resources.AccountResponseHandler.handleNoBodyResponse;
import static io.gravitee.am.gateway.handler.account.resources.AccountResponseHandler.handleUpdateUserResponse;
import static io.gravitee.am.service.dataplane.user.activity.utils.ConsentUtils.canSaveIp;
import static io.gravitee.am.service.dataplane.user.activity.utils.ConsentUtils.canSaveUserAgent;
import static io.gravitee.am.service.utils.UserProfileUtils.buildDisplayName;
import static io.gravitee.am.service.utils.UserProfileUtils.hasGeneratedDisplayName;
import static io.gravitee.common.http.HttpStatusCode.FORBIDDEN_403;
import static java.util.Objects.isNull;
import static org.springframework.util.StringUtils.isEmpty;

/**
 * @author Donald Courtney (donald.courtney at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccountEndpointHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountEndpointHandler.class);
    private static final String PASSWORD_KEY = "password";
    private static final String OLD_PASSWORD_KEY = "oldPassword";
    public static final String ERROR_MSG_ACCESS_TOKEN_TOO_OLD = "Access token does not conform with expiration period. Please generate a new token.";
    private final AccountService accountService;
    private final Domain domain;

    public AccountEndpointHandler(AccountService accountService, Domain domain) {
        this.accountService = accountService;
        this.domain = domain;
    }

    public void getUser(RoutingContext routingContext) {
        JWT token = routingContext.get(ConstantKeys.TOKEN_CONTEXT_KEY);
        accountService.getBySub(token).subscribe(
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
        handleGetProfileResponse(routingContext, routingContext.get(ConstantKeys.USER_CONTEXT_KEY));
    }

    public void getActivity(RoutingContext routingContext) {
        final User user = routingContext.get(ConstantKeys.USER_CONTEXT_KEY);
        final AuditReportableCriteria criteria = new AuditReportableCriteria.Builder().userId(user.getId()).build();
        final int page = ContextPathParamUtil.getPageNumber(routingContext);
        final int size = ContextPathParamUtil.getPageSize(routingContext);

        accountService.getActivity(user, criteria, page, size).subscribe(
                activities -> AccountResponseHandler.handleDefaultResponse(routingContext, activities),
                routingContext::fail
        );
    }

    public void changePassword(RoutingContext routingContext) {
        try {
            // update user password value from parameters
            final JsonObject bodyAsJson = routingContext.body().asJsonObject();
            if (isNull(bodyAsJson) || bodyAsJson.isEmpty()) {
                routingContext.fail(new InvalidRequestException("Body is null or empty"));
                return;
            }

            final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
            final User user = getUserFromContext(routingContext);
            final DefaultUser principal = convertUserToPrincipal(routingContext, user);
            final String password = bodyAsJson.getString(PASSWORD_KEY);
            final Optional<String> oldPassword = Optional.ofNullable(bodyAsJson.getString(OLD_PASSWORD_KEY));

            if (!tokenCanHandleResetPassword(routingContext)){
                routingContext.fail(new HttpException(HttpStatusCode.UNAUTHORIZED_401, ERROR_MSG_ACCESS_TOKEN_TOO_OLD));
                return;
            }

            if (domain.getSelfServiceAccountManagementSettings().resetPasswordWithOldValue() && oldPassword.isEmpty()) {
                routingContext.fail(new InvalidRequestException("Field [oldPassword] is required"));
                return;
            }

            // user password is required
            if (isEmpty(password)) {
                routingContext.fail(new InvalidRequestException("Field [password] is required"));
                return;
            }

            accountService.resetPassword(user, client, password, principal, oldPassword)
                    .subscribe(
                            __ -> handleNoBodyResponse(routingContext),
                            error -> {
                                if (error instanceof UserProviderNotFoundException) {
                                    handleUpdateUserResponse(routingContext, "Action forbidden", FORBIDDEN_403);
                                } else {
                                    routingContext.fail(error);
                                }
                            }
                    );
        } catch (DecodeException ex) {
            routingContext.fail(new InvalidRequestException("Unable to parse body message"));
        }
    }

    private boolean tokenCanHandleResetPassword(RoutingContext routingContext) {
        final var sefAccountSettings = domain.getSelfServiceAccountManagementSettings();
        if (sefAccountSettings.resetPasswordWithTokenAge()) {
            final JWT token = routingContext.get(ConstantKeys.TOKEN_CONTEXT_KEY);
            final long iat = token.getIat();
            if ((Instant.now().getEpochSecond() - iat) >= sefAccountSettings.getResetPassword().getTokenAge()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Create principal from the authenticated user
     * @param routingContext
     * @param user
     * @return
     */
    private DefaultUser convertUserToPrincipal(RoutingContext routingContext, User user) {
        DefaultUser principal = new DefaultUser(user.getUsername());
        Map<String, Object> additionalInformation = new HashMap<>();
        if (canSaveIp(routingContext)) {
            additionalInformation.put(Claims.IP_ADDRESS, RequestUtils.remoteAddress(routingContext.request()));
        }
        if (canSaveUserAgent(routingContext)) {
            additionalInformation.put(Claims.USER_AGENT, RequestUtils.userAgent(routingContext.request()));
        }
        additionalInformation.put(Claims.DOMAIN, domain.getId());
        principal.setAdditionalInformation(additionalInformation);
        return principal;
    }

    public void redirectForgotPassword(RoutingContext routingContext) {
        if (!tokenCanHandleResetPassword(routingContext)){
            routingContext.fail(new HttpException(HttpStatusCode.UNAUTHORIZED_401, ERROR_MSG_ACCESS_TOKEN_TOO_OLD));
            return;
        }

        final Client client = routingContext.get(ConstantKeys.CLIENT_CONTEXT_KEY);
        final String path = AccountRoutes.CHANGE_PASSWORD_REDIRECT.getRoute() + "?client_id=" + client.getClientId();
        RedirectHandler.create(path).handle(routingContext);
    }

    public void updateProfile(RoutingContext routingContext) {
        User user = getUserFromContext(routingContext);
        User updatedUser = mapRequestToUser(user, routingContext);
        if (Objects.equals(user.getId(), updatedUser.getId())) {
            accountService.update(user)
                    .subscribe(
                            nestedResult -> handleUpdateUserResponse(routingContext),
                            er -> handleUpdateUserResponse(routingContext, er.getMessage())
                    );
        } else {
            handleUpdateUserResponse(routingContext, "Mismatched user IDs", 401);
        }
    }

    public void updateUsername(RoutingContext routingContext) {
        final var user = getUserFromContext(routingContext);
        final var newUsername = routingContext.body().asPojo(UpdateUsername.class);

        accountService.updateUsername(user, newUsername, convertUserToPrincipal(routingContext, user))
                .subscribe(
                        nestedResult -> handleUpdateUserResponse(routingContext),
                        er -> handleUpdateUserResponse(routingContext, er)
                );
    }

    private User mapRequestToUser(User user, RoutingContext routingContext) {
        JsonObject bodyAsJson = routingContext.body().asJsonObject();

        final var generatedDisplayName = hasGeneratedDisplayName(user);

        user.setFirstName(bodyAsJson.getString(StandardClaims.GIVEN_NAME));
        user.setLastName(bodyAsJson.getString(StandardClaims.FAMILY_NAME));
        user.setMiddleName(bodyAsJson.getString(StandardClaims.MIDDLE_NAME));
        user.setNickName(bodyAsJson.getString(StandardClaims.NICKNAME));
        user.setProfile(bodyAsJson.getString(StandardClaims.PROFILE));
        user.setPicture(bodyAsJson.getString(StandardClaims.PICTURE));
        user.getAdditionalInformation().put(StandardClaims.PICTURE, user.getPicture());
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
        if (generatedDisplayName) {
            user.setDisplayName(buildDisplayName(user));
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
