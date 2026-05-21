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
package io.gravitee.am.gateway.handler.oauth2.service.assertion.impl;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.reactivex.rxjava3.core.Maybe;

import java.text.ParseException;

/**
 * Shared helpers for client assertion validators. Kept package-private so the
 * dispatcher and per-assertion-type validators can compose with it without
 * exposing internals more broadly.
 */
final class JwtAssertionSupport {

    static final InvalidClientException NOT_VALID = new InvalidClientException("assertion is not valid");

    private JwtAssertionSupport() {
    }

    static Maybe<JWT> parseJwt(String assertion) {
        try {
            return Maybe.just(JWTParser.parse(assertion));
        } catch (ParseException pe) {
            return Maybe.error(NOT_VALID);
        }
    }

    static InvalidClientException unableToValidateClient() {
        return new InvalidClientException("Unable to validate client, assertion signature is not valid.");
    }
}
