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
package io.gravitee.am.extensiongrant.crossappaccess.provider;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.extensiongrant.api.ExtensionGrantProvider;
import io.gravitee.am.extensiongrant.api.IdJagAssertions;
import io.gravitee.am.extensiongrant.api.ResolvedEndUser;
import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.trustedissuer.ResolvedTrustedIssuer;
import io.gravitee.am.identityprovider.api.trustedissuer.TrustedIssuerResolver;
import io.gravitee.am.repository.oauth2.model.request.TokenRequest;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.CustomLog;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

@CustomLog
public class CrossAppAccessExtensionGrantProvider implements ExtensionGrantProvider {

    @Autowired
    private TrustedIssuerResolver trustedIssuerResolver;

    @Override
    public boolean supports(TokenRequest tokenRequest) {
        return IdJagAssertions.isIdJag(tokenRequest);
    }

    @Override
    public Maybe<User> grant(TokenRequest tokenRequest) {
        throw new UnsupportedOperationException("Cross App Access assertions are redeemed through resolveEndUser");
    }

    @Override
    public Maybe<ResolvedEndUser> resolveEndUser(TokenRequest tokenRequest) {
        return Maybe.defer(() -> {
            String assertion = assertion(tokenRequest);
            return trustedIssuerResolver.resolve(unverifiedIssuer(assertion))
                    .switchIfEmpty(Maybe.error(() -> new InvalidGrantException("Assertion issuer is not trusted")))
                    .flatMapSingle(trustedIssuer -> verify(assertion, trustedIssuer));
        });
    }

    private static Single<ResolvedEndUser> verify(String assertion, ResolvedTrustedIssuer resolved) {
        return Single.defer(() -> resolved.trustedIssuer().verifier().verify(assertion))
                .onErrorResumeNext(error -> {
                    log.debug("Assertion verification failed identityProvider={} reason={}", resolved.identityProvider(), error.getMessage());
                    return Single.error(new InvalidGrantException("Assertion verification failed", error));
                })
                .map(claims -> new ResolvedEndUser(endUser(claims), resolved.identityProvider(), claims.getClaims()));
    }

    private static String assertion(TokenRequest tokenRequest) {
        Map<String, String> parameters = tokenRequest.getRequestParameters();
        String assertion = parameters == null ? null : parameters.get(Parameters.ASSERTION);
        if (assertion == null) {
            throw new InvalidGrantException("Assertion value is missing");
        }
        return assertion;
    }

    private static String unverifiedIssuer(String assertion) {
        String issuer;
        try {
            issuer = SignedJWT.parse(assertion).getJWTClaimsSet().getIssuer();
        } catch (ParseException e) {
            throw new InvalidGrantException("Assertion cannot be parsed");
        }
        if (issuer == null) {
            throw new InvalidGrantException("Assertion issuer is missing");
        }
        return issuer;
    }

    private static User endUser(JWTClaimsSet verifiedClaims) {
        String subject = verifiedClaims.getSubject();
        String preferredUsername = stringClaim(verifiedClaims, StandardClaims.PREFERRED_USERNAME);
        String email = stringClaim(verifiedClaims, StandardClaims.EMAIL);

        Map<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put(StandardClaims.SUB, subject);
        if (preferredUsername != null) {
            additionalInformation.put(StandardClaims.PREFERRED_USERNAME, preferredUsername);
        }
        if (email != null) {
            additionalInformation.put(StandardClaims.EMAIL, email);
        }

        DefaultUser user = new DefaultUser(preferredUsername != null ? preferredUsername : subject);
        user.setId(subject);
        user.setEmail(email);
        user.setAdditionalInformation(additionalInformation);
        return user;
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {
        return claims.getClaim(name) instanceof String value ? value : null;
    }
}
