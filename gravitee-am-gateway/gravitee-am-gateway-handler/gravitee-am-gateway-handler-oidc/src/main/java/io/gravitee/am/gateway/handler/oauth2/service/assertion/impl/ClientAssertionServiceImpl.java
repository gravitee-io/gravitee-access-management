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

import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.ClientAssertionService;
import io.gravitee.am.gateway.handler.oauth2.service.assertion.ClientAssertionValidator;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Maybe;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dispatches a client assertion to the {@link ClientAssertionValidator} that
 * handles the supplied {@code client_assertion_type}. Per-type logic lives in
 * the individual validators; this class only owns the registry and the
 * "unsupported assertion type" error path.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7521#section-4.2">RFC 7521 §4.2</a>
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#ClientAuthentication">OIDC Client Authentication</a>
 */
public class ClientAssertionServiceImpl implements ClientAssertionService {

    private final Map<String, ClientAssertionValidator> validators;

    public ClientAssertionServiceImpl(List<ClientAssertionValidator> validators) {
        this.validators = validators.stream()
                .collect(Collectors.toUnmodifiableMap(ClientAssertionValidator::assertionType, Function.identity()));
    }

    @Override
    public Maybe<Client> assertClient(String assertionType, String assertion, String basePath, String clientIdHint) {
        if (assertionType == null || assertionType.isEmpty()) {
            return Maybe.error(unsupportedAssertionType());
        }
        ClientAssertionValidator validator = validators.get(assertionType);
        if (validator == null) {
            return Maybe.error(unsupportedAssertionType());
        }
        return validator.validate(assertion, basePath, clientIdHint);
    }

    private static InvalidClientException unsupportedAssertionType() {
        return new InvalidClientException("Unknown or unsupported assertion_type");
    }
}
