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
package io.gravitee.am.gateway.handler.oidc.service.idjag.impl;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.jwt.JwtType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.IdJagTarget;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.idjag.IdJag;
import io.gravitee.am.gateway.handler.oidc.service.idjag.IdJagService;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Set;

public class IdJagServiceImpl implements IdJagService {

    @Autowired
    private JWTService jwtService;

    @Autowired
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Override
    public Single<IdJag> create(OAuth2Request oAuth2Request, Client client, User user) {
        return Single.fromCallable(() -> createAssertion(oAuth2Request, client, user))
                .flatMap(assertion -> jwtService.encode(assertion, client)
                        .map(value -> new IdJag(value, assertion.getJti(), assertion.getExp() - assertion.getIat(),
                                (String) assertion.get(Claims.SCOPE))));
    }

    private JWT createAssertion(OAuth2Request oAuth2Request, Client client, User user) {
        IdJagTarget target = oAuth2Request.getIdJagTarget();
        long issuedAt = Instant.now().getEpochSecond();

        JWT assertion = new JWT();
        assertion.setType(JwtType.ID_JAG);
        assertion.setIss(openIDDiscoveryService.getIssuer(oAuth2Request.getOrigin()));
        assertion.setSub(user.getId());
        assertion.setAud(target.audience());
        assertion.put(Claims.CLIENT_ID, target.clientId());
        assertion.put(Parameters.RESOURCE, target.resource());
        Set<String> grantedScopes = oAuth2Request.getScopes();
        if (grantedScopes != null && !grantedScopes.isEmpty()) {
            target.requireMapped(grantedScopes);
            assertion.put(Claims.SCOPE, target.partnerScope(grantedScopes));
        }
        assertion.setJti(SecureRandomString.generate());
        assertion.setIat(issuedAt);
        assertion.setExp(expiration(oAuth2Request, client, issuedAt));

        if (oAuth2Request.isDelegation() && oAuth2Request.getActClaim() != null) {
            assertion.put(Claims.ACT, oAuth2Request.getActClaim());
        }

        return assertion;
    }

    private static long expiration(OAuth2Request oAuth2Request, Client client, long issuedAt) {
        long expiration = issuedAt + client.getIdJagValiditySeconds();
        if (oAuth2Request.getExchangeExpiration() != null) {
            expiration = Math.min(expiration, oAuth2Request.getExchangeExpiration().toInstant().getEpochSecond());
        }
        return expiration;
    }
}
