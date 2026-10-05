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
package io.gravitee.am.repository.mongodb.management;

import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.repository.mongodb.management.internal.model.TokenClaimMongo;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class MongoApplicationRepositoryTokenClaimsTest {

    @Test
    public void shouldSkipTokenClaimWithUnknownTokenType() {
        List<TokenClaim> claims = MongoApplicationRepository.getTokenClaims(List.of(
                claim("UNKNOWN_TYPE", "jag-claim"),
                claim(null, "null-claim")));

        assertThat(claims).isEmpty();
    }

    @Test
    public void shouldKeepKnownTokenClaims() {
        List<TokenClaim> claims = MongoApplicationRepository.getTokenClaims(List.of(
                claim("ACCESS_TOKEN", "at-claim"),
                claim("UNKNOWN_TYPE", "jag-claim"),
                claim("ID_TOKEN", "id-claim"),
                claim("REFRESH_TOKEN", "rt-claim")));

        assertThat(claims)
                .extracting(TokenClaim::getTokenType, TokenClaim::getClaimName, TokenClaim::getClaimValue)
                .containsExactly(
                        tuple(TokenTypeHint.ACCESS_TOKEN, "at-claim", "at-claim-value"),
                        tuple(TokenTypeHint.ID_TOKEN, "id-claim", "id-claim-value"),
                        tuple(TokenTypeHint.REFRESH_TOKEN, "rt-claim", "rt-claim-value"));
    }

    private static TokenClaimMongo claim(String tokenType, String name) {
        TokenClaimMongo claim = new TokenClaimMongo();
        claim.setTokenType(tokenType);
        claim.setClaimName(name);
        claim.setClaimValue(name + "-value");
        return claim;
    }
}
