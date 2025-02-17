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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.account.model.UpdateUsername;
import io.gravitee.am.gateway.handler.account.services.AccountService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.gateway.handler.root.service.response.ResetPasswordResponse;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.SelfServiceAccountManagementSettings;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.exception.InvalidUserException;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static io.gravitee.am.common.oidc.StandardClaims.BIRTHDATE;
import static io.gravitee.am.common.oidc.StandardClaims.EMAIL;
import static io.gravitee.am.common.oidc.StandardClaims.FAMILY_NAME;
import static io.gravitee.am.common.oidc.StandardClaims.GIVEN_NAME;
import static io.gravitee.am.common.oidc.StandardClaims.LOCALE;
import static io.gravitee.am.common.oidc.StandardClaims.MIDDLE_NAME;
import static io.gravitee.am.common.oidc.StandardClaims.NICKNAME;
import static io.gravitee.am.common.oidc.StandardClaims.PHONE_NUMBER;
import static io.gravitee.am.common.oidc.StandardClaims.PICTURE;
import static io.gravitee.am.common.oidc.StandardClaims.PROFILE;
import static io.gravitee.am.common.oidc.StandardClaims.WEBSITE;
import static io.gravitee.am.common.oidc.StandardClaims.ZONEINFO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AccountEndpointHandlerTest extends RxWebTestBase {
    private static final String REQUEST_PATH = "/account/api/profile";
    private static final String USERNAME_REQUEST_PATH = REQUEST_PATH + "/username";
    private static final String CHANGE_PWD_REQUEST_PATH = "/account/api/changePassword";

    public static final int TOKEN_AGE_IN_SEC = 600;

    private Domain domain;
    @Mock
    private User user;
    @Mock
    private JWT jwt;
    @Mock
    private Client client;
    @Mock
    private AccountService accountService;

    private AccountEndpointHandler accountEndpointHandler;

    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public void setUp() throws Exception {
        super.setUp();

        this.domain = new Domain();
        final var selfAccountSettings = new SelfServiceAccountManagementSettings();
        selfAccountSettings.setEnabled(true);
        this.domain.setSelfServiceAccountManagementSettings(selfAccountSettings);

        when(user.getUsername()).thenReturn("user1");
        accountEndpointHandler = new AccountEndpointHandler(accountService, domain);

        router.route()
                .handler(BodyHandler.create())
                .handler(ctx -> {
                    ctx.put(ConstantKeys.USER_CONTEXT_KEY, user);
                    ctx.put(ConstantKeys.TOKEN_CONTEXT_KEY, jwt);
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    ctx.next();
                })
                .failureHandler(new ErrorHandler());
    }

    private void initUserContextKeyValue(User user) {
        router.getRoutes().clear();
        router.route()
                .handler(BodyHandler.create())
                .handler(ctx -> {
                    ctx.put(ConstantKeys.USER_CONTEXT_KEY, user);
                    ctx.next();
                })
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void shouldChangePassword_OnlyNewValue() throws Exception {
        router.route(CHANGE_PWD_REQUEST_PATH)
                .handler(accountEndpointHandler::changePassword)
                .handler(rc -> rc.response().end());

        when(accountService.resetPassword(any(), any(), any(), any(), any())).thenReturn(Single.just(new ResetPasswordResponse()));

        testRequest(HttpMethod.POST,
                CHANGE_PWD_REQUEST_PATH,
                req -> {
                    req.headers().set("content-type", "application/json");
                    setBody(req, Map.of("password", "Test123!"));
                },
                204,
                "No Content", null);

        verify(accountService).resetPassword(any(), any(), eq("Test123!"), any(), any());
    }

    @Test
    public void shouldChangePassword_TokenNotExpired() throws Exception {
        router.route(CHANGE_PWD_REQUEST_PATH)
                .handler(accountEndpointHandler::changePassword)
                .handler(rc -> rc.response().end());

        // reject request older than 10 minutes
        final var resetPasswordSettings = new SelfServiceAccountManagementSettings.ResetPasswordSettings();
        resetPasswordSettings.setTokenAge(TOKEN_AGE_IN_SEC);
        this.domain.getSelfServiceAccountManagementSettings().setResetPassword(resetPasswordSettings);

        when(jwt.getIat()).thenReturn(Instant.now().minus(TOKEN_AGE_IN_SEC - 1, ChronoUnit.SECONDS).getEpochSecond());

        when(accountService.resetPassword(any(), any(), any(), any(), any())).thenReturn(Single.just(new ResetPasswordResponse()));

        testRequest(HttpMethod.POST,
                CHANGE_PWD_REQUEST_PATH,
                req -> {
                    req.headers().set("content-type", "application/json");
                    setBody(req, Map.of("password", "Test123!"));
                },
                204,
                "No Content", null);

        verify(accountService).resetPassword(any(), any(), eq("Test123!"), any(), any());
    }

    @Test
    public void shouldNotChangePassword_JwtTooOld() throws Exception {
        router.route(CHANGE_PWD_REQUEST_PATH)
                .handler(accountEndpointHandler::changePassword)
                .handler(rc -> rc.response().end());

        // reject request older than 10 minutes
        final var resetPasswordSettings = new SelfServiceAccountManagementSettings.ResetPasswordSettings();
        resetPasswordSettings.setTokenAge(TOKEN_AGE_IN_SEC);
        this.domain.getSelfServiceAccountManagementSettings().setResetPassword(resetPasswordSettings);

        when(jwt.getIat()).thenReturn(Instant.now().minus(TOKEN_AGE_IN_SEC + 1, ChronoUnit.SECONDS).getEpochSecond());

        testRequest(HttpMethod.POST,
                CHANGE_PWD_REQUEST_PATH,
                req -> {
                    req.headers().set("content-type", "application/json");
                    setBody(req, Map.of("password", "Test123!"));
                },
                401,
                "Unauthorized", null);

        verify(accountService, never()).resetPassword(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldNotChangePassword_MissingOldPassword() throws Exception {
        router.route(CHANGE_PWD_REQUEST_PATH)
                .handler(accountEndpointHandler::changePassword)
                .handler(rc -> rc.response().end());

        // reject request older than 10 minutes
        final var resetPasswordSettings = new SelfServiceAccountManagementSettings.ResetPasswordSettings();
        resetPasswordSettings.setTokenAge(TOKEN_AGE_IN_SEC);
        resetPasswordSettings.setOldPasswordRequired(true);
        this.domain.getSelfServiceAccountManagementSettings().setResetPassword(resetPasswordSettings);

        when(jwt.getIat()).thenReturn(Instant.now().getEpochSecond());

        testRequest(HttpMethod.POST,
                CHANGE_PWD_REQUEST_PATH,
                req -> {
                    req.headers().set("content-type", "application/json");
                    setBody(req, Map.of("password", "Test123!"));
                },
                400,
                "Bad Request", null);

        verify(accountService, never()).resetPassword(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldChangePassword_WithOldPassword() throws Exception {
        router.route(CHANGE_PWD_REQUEST_PATH)
                .handler(accountEndpointHandler::changePassword)
                .handler(rc -> rc.response().end());

        // reject request older than 10 minutes
        final var resetPasswordSettings = new SelfServiceAccountManagementSettings.ResetPasswordSettings();
        resetPasswordSettings.setTokenAge(TOKEN_AGE_IN_SEC);
        resetPasswordSettings.setOldPasswordRequired(true);
        this.domain.getSelfServiceAccountManagementSettings().setResetPassword(resetPasswordSettings);

        when(jwt.getIat()).thenReturn(Instant.now().getEpochSecond());

        when(accountService.resetPassword(any(), any(), any(), any(), any())).thenReturn(Single.just(new ResetPasswordResponse()));

        testRequest(HttpMethod.POST,
                CHANGE_PWD_REQUEST_PATH,
                req -> {
                    req.headers().set("content-type", "application/json");
                    setBody(req, Map.of("password", "Test123!", "oldPassword", "NewTest1234!"));
                },
                204,
                "No Content", null);

        verify(accountService).resetPassword(any(),
                any(),
                eq("Test123!"),
                any(),
                argThat(opt -> opt.get().equals("NewTest1234!")));
    }

    @Test
    public void shouldNotRedirectToChangePassword_JwtTooOld() throws Exception {
        router.route(CHANGE_PWD_REQUEST_PATH)
                .handler(accountEndpointHandler::redirectForgotPassword)
                .handler(rc -> rc.response().end());

        // reject request older than 10 minutes
        final var resetPasswordSettings = new SelfServiceAccountManagementSettings.ResetPasswordSettings();
        resetPasswordSettings.setTokenAge(TOKEN_AGE_IN_SEC);
        this.domain.getSelfServiceAccountManagementSettings().setResetPassword(resetPasswordSettings);

        when(jwt.getIat()).thenReturn(Instant.now().minus(TOKEN_AGE_IN_SEC + 1, ChronoUnit.SECONDS).getEpochSecond());

        testRequest(HttpMethod.GET,
                CHANGE_PWD_REQUEST_PATH,
                401,
                "Unauthorized");
    }

    @Test
    public void shouldRedirectToChangePassword_TokenNotExpired() throws Exception {
        router.route(CHANGE_PWD_REQUEST_PATH)
                .handler(accountEndpointHandler::redirectForgotPassword)
                .handler(rc -> rc.response().end());

        // reject request older than 10 minutes
        final var resetPasswordSettings = new SelfServiceAccountManagementSettings.ResetPasswordSettings();
        resetPasswordSettings.setTokenAge(TOKEN_AGE_IN_SEC);
        this.domain.getSelfServiceAccountManagementSettings().setResetPassword(resetPasswordSettings);

        when(jwt.getIat()).thenReturn(Instant.now().minus(TOKEN_AGE_IN_SEC - 1, ChronoUnit.SECONDS).getEpochSecond());

        testRequest(HttpMethod.GET,
                CHANGE_PWD_REQUEST_PATH,
                302,
                "Found");
    }

    private void setBody(HttpClientRequest req, Object body) {
        try {
            req.send(Buffer.buffer(mapper.writeValueAsString(body)));
        } catch (Exception e) {
            fail(e);
        }
    }

    public void shouldUpdateUser() throws Exception {
        final var existingUser = new User();
        existingUser.setId(UUID.randomUUID().toString());
        existingUser.setFirstName("Firstname");
        existingUser.setLastName("lastname");
        existingUser.setMiddleName("middle name");
        existingUser.setMiddleName("nickname");
        existingUser.setProfile("http://profile");
        existingUser.setProfile("http://picture");
        existingUser.getAdditionalInformation().put(StandardClaims.PICTURE, existingUser.getPicture());
        existingUser.setWebsite("http://site");
        existingUser.setEmail("user@acme.com");
        existingUser.setBirthdate("01/01/1970");
        existingUser.setZoneInfo("UTC");
        existingUser.setLocale("fr");
        existingUser.setPhoneNumber("0606060606");
        initUserContextKeyValue(existingUser);

        JsonObject userUpdate = new JsonObject();
        userUpdate.put(GIVEN_NAME, "Firstname updated");
        userUpdate.put(FAMILY_NAME, "Lastname updated");
        userUpdate.put(MIDDLE_NAME, "Middle name updated");
        userUpdate.put(NICKNAME, "nickname updated");
        userUpdate.put(PROFILE, "https://updated/profile");
        userUpdate.put(PICTURE, "https://updated/picture");
        userUpdate.put(WEBSITE, "https://updated/site");
        userUpdate.put(EMAIL, "updated@acme.com");
        userUpdate.put(BIRTHDATE, "01/01/1971");
        userUpdate.put(ZONEINFO, "GMT+1");
        userUpdate.put(LOCALE, "fr");
        userUpdate.put(PHONE_NUMBER, "0707070707");

        when(accountService.update(any())).thenReturn(Single.just(new User()));

        router.route(REQUEST_PATH)
                .handler(accountEndpointHandler::updateProfile)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.PUT,
                REQUEST_PATH,
                req -> req
                        .putHeader("content-type", "application/json")
                        .send(Json.encode(userUpdate)),
                res -> {
                    res.bodyHandler(h -> {
                        String body = h.toString();
                        var jsonObject = (JsonObject)Json.decodeValue(body);
                        assertNotNull(jsonObject);
                    });
                },
                200,
                "OK", null);

        verify(accountService).update(argThat(args -> {
            var equals = true;
            equals &= (args.getId().equals(existingUser.getId()));
            equals &= (args.getFirstName().equals(userUpdate.getString(GIVEN_NAME)));
            equals &= (args.getLastName().equals(userUpdate.getString(FAMILY_NAME)));
            equals &= (args.getMiddleName().equals(userUpdate.getString(MIDDLE_NAME)));
            equals &= (args.getNickName().equals(userUpdate.getString(NICKNAME)));
            equals &= (args.getProfile().equals(userUpdate.getString(PROFILE)));
            equals &= (args.getPicture().equals(userUpdate.getString(PICTURE)));
            equals &= (args.getWebsite().equals(userUpdate.getString(WEBSITE)));
            equals &= (args.getEmail().equals(userUpdate.getString(EMAIL)));
            equals &= (args.getBirthdate().equals(userUpdate.getString(BIRTHDATE)));
            equals &= (args.getZoneInfo().equals(userUpdate.getString(ZONEINFO)));
            equals &= (args.getLocale().equals(userUpdate.getString(LOCALE)));
            equals &= (args.getPhoneNumber().equals(userUpdate.getString(PHONE_NUMBER)));
            equals &= (args.getDisplayName().contains(userUpdate.getString(GIVEN_NAME)));
            equals &= (args.getDisplayName().contains(userUpdate.getString(FAMILY_NAME)));
            return equals;
        }));
    }

    @Test
    public void shouldUpdateUser_MissingFirstName() throws Exception {
        final var existingUser = new User();
        existingUser.setId(UUID.randomUUID().toString());
        existingUser.setFirstName("Firstname");
        existingUser.setLastName("lastname");
        existingUser.setMiddleName("middle name");
        existingUser.setMiddleName("nickname");
        existingUser.setProfile("http://profile");
        existingUser.setProfile("http://picture");
        existingUser.getAdditionalInformation().put(StandardClaims.PICTURE, existingUser.getPicture());
        existingUser.setWebsite("http://site");
        existingUser.setEmail("user@acme.com");
        existingUser.setBirthdate("01/01/1970");
        existingUser.setZoneInfo("UTC");
        existingUser.setLocale("fr");
        existingUser.setPhoneNumber("0606060606");
        initUserContextKeyValue(existingUser);

        JsonObject userUpdate = new JsonObject();
        userUpdate.put(GIVEN_NAME, "Firstname updated");
        userUpdate.put(MIDDLE_NAME, "Middle name updated");
        userUpdate.put(NICKNAME, "nickname updated");
        userUpdate.put(PROFILE, "https://updated/profile");
        userUpdate.put(PICTURE, "https://updated/picture");
        userUpdate.put(WEBSITE, "https://updated/site");
        userUpdate.put(EMAIL, "updated@acme.com");
        userUpdate.put(BIRTHDATE, "01/01/1971");
        userUpdate.put(ZONEINFO, "GMT+1");
        userUpdate.put(LOCALE, "fr");
        userUpdate.put(PHONE_NUMBER, "0707070707");

        when(accountService.update(any())).thenReturn(Single.just(new User()));

        router.route(REQUEST_PATH)
                .handler(accountEndpointHandler::updateProfile)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.PUT,
                REQUEST_PATH,
                req -> req
                        .putHeader("content-type", "application/json")
                        .send(Json.encode(userUpdate)),
                res -> {
                    res.bodyHandler(h -> {
                        String body = h.toString();
                        var jsonObject = (JsonObject)Json.decodeValue(body);
                        assertNotNull(jsonObject);
                    });
                },
                200,
                "OK", null);

        verify(accountService).update(argThat(args -> {
            var equals = true;
            equals &= (args.getId().equals(existingUser.getId()));
            equals &= (args.getFirstName().equals(userUpdate.getString(GIVEN_NAME)));
            equals &= (args.getLastName() == null);
            equals &= (args.getMiddleName().equals(userUpdate.getString(MIDDLE_NAME)));
            equals &= (args.getNickName().equals(userUpdate.getString(NICKNAME)));
            equals &= (args.getProfile().equals(userUpdate.getString(PROFILE)));
            equals &= (args.getPicture().equals(userUpdate.getString(PICTURE)));
            equals &= (args.getWebsite().equals(userUpdate.getString(WEBSITE)));
            equals &= (args.getEmail().equals(userUpdate.getString(EMAIL)));
            equals &= (args.getBirthdate().equals(userUpdate.getString(BIRTHDATE)));
            equals &= (args.getZoneInfo().equals(userUpdate.getString(ZONEINFO)));
            equals &= (args.getLocale().equals(userUpdate.getString(LOCALE)));
            equals &= (args.getPhoneNumber().equals(userUpdate.getString(PHONE_NUMBER)));
            equals &= (args.getDisplayName().equals(userUpdate.getString(GIVEN_NAME)));
            return equals;
        }));
    }

    @Test
    public void shouldUpdateUser_DisplayNameNotGenerated() throws Exception {
        final var existingUser = new User();
        existingUser.setId(UUID.randomUUID().toString());
        existingUser.setFirstName("Firstname");
        existingUser.setLastName("lastname");
        existingUser.setMiddleName("middle name");
        existingUser.setMiddleName("nickname");
        existingUser.setProfile("http://profile");
        existingUser.setProfile("http://picture");
        existingUser.getAdditionalInformation().put(StandardClaims.PICTURE, existingUser.getPicture());
        existingUser.setWebsite("http://site");
        existingUser.setEmail("user@acme.com");
        existingUser.setBirthdate("01/01/1970");
        existingUser.setZoneInfo("UTC");
        existingUser.setLocale("fr");
        existingUser.setPhoneNumber("0606060606");
        existingUser.setDisplayName("ExplicitlySetDN");
        initUserContextKeyValue(existingUser);

        JsonObject userUpdate = new JsonObject();
        userUpdate.put(GIVEN_NAME, "Firstname updated");
        userUpdate.put(FAMILY_NAME, "Lastname updated");
        userUpdate.put(MIDDLE_NAME, "Middle name updated");
        userUpdate.put(NICKNAME, "nickname updated");
        userUpdate.put(PROFILE, "https://updated/profile");
        userUpdate.put(PICTURE, "https://updated/picture");
        userUpdate.put(WEBSITE, "https://updated/site");
        userUpdate.put(EMAIL, "updated@acme.com");
        userUpdate.put(BIRTHDATE, "01/01/1971");
        userUpdate.put(ZONEINFO, "GMT+1");
        userUpdate.put(LOCALE, "fr");
        userUpdate.put(PHONE_NUMBER, "0707070707");

        when(accountService.update(any())).thenReturn(Single.just(new User()));

        router.route(REQUEST_PATH)
                .handler(accountEndpointHandler::updateProfile)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.PUT,
                REQUEST_PATH,
                req -> req
                        .putHeader("content-type", "application/json")
                        .send(Json.encode(userUpdate)),
                res -> {
                    res.bodyHandler(h -> {
                        String body = h.toString();
                        var jsonObject = (JsonObject)Json.decodeValue(body);
                        assertNotNull(jsonObject);
                    });
                },
                200,
                "OK", null);

        verify(accountService).update(argThat(args -> {
            var equals = true;
            equals &= (args.getId().equals(existingUser.getId()));
            equals &= (args.getFirstName().equals(userUpdate.getString(GIVEN_NAME)));
            equals &= (args.getLastName().equals(userUpdate.getString(FAMILY_NAME)));
            equals &= (args.getMiddleName().equals(userUpdate.getString(MIDDLE_NAME)));
            equals &= (args.getNickName().equals(userUpdate.getString(NICKNAME)));
            equals &= (args.getProfile().equals(userUpdate.getString(PROFILE)));
            equals &= (args.getPicture().equals(userUpdate.getString(PICTURE)));
            equals &= (args.getWebsite().equals(userUpdate.getString(WEBSITE)));
            equals &= (args.getEmail().equals(userUpdate.getString(EMAIL)));
            equals &= (args.getBirthdate().equals(userUpdate.getString(BIRTHDATE)));
            equals &= (args.getZoneInfo().equals(userUpdate.getString(ZONEINFO)));
            equals &= (args.getLocale().equals(userUpdate.getString(LOCALE)));
            equals &= (args.getPhoneNumber().equals(userUpdate.getString(PHONE_NUMBER)));
            equals &= (args.getDisplayName().equals(existingUser.getDisplayName()));
            return equals;
        }));
    }

    @Test
    public void should_execute_update_username() throws Exception {
        final var input = new UpdateUsername();
        input.setUsername(UUID.randomUUID().toString());

        final var updatedUser = new User();
        updatedUser.setId(input.getUsername());
        when(accountService.updateUsername(any(), any(), any())).thenReturn(Single.just(new User()));

        router.route(USERNAME_REQUEST_PATH)
                .handler(accountEndpointHandler::updateUsername)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.PUT,
                USERNAME_REQUEST_PATH,
                req -> req
                        .putHeader("content-type", "application/json")
                        .send(Json.encode(input)),
                res -> {
                    res.bodyHandler(h -> {
                        String body = h.toString();
                        var jsonObject = (JsonObject)Json.decodeValue(body);
                        assertNotNull(jsonObject);
                    });
                },
                200,
                "OK", null);

        verify(accountService).updateUsername(any(), any(), any());
    }

    @Test
    public void should_propagate_update_username_error() throws Exception {
        final var input = new UpdateUsername();
        input.setUsername(UUID.randomUUID().toString());

        final var updatedUser = new User();
        updatedUser.setId(input.getUsername());
        when(accountService.updateUsername(any(), any(), any())).thenReturn(Single.error(new InvalidUserException("")));

        router.route(USERNAME_REQUEST_PATH)
                .handler(accountEndpointHandler::updateUsername)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.PUT,
                USERNAME_REQUEST_PATH,
                req -> req
                        .putHeader("content-type", "application/json")
                        .send(Json.encode(input)),
                res -> {
                    res.bodyHandler(h -> {
                        String body = h.toString();
                        var jsonObject = (JsonObject)Json.decodeValue(body);
                        assertNotNull(jsonObject);
                    });
                },
                400,
                "Bad Request", null);

        verify(accountService).updateUsername(any(), any(), any());
    }
}
