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
package io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.impl;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.oauth2.service.binding.UserBindingException;
import io.gravitee.am.gateway.handler.oauth2.service.binding.UserBindingResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenExchangeUserResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ValidatedToken;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserBindingCriterion;
import io.gravitee.am.model.oidc.TrustedDomain;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.List;

/**
 * Resolves an external JWT subject to a domain user using trusted issuer user binding criteria.
 * EL context variable {@code token} holds the subject token claims.
 *
 * @author GraviteeSource Team
 */
public class TrustedIssuerUserResolver implements TokenExchangeUserResolver {

    private final UserBindingResolver userBindingResolver;

    public TrustedIssuerUserResolver(UserGatewayService userGatewayService) {
        this.userBindingResolver = new UserBindingResolver(userGatewayService);
    }

    @Override
    public Maybe<User> resolve(ValidatedToken subjectToken) {
        TrustedDomain trustedDomain = subjectToken.getTrustedDomain();
        if (trustedDomain == null || !trustedDomain.isUserBindingEnabled()) {
            return Maybe.empty();
        }
        List<UserBindingCriterion> criteria = trustedDomain.getUserBindingCriteria();
        if (criteria == null || criteria.isEmpty()) {
            return Maybe.empty();
        }

        return userBindingResolver.resolve(criteria, subjectToken.getClaims())
                .onErrorResumeNext(error -> Single.error(error instanceof UserBindingException
                        ? new InvalidRequestException(error.getMessage())
                        : error))
                .toMaybe();
    }
}
