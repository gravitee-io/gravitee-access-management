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

import io.gravitee.am.model.application.TokenExchangeOAuthSettings;
import io.gravitee.am.model.application.TokenExchangeScopeHandling;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MongoDB representation of {@link TokenExchangeOAuthSettings}.
 */
public class TokenExchangeOAuthSettingsMongo {

    private boolean inherited = true;
    private String scopeHandling;
    private List<TokenExchangeClaimMappingMongo> claimsMapper;

    public boolean isInherited() {
        return inherited;
    }

    public void setInherited(boolean inherited) {
        this.inherited = inherited;
    }

    public String getScopeHandling() {
        return scopeHandling;
    }

    public void setScopeHandling(String scopeHandling) {
        this.scopeHandling = scopeHandling;
    }

    public List<TokenExchangeClaimMappingMongo> getClaimsMapper() {
        return claimsMapper;
    }

    public void setClaimsMapper(List<TokenExchangeClaimMappingMongo> claimsMapper) {
        this.claimsMapper = claimsMapper;
    }

    /**
     * Convert this MongoDB representation to the domain model.
     */
    public TokenExchangeOAuthSettings convert() {
        TokenExchangeOAuthSettings settings = new TokenExchangeOAuthSettings();
        settings.setInherited(isInherited());
        if (getScopeHandling() != null) {
            settings.setScopeHandling(TokenExchangeScopeHandling.valueOf(getScopeHandling()));
        }
        if (getClaimsMapper() != null) {
            settings.setClaimsMapper(getClaimsMapper().stream()
                    .map(TokenExchangeClaimMappingMongo::convert)
                    .collect(Collectors.toList()));
        }
        return settings;
    }

    /**
     * Convert a domain model to its MongoDB representation.
     */
    public static TokenExchangeOAuthSettingsMongo convert(TokenExchangeOAuthSettings settings) {
        if (settings == null) {
            return null;
        }
        TokenExchangeOAuthSettingsMongo mongo = new TokenExchangeOAuthSettingsMongo();
        mongo.setInherited(settings.isInherited());
        if (settings.getScopeHandling() != null) {
            mongo.setScopeHandling(settings.getScopeHandling().name());
        }
        if (settings.getClaimsMapper() != null) {
            mongo.setClaimsMapper(settings.getClaimsMapper().stream()
                    .map(TokenExchangeClaimMappingMongo::convert)
                    .collect(Collectors.toList()));
        }
        return mongo;
    }
}
