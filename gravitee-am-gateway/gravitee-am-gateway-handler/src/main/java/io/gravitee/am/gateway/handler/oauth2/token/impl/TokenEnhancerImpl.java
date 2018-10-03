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
package io.gravitee.am.gateway.handler.oauth2.token.impl;

import io.gravitee.am.common.oidc.ResponseType;
import io.gravitee.am.gateway.handler.oauth2.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.token.TokenEnhancer;
import io.gravitee.am.gateway.handler.oidc.idtoken.IDTokenService;
import io.gravitee.am.gateway.handler.oidc.utils.OIDCClaims;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.User;
import io.gravitee.am.repository.oauth2.model.AccessToken;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenEnhancerImpl implements TokenEnhancer {

    private static final String OPEN_ID = "openid";
    private static final String ID_TOKEN = "id_token";

    @Autowired
    private IDTokenService idTokenService;

    @Override
    public Single<AccessToken> enhance(AccessToken accessToken, OAuth2Request oAuth2Request, Client client, User endUser) {
        // enhance token with ID token
        if (oAuth2Request.getResponseType() != null && ResponseType.CODE_ID_TOKEN_TOKEN.equals(oAuth2Request.getResponseType())) {
            oAuth2Request.getContext().put(OIDCClaims.at_hash, accessToken.getToken());
            return enhanceIDToken(accessToken, client, endUser, oAuth2Request);
        } else if (oAuth2Request.getScopes() != null && oAuth2Request.getScopes().contains(OPEN_ID)) {
            return enhanceIDToken(accessToken, client, endUser, oAuth2Request);
        } else {
            return Single.just(accessToken);
        }
    }

    private Single<AccessToken> enhanceIDToken(AccessToken accessToken, Client client, User user, OAuth2Request oAuth2Request) {
        return idTokenService.create(oAuth2Request, client, user)
                .flatMap(idToken -> {
                    Map<String, Object> additionalInformation = new HashMap<>(accessToken.getAdditionalInformation());
                    additionalInformation.put(ID_TOKEN, idToken);
                    accessToken.setAdditionalInformation(additionalInformation);
                    return Single.just(accessToken);
                });
    }
}
