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

import io.gravitee.am.common.oidc.ResponseType;
import io.gravitee.am.common.oidc.idtoken.Claims;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenEnhancer;
import io.gravitee.am.gateway.handler.oidc.service.idtoken.IDTokenService;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.User;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenEnhancerImpl implements TokenEnhancer {

    @Autowired
    private IDTokenService idTokenService;

    /**
     * Option introduce to mitigate the breaking change introduced in 4.3.0
     * This option will be available at least to 4.8.0
     * Probably will be removed in 4.9.0
     */
    @Deprecated(forRemoval = true)
    @Value("${legacy.openid.accept_openid_for_service_app:true}")
    private Boolean acceptOpenidForServiceApp = Boolean.FALSE;

    @Override
    public Single<Token> enhance(Token accessToken, OAuth2Request oAuth2Request, Client client, User endUser, ExecutionContext executionContext) {
        // enhance token with ID token
        return Single.fromCallable(() -> oAuth2Request.shouldGenerateIDToken(this.acceptOpenidForServiceApp)).flatMap(generate -> {
            if (Boolean.TRUE.equals(generate)) {
                return enhanceIDToken(accessToken, client, endUser, oAuth2Request, executionContext);
            } else {
                return Single.just(accessToken);
            }
        });
    }

    private Single<Token> enhanceIDToken(Token accessToken, Client client, User user, OAuth2Request oAuth2Request, ExecutionContext executionContext) {
        if (oAuth2Request.isSupportAtHashValue()) {
            oAuth2Request.getContext().put(Claims.AT_HASH, accessToken.getValue());
        }
        return idTokenService.create(oAuth2Request, client, user, executionContext)
                .map(idToken -> {
                    Map<String, Object> additionalInformation = new HashMap<>(accessToken.getAdditionalInformation());
                    additionalInformation.put(ResponseType.ID_TOKEN, idToken);
                    accessToken.setAdditionalInformation(additionalInformation);
                    return accessToken;
                });
    }
}
