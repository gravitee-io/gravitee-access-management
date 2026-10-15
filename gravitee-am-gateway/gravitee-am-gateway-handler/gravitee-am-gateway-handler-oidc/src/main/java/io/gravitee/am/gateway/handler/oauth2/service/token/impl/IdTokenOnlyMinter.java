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
package io.gravitee.am.gateway.handler.oauth2.service.token.impl;

import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2RequestParams;
import io.gravitee.am.gateway.handler.oidc.service.idtoken.IDTokenService;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.ClientTokenAuditBuilder;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;

public class IdTokenOnlyMinter extends BaseTokenMinter {

    @Autowired
    IDTokenService idTokenService;

    @Override
    protected Single<MintedToken> mint(OAuth2Request oAuth2Request, Client client, User endUser, ExecutionContext executionContext) {
        return idTokenService.create(oAuth2Request, client, endUser, executionContext)
                .map(idTokenString -> {
                    // Create ExchangedIDToken with the ID token string
                    // ExchangedIDToken sets token_type="N_A" and issued_token_type=ID_TOKEN by default
                    ExchangedIDToken token = new ExchangedIDToken(idTokenString);
                    token.setExpiresIn(calculateExpiresIn(oAuth2Request, client));

                    // Note: No scope is set for ID tokens as they are for identity/authentication,
                    // not authorization. The ExchangedIDTokenSerializer also omits scope from serialization.
                    return new MintedToken(token, buildSuccessAudit(oAuth2Request, client, endUser));
                });

    }

    private ClientTokenAuditBuilder buildSuccessAudit(OAuth2Request oAuth2Request, Client client, User endUser) {
        return AuditBuilder.builder(ClientTokenAuditBuilder.class)
                .idTokenFor(endUser)
                .tokenActor(client)
                .withParams(() -> OAuth2RequestParams.of(oAuth2Request))
                .tokenTarget(endUser);
    }

    private long calculateExpiresIn(OAuth2Request oAuth2Request, Client client){
        // Calculate expires_in based on client's ID token validity
        // Also respect exchange expiration if set
        long defaultExpSeconds = client.getIdTokenValiditySeconds();
        long expiresIn = defaultExpSeconds;
        if (oAuth2Request.getExchangeExpiration() != null) {
            long subjectExpSeconds = (oAuth2Request.getExchangeExpiration().getTime() - System.currentTimeMillis()) / 1000;
            expiresIn = Math.min(defaultExpSeconds, Math.max(0, subjectExpSeconds));
        }
        return expiresIn;
    }
}
