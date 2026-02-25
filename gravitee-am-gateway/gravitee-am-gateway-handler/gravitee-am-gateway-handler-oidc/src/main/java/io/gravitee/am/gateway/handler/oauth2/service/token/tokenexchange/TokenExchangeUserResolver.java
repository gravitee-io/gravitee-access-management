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
package io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange;

import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.model.TrustedIssuer;
import io.gravitee.am.model.User;
import io.reactivex.rxjava3.core.Single;

import java.util.Optional;

/**
 * Resolves an external JWT subject to a domain user when the trusted issuer has user binding enabled.
 * When user binding is disabled or no user matches, returns empty; the caller uses a virtual user.
 * User lookup is scoped by the provided {@link UserGatewayService} (domain-scoped at the call site).
 *
 * @see TrustedIssuer#isUserBindingEnabled()
 * @author GraviteeSource Team
 */
public interface TokenExchangeUserResolver {

    /**
     * Resolve the subject token to a domain user when the trusted issuer has user binding enabled.
     * Builds EL context with token claims, evaluates each criterion expression, and looks up users by criteria.
     *
     * @param subjectToken the validated subject token (external JWT)
     * @param trustedIssuer the trusted issuer config that validated the token (may have user binding criteria)
     * @param userGatewayService the user service for the domain (already domain-scoped by the caller)
     * @return empty if user binding is disabled or no criteria; otherwise 0 users -> error, 1 user -> Optional.of(user), >1 -> error
     */
    Single<Optional<User>> resolve(ValidatedToken subjectToken,
                                   TrustedIssuer trustedIssuer,
                                   UserGatewayService userGatewayService);
}
