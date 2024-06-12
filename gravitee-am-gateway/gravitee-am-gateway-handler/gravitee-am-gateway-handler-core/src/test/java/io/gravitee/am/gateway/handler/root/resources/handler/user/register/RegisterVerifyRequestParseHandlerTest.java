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
package io.gravitee.am.gateway.handler.root.resources.handler.user.register;

import io.gravitee.am.common.exception.jwt.ExpiredJWTException;
import io.gravitee.am.common.exception.jwt.JWTException;
import io.gravitee.am.gateway.handler.root.resources.handler.dummies.SpyRoutingContext;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.gateway.handler.root.service.user.model.UserToken;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.exception.UserAlreadyVerifiedException;
import io.reactivex.rxjava3.core.Maybe;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.stream.Stream;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.ERROR_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.SUCCESS_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.TOKEN_PARAM_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.WARNING_PARAM_KEY;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.root.resources.handler.user.register.RegisterVerifyRequestParseHandler.INVALID_TOKEN;
import static io.gravitee.am.gateway.handler.root.resources.handler.user.register.RegisterVerifyRequestParseHandler.REGISTRATION_VERIFY_LINK_EXPIRED;
import static io.gravitee.am.gateway.handler.root.resources.handler.user.register.RegisterVerifyRequestParseHandler.UNEXPECTED_ERROR;
import static io.gravitee.am.model.Template.REGISTRATION_VERIFY;
import static io.gravitee.gateway.api.http.HttpHeaderNames.LOCATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class RegisterVerifyRequestParseHandlerTest {

    @Mock
    private UserService userService;
    @Mock
    private Domain domain;

    private SpyRoutingContext context;
    private RegisterVerifyRequestParseHandler registerVerifyRequestParseHandler;

    @BeforeEach
    public void setUp() {
        registerVerifyRequestParseHandler = new RegisterVerifyRequestParseHandler(domain, userService);
        context = new SpyRoutingContext("/verifyRegistration");
        context.put(CONTEXT_PATH, "");
    }

    @Test
    @DisplayName("Must continue success is present")
    public void must_continue_success_is_present() {
        context.request().params().add(SUCCESS_PARAM_KEY, "success");
        registerVerifyRequestParseHandler.handle(context);

        assertTrue(context.verifyNext(1));
    }

    @Test
    @DisplayName("Must continue warning is present with no token")
    public void must_continue_warn_is_present_with_no_token() {
        context.request().params().add(WARNING_PARAM_KEY, "warning");
        registerVerifyRequestParseHandler.handle(context);

        assertTrue(context.verifyNext(1));
    }

    @Test
    @DisplayName("Must continue error is present")
    public void must_continue_error_is_present_with_no_token() {
        context.request().params().add(ERROR_PARAM_KEY, "error");
        registerVerifyRequestParseHandler.handle(context);

        assertTrue(context.verifyNext(1));
    }


    @Test
    @DisplayName("Must redirect token is null")
    public void must_redirect_token_is_null() {
        registerVerifyRequestParseHandler.handle(context);

        Awaitility.await().until(() -> context.ended());
        assertEquals(302, context.response().getStatusCode());
        assertEquals("/verifyRegistration?error=token_missing", context.response().headers().get(LOCATION));
    }

    @ParameterizedTest
    @MethodSource("params_that_must_redirect_user_token_is_in_error")
    @DisplayName("Must do next with token error")
    public void must_redirect_user_token_is_in_error(Throwable throwable, String expectedErrorKey) {
        when(userService.confirmVerifyRegistration("someToken")).thenReturn(Maybe.error(throwable));
        context.request().params().add(TOKEN_PARAM_KEY, "someToken");
        registerVerifyRequestParseHandler.handle(context);

        assertEquals(expectedErrorKey, context.get(ERROR_PARAM_KEY));
        assertTrue(context.verifyNext(1));
    }

    private static Stream<Arguments> params_that_must_redirect_user_token_is_in_error() {
        return Stream.of(
                Arguments.of(new ExpiredJWTException("token is expired"), REGISTRATION_VERIFY_LINK_EXPIRED),
                Arguments.of(new UserAlreadyVerifiedException("token is expired"), REGISTRATION_VERIFY_LINK_EXPIRED),
                Arguments.of(new JWTException("invalid_token"), INVALID_TOKEN),
                Arguments.of(new IOException("an unexpected error has occurred"), UNEXPECTED_ERROR)
        );
    }

    @Test
    @DisplayName("Must do next with success")
    public void must_redirect_user_token_is_in_success() {
        final UserToken userToken = new UserToken(new User(), new Client());
        when(userService.confirmVerifyRegistration("someToken")).thenReturn(Maybe.just(userToken));
        context.request().params().add(TOKEN_PARAM_KEY, "someToken");
        registerVerifyRequestParseHandler.handle(context);

        assertTrue(context.verifyNext(1));
        assertNotNull(context.get(USER_CONTEXT_KEY));
        assertNotNull(context.get(CLIENT_CONTEXT_KEY));
    }

    @Test
    @DisplayName("Must redirect user with auto login if activated")
    public void must_redirect_user_to_autologin_url_token_is_in_success() {
        final UserToken userToken = new UserToken(new User(), new Client());
        when(userService.confirmVerifyRegistration("someToken")).thenReturn(Maybe.just(userToken));

        var accountSettings = new AccountSettings();
        accountSettings.setSendVerifyRegistrationAccountEmail(true);
        accountSettings.setAutoLoginAfterRegistration(true);
        accountSettings.setRedirectUriAfterRegistration(REGISTRATION_VERIFY.redirectUri());
        when(domain.getAccountSettings()).thenReturn(accountSettings);

        context.request().params().add(TOKEN_PARAM_KEY, "someToken");
        registerVerifyRequestParseHandler.handle(context);

        Awaitility.await().until(() -> context.ended());
        assertEquals(302, context.response().getStatusCode());
        assertEquals(REGISTRATION_VERIFY.redirectUri(), context.response().headers().get(LOCATION));
    }
}
