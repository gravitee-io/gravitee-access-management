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
package io.gravitee.am.identityprovider.api.trustedissuer;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTProcessor;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JwtType;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.util.Set;

public class IdJagAssertionVerifier implements AssertionVerifier {

    private static final JOSEObjectType ID_JAG_TYPE = new JOSEObjectType(JwtType.ID_JAG.getValue());

    private static final Set<JWSAlgorithm> ASYMMETRIC_ALGORITHMS = Set.of(
            JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512,
            JWSAlgorithm.ES256, JWSAlgorithm.ES384, JWSAlgorithm.ES512,
            JWSAlgorithm.PS256, JWSAlgorithm.PS384, JWSAlgorithm.PS512);

    private static final Set<String> REQUIRED_CLAIMS = Set.of(
            Claims.SUB, Claims.AUD, Claims.CLIENT_ID, Claims.EXP, Claims.IAT, Claims.JTI);

    private final JWTProcessor<SecurityContext> processor;

    public IdJagAssertionVerifier(String issuer, JWKSource<SecurityContext> jwkSource) {
        DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
        jwtProcessor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(ID_JAG_TYPE));
        jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(ASYMMETRIC_ALGORITHMS, jwkSource));
        jwtProcessor.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                new JWTClaimsSet.Builder().issuer(issuer).build(), REQUIRED_CLAIMS));
        this.processor = jwtProcessor;
    }

    @Override
    public Single<JWTClaimsSet> verify(String assertion) {
        return Single.fromCallable(() -> processor.process(assertion, null))
                .subscribeOn(Schedulers.io());
    }
}
