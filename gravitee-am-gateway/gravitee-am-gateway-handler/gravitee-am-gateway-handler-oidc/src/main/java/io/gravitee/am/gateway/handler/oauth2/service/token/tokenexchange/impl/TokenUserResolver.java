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

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenExchangeUserResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ValidatedToken;
import io.gravitee.am.model.User;
import io.reactivex.rxjava3.core.Maybe;
import lombok.RequiredArgsConstructor;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@CustomLog
public class TokenUserResolver implements TokenExchangeUserResolver {
    private final SubjectManager subjectManager;
    private final UserGatewayService userGatewayService;

    @Override
    public Maybe<User> resolve(ValidatedToken subjectToken) {
        return findUser(subjectToken)
                .switchIfEmpty(createSyntheticUser(subjectToken));
    }

    private Maybe<User> createSyntheticUser(ValidatedToken subjectToken) {
        String subject = subjectToken.getSubject();
        String username = subject;
        Object preferredUsername = subjectToken.getClaim(StandardClaims.PREFERRED_USERNAME);
        if (preferredUsername != null) {
            username = preferredUsername.toString();
        }

        User user = new User();
        user.setId(subject);
        user.setUsername(username);

        Map<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put(Claims.SUB, subject);

        Object gioInternalSub = subjectToken.getClaim(Claims.GIO_INTERNAL_SUB);
        if (gioInternalSub instanceof String gioInternalSubValue) {
            if (subjectManager.hasValidInternalSub(gioInternalSubValue)) {
                user.setSource(subjectManager.extractSourceId(gioInternalSubValue));
                user.setExternalId(subjectManager.extractUserId(gioInternalSubValue));
            }
        }
        user.setAdditionalInformation(additionalInformation);

        return Maybe.just(user);
    }

    private Maybe<User> findUser(ValidatedToken subjectToken) {
        if (StringUtils.isBlank(subjectToken.getSubject())) {
            return Maybe.empty();
        }

        JWT subjectJwt = new JWT();
        subjectJwt.setSub(subjectToken.getSubject());
        Object internalSub = subjectToken.getClaim(Claims.GIO_INTERNAL_SUB);
        if (internalSub instanceof String internalSubValue) {
            subjectJwt.setInternalSub(internalSubValue);
        }

        return subjectManager.findUserBySub(subjectJwt)
                .flatMapSingle(userGatewayService::enhance)
                .onErrorResumeNext(error -> {
                    log.debug("Unable to resolve local user profile for subject '{}'", subjectToken.getSubject(), error);
                    return Maybe.empty();
                });
    }

}
