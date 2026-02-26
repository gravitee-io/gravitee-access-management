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
package io.gravitee.am.model.application;

import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;

/**
 * Token exchange OAuth settings, with optional inheritance from domain defaults.
 */
public class TokenExchangeOAuthSettings {

    private boolean inherited = true;
    private TokenExchangeScopeHandling scopeHandling = TokenExchangeScopeHandling.DOWNSCOPING;

    public TokenExchangeOAuthSettings() {}

    public TokenExchangeOAuthSettings(TokenExchangeOAuthSettings other) {
        this.inherited = other.inherited;
        this.scopeHandling = other.scopeHandling;
    }

    public boolean isInherited() {
        return inherited;
    }

    public void setInherited(boolean inherited) {
        this.inherited = inherited;
    }

    public TokenExchangeScopeHandling getScopeHandling() {
        return scopeHandling;
    }

    public void setScopeHandling(TokenExchangeScopeHandling scopeHandling) {
        this.scopeHandling = scopeHandling;
    }

    /**
     * Resolves the effective TokenExchangeOAuthSettings for a client, applying domain-level inheritance.
     */
    public static TokenExchangeOAuthSettings getInstance(Domain domain, Client client) {
        if (client != null && client.getTokenExchangeOAuthSettings() != null && !client.getTokenExchangeOAuthSettings().isInherited()) {
            return client.getTokenExchangeOAuthSettings();
        }
    
        if (domain != null
                && domain.getTokenExchangeSettings() != null
                && domain.getTokenExchangeSettings().getTokenExchangeOAuthSettings() != null) {
            return domain.getTokenExchangeSettings().getTokenExchangeOAuthSettings();
        }
    
        return new TokenExchangeOAuthSettings();
    }
}
