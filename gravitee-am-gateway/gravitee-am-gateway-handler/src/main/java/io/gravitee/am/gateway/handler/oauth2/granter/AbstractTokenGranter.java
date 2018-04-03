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
package io.gravitee.am.gateway.handler.oauth2.granter;

import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.exception.UnauthorizedClientException;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.model.Client;
import io.reactivex.Single;
import io.reactivex.functions.Function;

import java.util.Objects;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AbstractTokenGranter implements TokenGranter {

    private final String grantType;

    private ClientService clientService;

    public AbstractTokenGranter(final String grantType) {
        Objects.requireNonNull(grantType);
        this.grantType = grantType;
    }

    @Override
    public boolean handle(String grantType) {
        return this.grantType.equals(grantType);
    }

    @Override
    public Single<AccessToken> grant(TokenRequest tokenRequest) {
        if (! this.grantType.equals(tokenRequest.getGrantType())) {
            //TODO: Implement a way to generate access token
            return Single.just(new AccessToken() {
                @Override
                public int hashCode() {
                    return super.hashCode();
                }
            });
        }

        return clientService.findByClientId(tokenRequest.getClientId())
                .map(new Function<Client, AccessToken>() {
            @Override
            public AccessToken apply(Client client) throws Exception {
                // Is client allowed to use such grant type ?
                if (client.getAuthorizedGrantTypes() != null && !client.getAuthorizedGrantTypes().isEmpty()
                        && !client.getAuthorizedGrantTypes().contains(grantType)) {
                    throw new UnauthorizedClientException("Unauthorized grant type: " + grantType);
                }

                return null;
            }
        }).toSingle();
    }

    public void setClientService(ClientService clientService) {
        this.clientService = clientService;
    }
}
