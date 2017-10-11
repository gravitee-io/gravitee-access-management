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
package io.gravitee.am.gateway.handler.oauth2.provider.code;

import io.gravitee.am.gateway.handler.oauth2.provider.RepositoryProviderUtils;
import io.gravitee.am.repository.oauth2.api.AuthorizationCodeRepository;
import io.gravitee.am.model.oauth2.code.OAuth2AuthorizationCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.code.RandomValueAuthorizationCodeServices;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class RepositoryAuthorizationCodeServices extends RandomValueAuthorizationCodeServices {

    @Value("${authorization.code.validity:60000}")
    private int authorizationCodeValidity;

    @Autowired
    private AuthorizationCodeRepository authorizationCodeRepository;

    @Override
    protected void store(String code, OAuth2Authentication oAuth2Authentication) {
        OAuth2AuthorizationCode oAuth2AuthorizationCode = new OAuth2AuthorizationCode();
        oAuth2AuthorizationCode.setCode(code);
        oAuth2AuthorizationCode.setOAuth2Authentication(RepositoryProviderUtils.convert(oAuth2Authentication));
        oAuth2AuthorizationCode.setExpiration(new Date(System.currentTimeMillis() + authorizationCodeValidity));
        oAuth2AuthorizationCode.setCreatedAt(new Date());
        oAuth2AuthorizationCode.setUpdatedAt(oAuth2AuthorizationCode.getCreatedAt());

        authorizationCodeRepository.store(oAuth2AuthorizationCode);
    }

    @Override
    protected OAuth2Authentication remove(String code) {
        Optional<io.gravitee.am.model.oauth2.OAuth2Authentication> oAuth2Authentication = authorizationCodeRepository.remove(code);

        if (oAuth2Authentication.isPresent()) {
            return RepositoryProviderUtils.convert(oAuth2Authentication.get());
        } else {
            return null;
        }
    }
}
