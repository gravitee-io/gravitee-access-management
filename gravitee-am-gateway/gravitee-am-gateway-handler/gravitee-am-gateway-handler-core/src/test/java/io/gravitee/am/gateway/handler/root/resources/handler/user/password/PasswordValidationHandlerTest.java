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
package io.gravitee.am.gateway.handler.root.resources.handler.user.password;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.common.password.PasswordPolicyManager;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.gateway.handler.root.service.user.model.UserToken;
import io.gravitee.am.model.User;
import io.gravitee.am.service.PasswordService;
import io.gravitee.am.service.validators.password.PasswordSettingsStatus;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.core.http.HttpServerResponse;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
class PasswordValidationHandlerTest {

    @Mock
    private IdentityProviderManager identityProviderManager;
    @Mock
    private PasswordPolicyManager passwordPolicyManager;
    @Mock
    private PasswordService passwordService;
    @Mock
    private UserService userService;

    @ParameterizedTest
    @MethodSource()
    void shouldReturnAppropriateStatusCode(PasswordSettingsStatus pwdSettingsStatus, int statusCode) {
        var handler = new PasswordValidationHandler(passwordService, userService, passwordPolicyManager, identityProviderManager);

        var user = new User();
        user.setId(UUID.randomUUID().toString());
        var userToken = new UserToken(user, null);
        given(userService.verifyToken(any())).willReturn(Maybe.just(userToken));
        given(passwordService.evaluate(any(), any(), any())).willReturn(pwdSettingsStatus);

        var context = mock(RoutingContext.class);
        var request = mock(HttpServerRequest.class);
        given(request.getFormAttribute(ConstantKeys.TOKEN_CONTEXT_KEY)).willReturn("token");
        given(request.getFormAttribute(ConstantKeys.PASSWORD_PARAM_KEY)).willReturn("password");
        var response = mock(HttpServerResponse.class);
        given(response.setStatusCode(anyInt())).willReturn(response);
        given(context.request()).willReturn(request);
        given(context.response()).willReturn(response);

        //when
        handler.handle(context);

        verify(response).setStatusCode(statusCode);
    }

    static Stream<Arguments> shouldReturnAppropriateStatusCode() {
        var pwdStatusOK = PasswordSettingsStatus.builder()
                .includeSpecialCharacters(true)
                .minLength(true)
                .build();

        var pwdStatusKO = PasswordSettingsStatus.builder()
                .includeSpecialCharacters(false)
                .minLength(false)
                .build();

        return Stream.of(arguments(pwdStatusKO, HttpStatusCode.BAD_REQUEST_400), arguments(pwdStatusOK, HttpStatusCode.OK_200));
    }
}
