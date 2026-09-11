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
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.common.utils.SecureRandomString;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.service.el.ExecutionContextTokenEnhancer;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.IdJagTarget;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.idjag.IdJag;
import io.gravitee.am.gateway.handler.oidc.service.idjag.IdJagService;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.rxjava3.core.Single;
import lombok.CustomLog;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@CustomLog
public class IdJagServiceImpl implements IdJagService {

    private static final Set<String> RESERVED_CLAIMS = Set.of(Claims.ISS, Claims.AUD, Claims.CLIENT_ID, Claims.JTI, Claims.EXP, Claims.IAT);

    @Autowired
    private JWTService jwtService;

    @Autowired
    private OpenIDDiscoveryService openIDDiscoveryService;

    @Override
    public Single<IdJag> create(OAuth2Request oAuth2Request, Client client, User user, ExecutionContext executionContext) {
        return Single.fromCallable(() -> createAssertion(oAuth2Request, client, user, executionContext))
                .flatMap(assertion -> jwtService.encode(assertion, client)
                        .map(value -> new IdJag(value, assertion.getJti(), assertion.getExp() - assertion.getIat(),
                                Objects.toString(assertion.get(Claims.SCOPE), null))));
    }

    private JWT createAssertion(OAuth2Request oAuth2Request, Client client, User user, ExecutionContext executionContext) {
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

        List<TokenClaim> customClaims = idJagCustomClaims(client);
        if (customClaims.stream().noneMatch(claim -> Claims.AUD_SUB.equals(claim.getClaimName()))) {
            audSub(target, executionContext).ifPresent(audSub -> assertion.put(Claims.AUD_SUB, audSub));
        }
        new ExecutionContextTokenEnhancer().enhanceToken(assertion, TokenTypeHint.ID_JAG, customClaims, executionContext);
        if (assertion.get(Claims.SCOPE) instanceof Collection<?> scopes) {
            assertion.put(Claims.SCOPE, scopes.stream().map(String::valueOf).collect(Collectors.joining(" ")));
        }

        return assertion;
    }

    private static List<TokenClaim> idJagCustomClaims(Client client) {
        if (client.getTokenCustomClaims() == null) {
            return List.of();
        }
        return client.getTokenCustomClaims().stream()
                .filter(claim -> TokenTypeHint.ID_JAG.equals(claim.getTokenType()))
                .filter(claim -> {
                    boolean reserved = RESERVED_CLAIMS.contains(claim.getClaimName());
                    if (reserved) {
                        log.warn("Ignoring reserved ID-JAG custom claim, claim={} clientId={}", claim.getClaimName(), client.getClientId());
                    }
                    return !reserved;
                })
                .toList();
    }

    private static Optional<String> audSub(IdJagTarget target, ExecutionContext executionContext) {
        if (StringUtils.isBlank(target.audSubMapping())) {
            return Optional.empty();
        }
        Object value;
        try {
            value = executionContext.getTemplateEngine().getValue(target.audSubMapping(), Object.class);
        } catch (Exception e) {
            log.warn("aud_sub mapping could not be evaluated, audience={}", target.audience());
            throw new InvalidGrantException("The aud_sub mapping could not be evaluated for audience: " + target.audience());
        }
        if (ObjectUtils.isEmpty(value)) {
            return Optional.empty();
        }
        if (value instanceof Collection<?> || value instanceof Map<?, ?> || value.getClass().isArray()) {
            log.warn("aud_sub mapping produced several values, audience={}", target.audience());
            throw new InvalidGrantException("The aud_sub mapping did not produce a single value for audience: " + target.audience());
        }
        return Optional.of(value.toString()).filter(StringUtils::isNotBlank);
    }

    private static long expiration(OAuth2Request oAuth2Request, Client client, long issuedAt) {
        long expiration = issuedAt + client.getIdJagValiditySeconds();
        if (oAuth2Request.getExchangeExpiration() != null) {
            expiration = Math.min(expiration, oAuth2Request.getExchangeExpiration().toInstant().getEpochSecond());
        }
        return expiration;
    }
}
