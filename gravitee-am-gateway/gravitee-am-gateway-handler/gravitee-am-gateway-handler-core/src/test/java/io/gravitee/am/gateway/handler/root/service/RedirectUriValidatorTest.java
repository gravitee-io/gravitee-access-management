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
package io.gravitee.am.gateway.handler.root.service;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.exception.oauth2.RedirectMismatchException;
import io.gravitee.am.common.jwt.TokenPurpose;
import io.gravitee.am.model.oidc.Client;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BiConsumer;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class RedirectUriValidatorTest {

    private final RedirectUriValidator validator = new RedirectUriValidator();

    private final static String REGISTERED_URI_1 = "http://a.localhost";
    private final static String REGISTERED_URI_2 = "http://b.localhost";
    private final static String UNREGISTERED_URI = "http://example.com";


    private final BiConsumer<String, List<String>> strictUriChecker = (uri, registeredUris) -> {
        if (!registeredUris.contains(uri)) {
            throw new ExceptionFromUriChecker();
        }
    };

    // allows asserting that it's not the validator throwing, but the checker delegate
    static class ExceptionFromUriChecker extends RuntimeException {}


    abstract class SharedCases {
        abstract Client getClient();


        @Test
        void noOperation_redirectUriNotRegistered_shouldFail() {
            assertThatThrownBy(()->validator.validate(getClient(), UNREGISTERED_URI, strictUriChecker))
                    .isInstanceOf(ExceptionFromUriChecker.class);
        }

        @Test
        void operationOptionalRedirect_redirectUriNotRegistered_shouldFail() {
            assertThatThrownBy(()->validator.validate(getClient(), UNREGISTERED_URI, TokenPurpose.RESET_PASSWORD, strictUriChecker))
                    .isInstanceOf(ExceptionFromUriChecker.class);
        }

        @Test
        void operationOptionalRedirect_redirectUriRegistered_ok_resetPassword() {
            assertThatCode(()->validator.validate(getClient(), REGISTERED_URI_1, TokenPurpose.RESET_PASSWORD, strictUriChecker))
                    .doesNotThrowAnyException();
        }

        @Test
        void operationOptionalRedirect_noRedirectGiven_ok_resetPassword() {
            assertThatCode(()->validator.validate(getClient(), null, TokenPurpose.RESET_PASSWORD, strictUriChecker))
                    .doesNotThrowAnyException();
        }

        @Test
        void operationOptionalRedirect_redirectUriRegistered_ok_registrationConfirmation() {
            assertThatCode(()->validator.validate(getClient(), REGISTERED_URI_1, TokenPurpose.REGISTRATION_CONFIRMATION, strictUriChecker))
                    .doesNotThrowAnyException();
        }

        @Test
        void operationOptionalRedirect_noRedirectGiven_ok_registrationConfirmation() {
            assertThatCode(()->validator.validate(getClient(), null, TokenPurpose.REGISTRATION_CONFIRMATION, strictUriChecker))
                    .doesNotThrowAnyException();
        }

        @Test
        void operationRequiredRedirect_redirectUriNotRegistered_shouldFail() {
            assertThatThrownBy(()->validator.validate(getClient(), UNREGISTERED_URI, TokenPurpose.UNSPECIFIED, strictUriChecker))
                    .isInstanceOf(ExceptionFromUriChecker.class);
        }

        @Test
        void operationRequiredRedirect_redirectUriWithUserInfo_shouldFail() {
            assertThatThrownBy(()->validator.validate(getClient(), "http://user@google.com", TokenPurpose.UNSPECIFIED,strictUriChecker))
                    .isInstanceOf(RedirectMismatchException.class);
        }

    }

    @Nested
    class ClientWithSingleRedirect extends SharedCases {
        @Getter
        private Client client;

        @BeforeEach
        public void setup() {
            client = new Client();
            client.setRedirectUris(List.of(REGISTERED_URI_1));
        }
        @Test
        void noOperation_noRedirectGiven_ok() {
            assertThatCode(()->validator.validate(client, null, strictUriChecker))
                    .doesNotThrowAnyException();
        }


    }

    @Nested
    class ClientWithMultipleRedirects extends SharedCases {
        @Getter
        private Client client;
        @BeforeEach
        public void setup() {
            client = new Client();
            client.setRedirectUris(List.of(REGISTERED_URI_1, REGISTERED_URI_2));
        }
        @Test
        void noOperation_noRedirectGiven_fail() {
            assertThatThrownBy(()->validator.validate(client, null, strictUriChecker))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

}