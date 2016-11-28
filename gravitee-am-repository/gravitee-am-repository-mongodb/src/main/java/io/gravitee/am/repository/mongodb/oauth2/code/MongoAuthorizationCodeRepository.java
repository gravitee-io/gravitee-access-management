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
package io.gravitee.am.repository.mongodb.oauth2.code;

import io.gravitee.am.repository.mongodb.oauth2.code.internal.OAuth2AuthorizationCodeMongoRepository;
import io.gravitee.am.repository.mongodb.oauth2.code.model.OAuth2AuthorizationCodeMongo;
import io.gravitee.am.repository.mongodb.oauth2.common.SerializationUtils;
import io.gravitee.am.repository.oauth2.api.AuthorizationCodeRepository;
import io.gravitee.am.repository.oauth2.model.OAuth2Authentication;
import io.gravitee.am.repository.oauth2.model.code.OAuth2AuthorizationCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoAuthorizationCodeRepository implements AuthorizationCodeRepository {

    @Autowired
    private OAuth2AuthorizationCodeMongoRepository oAuth2AuthorizationCodeMongoRepository;

    @Override
    public void store(OAuth2AuthorizationCode oAuth2AuthorizationCode) {
        OAuth2AuthorizationCodeMongo oAuth2AuthorizationCodeMongo = new OAuth2AuthorizationCodeMongo();
        oAuth2AuthorizationCodeMongo.setCode(oAuth2AuthorizationCode.getCode());
        oAuth2AuthorizationCodeMongo.setOAuth2Authentication(SerializationUtils.serialize(oAuth2AuthorizationCode.getOAuth2Authentication()));
        oAuth2AuthorizationCodeMongo.setExpiration(oAuth2AuthorizationCode.getExpiration());
        oAuth2AuthorizationCodeMongo.setCreatedAt(oAuth2AuthorizationCode.getCreatedAt());
        oAuth2AuthorizationCodeMongo.setUpdatedAt(oAuth2AuthorizationCode.getUpdatedAt());

        oAuth2AuthorizationCodeMongoRepository.store(oAuth2AuthorizationCodeMongo);
    }

    @Override
    public Optional<OAuth2Authentication> remove(String code) {
        OAuth2AuthorizationCodeMongo oAuth2AuthorizationCodeMongo =  oAuth2AuthorizationCodeMongoRepository.remove(code);
        return Optional.ofNullable((oAuth2AuthorizationCodeMongo == null) ? null : SerializationUtils.deserialize(oAuth2AuthorizationCodeMongo.getOAuth2Authentication()));
    }
}
