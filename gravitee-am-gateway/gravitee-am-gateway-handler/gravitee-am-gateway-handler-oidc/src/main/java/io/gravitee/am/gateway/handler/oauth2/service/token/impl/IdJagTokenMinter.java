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
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oidc.service.idjag.IdJag;
import io.gravitee.am.gateway.handler.oidc.service.idjag.IdJagService;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.gravitee.am.service.reporter.builder.ClientTokenAuditBuilder;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;

public class IdJagTokenMinter extends BaseTokenMinter {

    @Autowired
    private IdJagService idJagService;

    @Override
    protected Single<MintedToken> mint(OAuth2Request oAuth2Request, Client client, User endUser, ExecutionContext executionContext) {
        return idJagService.create(oAuth2Request, client, endUser, executionContext)
                .map(idJag -> new MintedToken(toTokenResponse(idJag), buildSuccessAudit(idJag, oAuth2Request, client, endUser)));
    }

    private Token toTokenResponse(IdJag idJag) {
        ExchangedIdJag token = new ExchangedIdJag(idJag.value());
        token.setExpiresIn(idJag.expiresIn());
        token.setScope(idJag.scope());
        return token;
    }

    private ClientTokenAuditBuilder buildSuccessAudit(IdJag idJag, OAuth2Request oAuth2Request, Client client, User endUser) {
        return AuditBuilder.builder(ClientTokenAuditBuilder.class)
                .idJag(idJag.tokenId())
                .tokenActor(client)
                .agentApplication(client)
                .withParams(() -> OAuth2RequestParams.of(oAuth2Request))
                .tokenTarget(endUser);
    }

}
