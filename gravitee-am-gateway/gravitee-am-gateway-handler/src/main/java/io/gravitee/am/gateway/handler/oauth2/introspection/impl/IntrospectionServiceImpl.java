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
package io.gravitee.am.gateway.handler.oauth2.introspection.impl;

import io.gravitee.am.gateway.handler.oauth2.introspection.IntrospectionRequest;
import io.gravitee.am.gateway.handler.oauth2.introspection.IntrospectionResponse;
import io.gravitee.am.gateway.handler.oauth2.introspection.IntrospectionService;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.token.impl.DefaultAccessToken;
import io.gravitee.am.gateway.service.UserService;
import io.gravitee.am.model.User;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IntrospectionServiceImpl implements IntrospectionService {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserService userService;

    @Override
    public Single<IntrospectionResponse> introspect(IntrospectionRequest introspectionRequest) {
            return tokenService.getAccessToken(introspectionRequest.getToken())
                    .filter(token -> token.getExpiresIn() > 0)
                    .flatMap(token -> {
                        DefaultAccessToken accessToken = (DefaultAccessToken) token;
                        if (accessToken.getSubject() != null) {
                            return userService
                                    .findById(accessToken.getSubject())
                                    .map(user -> convert(accessToken, user))
                                    .defaultIfEmpty(convert(accessToken, null));

                        } else {
                            return Maybe.just(convert(accessToken, null));
                        }
                    })
                    .defaultIfEmpty(new IntrospectionResponse())
                    .toSingle();

    }

    public void setTokenService(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    private IntrospectionResponse convert(DefaultAccessToken accessToken, User user) {
        IntrospectionResponse introspectionResponse = new IntrospectionResponse();
        introspectionResponse.setActive(true);
        introspectionResponse.setScope(accessToken.getScope());
        introspectionResponse.setClientId(accessToken.getClientId());
        if (user != null) {
            introspectionResponse.setUsername(user.getUsername());
        }
        introspectionResponse.setExpireAt(accessToken.getExpireAt().getTime() / 1000);
        introspectionResponse.setIssueAt(accessToken.getCreatedAt().getTime() / 1000);
        introspectionResponse.setTokenType(accessToken.getTokenType());
        introspectionResponse.setSubject(accessToken.getSubject());
        return introspectionResponse;
    }
}
