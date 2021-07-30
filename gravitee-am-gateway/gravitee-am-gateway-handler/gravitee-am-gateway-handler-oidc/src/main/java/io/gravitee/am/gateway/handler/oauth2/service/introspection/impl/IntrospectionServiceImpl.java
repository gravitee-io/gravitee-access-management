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
package io.gravitee.am.gateway.handler.oauth2.service.introspection.impl;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.gateway.handler.oauth2.service.introspection.IntrospectionRequest;
import io.gravitee.am.gateway.handler.oauth2.service.introspection.IntrospectionResponse;
import io.gravitee.am.gateway.handler.oauth2.service.introspection.IntrospectionService;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import io.gravitee.am.model.User;
import io.gravitee.am.service.UserService;
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
        return tokenService.introspect(introspectionRequest.getToken())
                .flatMap(token -> {
                    AccessToken accessToken = (AccessToken) token;
                    if (accessToken.getSubject() != null && !accessToken.getSubject().equals(accessToken.getClientId())) {
                        return userService
                                .findById(accessToken.getSubject())
                                .map(user -> convert(accessToken, user))
                                .defaultIfEmpty(convert(accessToken, null))
                                .toSingle();

                    } else {
                        return Single.just(convert(accessToken, null));
                    }
                })
                .onErrorResumeNext(Single.just(new IntrospectionResponse(false)));
    }

    private IntrospectionResponse convert(AccessToken accessToken, User user) {
        IntrospectionResponse introspectionResponse = new IntrospectionResponse();
        introspectionResponse.setActive(true);
        introspectionResponse.setClientId(accessToken.getClientId());
        introspectionResponse.setExp(accessToken.getExpireAt().getTime() / 1000);
        introspectionResponse.setIat(accessToken.getCreatedAt().getTime() / 1000);
        introspectionResponse.setTokenType(accessToken.getTokenType());
        introspectionResponse.setSub(accessToken.getSubject());
        if (user != null) {
            introspectionResponse.setUsername(user.getUsername());
        }
        if (accessToken.getScope() != null && !accessToken.getScope().isEmpty()) {
            introspectionResponse.setScope(accessToken.getScope());
        }
        if (accessToken.getAdditionalInformation() != null && !accessToken.getAdditionalInformation().isEmpty()) {
            accessToken.getAdditionalInformation().forEach((k, v) -> introspectionResponse.putIfAbsent(k, v));
        }

        introspectionResponse.setConfirmationMethod(accessToken.getConfirmationMethod());

        // remove "aud" claim due to some backend APIs unable to verify the "aud" value
        // see <a href="https://github.com/gravitee-io/issues/issues/3111"></a>
        introspectionResponse.remove(Claims.aud);
        return introspectionResponse;
    }
}
