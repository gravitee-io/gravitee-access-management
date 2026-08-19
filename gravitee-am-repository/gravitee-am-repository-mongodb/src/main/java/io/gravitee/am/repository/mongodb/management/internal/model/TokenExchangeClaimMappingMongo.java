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
package io.gravitee.am.repository.mongodb.management.internal.model;

import io.gravitee.am.model.application.TokenExchangeClaimMapping;
import io.gravitee.am.model.application.TokenExchangeClaimSource;
import lombok.Getter;
import lombok.Setter;

/**
 * MongoDB representation of {@link TokenExchangeClaimMapping}.
 */
@Setter
@Getter
public class TokenExchangeClaimMappingMongo {

    private String source;
    private String sourceClaim;
    private String tokenClaim;

    public TokenExchangeClaimMapping convert() {
        TokenExchangeClaimMapping mapping = new TokenExchangeClaimMapping();
        if (getSource() != null) {
            mapping.setSource(TokenExchangeClaimSource.valueOf(getSource()));
        }

        mapping.setSourceClaim(getSourceClaim());
        mapping.setTokenClaim(getTokenClaim());
        return mapping;
    }

    public static TokenExchangeClaimMappingMongo convert(TokenExchangeClaimMapping mapping) {
        if (mapping == null) {
            return null;
        }

        TokenExchangeClaimMappingMongo mongo = new TokenExchangeClaimMappingMongo();
        if (mapping.getSource() != null) {
            mongo.setSource(mapping.getSource().name());
        }

        mongo.setSourceClaim(mapping.getSourceClaim());
        mongo.setTokenClaim(mapping.getTokenClaim());
        return mongo;
    }
}
