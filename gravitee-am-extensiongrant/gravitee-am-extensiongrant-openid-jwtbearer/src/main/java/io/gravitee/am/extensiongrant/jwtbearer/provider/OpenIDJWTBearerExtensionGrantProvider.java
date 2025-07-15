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
package io.gravitee.am.extensiongrant.jwtbearer.provider;

import io.gravitee.am.common.exception.jwt.ExpiredJWTException;
import io.gravitee.am.common.exception.jwt.MalformedJWTException;
import io.gravitee.am.common.exception.jwt.PrematureJWTException;
import io.gravitee.am.common.exception.jwt.SignatureException;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.extensiongrant.jwtbearer.OpenIDJWTBearerExtensionGrantConfiguration;
import io.gravitee.am.extensiongrant.jwtbearer.parser.JWKSJwtParser;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.repository.oauth2.model.request.TokenRequest;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.gravitee.am.common.jwt.Claims.GIO_INTERNAL_SUB;
import static io.gravitee.am.common.jwt.Claims.SUB;

@Slf4j
public class OpenIDJWTBearerExtensionGrantProvider implements ExtensionGrantProvider {

    private static final String ASSERTION_QUERY_PARAM = "assertion";

    private final JWKSJwtParser jwtParser;
    private final OpenIDJWTBearerExtensionGrantConfiguration configuration;

    public OpenIDJWTBearerExtensionGrantProvider(OpenIDJWTBearerExtensionGrantConfiguration configuration) throws Exception {
        this.configuration = configuration;
        jwtParser = new JWKSJwtParser(configuration.getJwksUri());
    }

    @Override
    public Maybe<User> grant(TokenRequest tokenRequest) throws InvalidGrantException {
        String assertion = tokenRequest.getRequestParameters().get(ASSERTION_QUERY_PARAM);

        if (assertion == null) {
            throw new InvalidGrantException("Assertion value is missing");
        }
        return Observable.fromCallable(() -> {
            try {
                JWT jwt = jwtParser.parse(assertion);
                return createUser(jwt);
            } catch (MalformedJWTException | ExpiredJWTException | PrematureJWTException | SignatureException ex) {
                log.debug(ex.getMessage(), ex.getCause());
                throw new InvalidGrantException(ex.getMessage(), ex);
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex.getCause());
                throw new InvalidGrantException(ex.getMessage(), ex);
            }
        }).firstElement();
    }

    private User createUser(JWT jwt) {
        final String sub = jwt.getSub();
        final String username = jwt.containsKey(StandardClaims.PREFERRED_USERNAME) ?
                jwt.get(StandardClaims.PREFERRED_USERNAME).toString() : sub;
        DefaultUser user = new DefaultUser(username);
        user.setId(sub);
        // set claims
        Map<String, Object> additionalInformation = new HashMap<>();
        // add sub required claim
        additionalInformation.put(SUB, sub);
        if (jwt.getInternalSub() != null) {
            additionalInformation.put(GIO_INTERNAL_SUB, jwt.getInternalSub());
        }
        List<Map<String, String>> claimsMapper = configuration.getClaimsMapper();
        if (claimsMapper != null && !claimsMapper.isEmpty()) {
            claimsMapper.forEach(claimMapper -> {
                String assertionClaim = claimMapper.get("assertion_claim");
                String tokenClaim = claimMapper.get("token_claim");
                if (jwt.containsKey(assertionClaim)) {
                    additionalInformation.put(tokenClaim, jwt.get(assertionClaim));
                }
            });
        }
        user.setAdditionalInformation(additionalInformation);
        return user;
    }

}
