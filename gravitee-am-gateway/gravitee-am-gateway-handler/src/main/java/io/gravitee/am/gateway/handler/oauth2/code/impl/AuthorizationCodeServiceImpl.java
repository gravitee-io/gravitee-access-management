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
package io.gravitee.am.gateway.handler.oauth2.code.impl;

import io.gravitee.am.gateway.handler.oauth2.code.AuthorizationCodeService;
import io.gravitee.am.repository.oauth2.api.AuthorizationCodeRepository;
import io.gravitee.am.repository.oauth2.model.OAuth2Authentication;
import io.gravitee.am.repository.oauth2.model.code.OAuth2AuthorizationCode;
import io.gravitee.common.utils.UUID;
import io.reactivex.Maybe;
import io.reactivex.Single;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.Date;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AuthorizationCodeServiceImpl implements AuthorizationCodeService {

    @Value("${authorization.code.validity:60000}")
    private int authorizationCodeValidity;

    @Autowired
    private AuthorizationCodeRepository authorizationCodeRepository;


    @Override
    public Single<OAuth2AuthorizationCode> create(OAuth2Authentication oAuth2Authentication) {
        OAuth2AuthorizationCode oAuth2AuthorizationCode = new OAuth2AuthorizationCode();
        oAuth2AuthorizationCode.setCode(UUID.random().toString());
        oAuth2AuthorizationCode.setOAuth2Authentication(oAuth2Authentication);
        oAuth2AuthorizationCode.setExpiration(new Date(System.currentTimeMillis() + authorizationCodeValidity));
        oAuth2AuthorizationCode.setCreatedAt(new Date());
        oAuth2AuthorizationCode.setUpdatedAt(oAuth2AuthorizationCode.getCreatedAt());

        //TODO
        // return authorizationCodeRepository.create(oAuth2AuthorizationCode);
        return null;
    }

    @Override
    public Maybe<OAuth2Authentication> remove(String code) {
        //TODO
        // return authorizationCodeRepository.delete(code);
        return Maybe.empty();
    }
}
