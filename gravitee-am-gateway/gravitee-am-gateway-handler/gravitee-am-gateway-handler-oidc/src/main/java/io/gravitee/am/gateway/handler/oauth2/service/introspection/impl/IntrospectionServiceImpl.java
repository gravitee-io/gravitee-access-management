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

import io.gravitee.am.common.exception.jwt.InvalidGISException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.oauth2.service.introspection.IntrospectionRequest;
import io.gravitee.am.gateway.handler.oauth2.service.introspection.IntrospectionResponse;
import io.gravitee.am.gateway.handler.oauth2.service.introspection.IntrospectionService;
import io.gravitee.am.gateway.handler.oauth2.service.token.Token;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import io.gravitee.am.model.User;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IntrospectionServiceImpl implements IntrospectionService {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private SubjectManager subjectManager;

    @Value("${services.introspection.allowAudience:true}")
    private boolean allowAudience;

    @Override
    public Single<IntrospectionResponse> introspect(IntrospectionRequest request) {
        return request.getTokenTypeHint()
                .map(hint -> tokenService.introspect(request.getToken(), hint))
                .orElseGet(() -> tokenService.introspect(request.getToken()))
                .flatMapSingle(token -> {
                    if (token.getSubject() != null && !token.getSubject().equals(token.getClientId())) {
                        return subjectManager
                                // accessToken additional info is initialized using the decoded JWT
                                // we can use it to create a temporary instance of JWT
                                .findUserBySub(new JWT(token.getAdditionalInformation()))
                                .onErrorResumeNext(err -> {
                                    if (err instanceof InvalidGISException) {
                                        // in some cases when extension grant is used
                                        // GIS claims maybe missing form the access_token
                                        // so in this case we can ignore the error and
                                        // continue the introspect process
                                        return Maybe.empty();
                                    }
                                    return Maybe.error(err);
                                })
                                .map(user -> convert(token, user))
                                .defaultIfEmpty(convert(token, null));

                    } else {
                        return Single.just(convert(token, null));
                    }
                })
                .switchIfEmpty(Single.just(new IntrospectionResponse(false)))
                .onErrorResumeNext(exception -> Single.just(new IntrospectionResponse(false)));
    }

    private IntrospectionResponse convert(Token token, User user) {
        IntrospectionResponse introspectionResponse = new IntrospectionResponse();
        introspectionResponse.setActive(true);
        introspectionResponse.setClientId(token.getClientId());
        introspectionResponse.setExp(token.getExpireAt().getTime() / 1000);
        introspectionResponse.setIat(token.getCreatedAt().getTime() / 1000);
        introspectionResponse.setTokenType(token.getTokenType());
        introspectionResponse.setSub(token.getSubject());
        if (user != null) {
            introspectionResponse.setUsername(user.getUsername());
        }
        if (token.getScope() != null && !token.getScope().isEmpty()) {
            introspectionResponse.setScope(token.getScope());
        }
        if (token.getAdditionalInformation() != null && !token.getAdditionalInformation().isEmpty()) {
            token.getAdditionalInformation().forEach((k, v) -> introspectionResponse.putIfAbsent(k, v));
        }

        if (token instanceof AccessToken) {
            final Map<String, Object> cnf = ((AccessToken) token).getConfirmationMethod();
            if (cnf != null) {
                introspectionResponse.setConfirmationMethod(cnf);
            }
        }


        if (!allowAudience) {
            // remove "aud" claim due to some backend APIs unable to verify the "aud" value
            // see <a href="https://github.com/gravitee-io/issues/issues/3111"></a>
            introspectionResponse.remove(Claims.AUD);
        }
        return introspectionResponse;
    }
}
