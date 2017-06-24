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
package io.gravitee.am.repository.mongodb.oauth2.token;

import io.gravitee.am.repository.mongodb.oauth2.token.internal.model.OAuth2AccessTokenMongo;
import io.gravitee.am.repository.mongodb.oauth2.token.internal.model.OAuth2RefreshTokenMongo;
import io.gravitee.am.repository.mongodb.oauth2.token.internal.token.OAuth2RefreshTokenMongoRepository;
import io.gravitee.am.repository.mongodb.common.SerializationUtils;
import io.gravitee.am.repository.mongodb.oauth2.token.internal.token.OAuth2AccessTokenMongoRepository;
import io.gravitee.am.repository.oauth2.api.TokenRepository;
import io.gravitee.am.repository.oauth2.model.OAuth2AccessToken;
import io.gravitee.am.repository.oauth2.model.OAuth2Authentication;
import io.gravitee.am.repository.oauth2.model.OAuth2RefreshToken;
import io.gravitee.am.repository.oauth2.model.token.AuthenticationKeyGenerator;
import io.gravitee.am.repository.oauth2.model.token.DefaultAuthenticationKeyGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Component
public class MongoTokenRepository implements TokenRepository {

    @Autowired
    private OAuth2AccessTokenMongoRepository oAuth2AccessTokenMongoRepository;

    @Autowired
    private OAuth2RefreshTokenMongoRepository oAuth2RefreshTokenMongoRepository;

    private AuthenticationKeyGenerator authenticationKeyGenerator = new DefaultAuthenticationKeyGenerator();

    @Override
    public Optional<OAuth2Authentication> readAuthentication(OAuth2AccessToken oAuth2AccessToken) {
        return readAuthentication(oAuth2AccessToken.getValue());
    }

    @Override
    public Optional<OAuth2Authentication> readAuthentication(String tokenValue) {
        OAuth2AccessTokenMongo oAuth2AccessTokenMongo = oAuth2AccessTokenMongoRepository.findOne(tokenValue);
        return Optional.ofNullable((oAuth2AccessTokenMongo == null) ? null : deserializeAuthentication(oAuth2AccessTokenMongo.getAuthentication()));
    }

    @Override
    public void storeAccessToken(OAuth2AccessToken oAuth2AccessToken, OAuth2Authentication oAuth2Authentication) {
        String refreshToken = null;
        if (oAuth2AccessToken.getRefreshToken() != null) {
            refreshToken = oAuth2AccessToken.getRefreshToken().getValue();
        }

        if (readAccessToken(oAuth2AccessToken.getValue()) != null) {
            removeAccessToken(oAuth2AccessToken.getValue());
        }

        OAuth2AccessTokenMongo oAuth2AccessTokenMongo = convert(oAuth2AccessToken, refreshToken, oAuth2Authentication);
        oAuth2AccessTokenMongoRepository.save(oAuth2AccessTokenMongo);
    }

    @Override
    public Optional<OAuth2AccessToken> readAccessToken(String tokenValue) {
        OAuth2AccessTokenMongo oAuth2AccessToken = oAuth2AccessTokenMongoRepository.findOne(tokenValue);
        return Optional.ofNullable((oAuth2AccessToken == null) ? null : convert(oAuth2AccessToken));
    }

    @Override
    public void removeAccessToken(OAuth2AccessToken oAuth2AccessToken) {
        removeAccessToken(oAuth2AccessToken.getValue());
    }

    private void removeAccessToken(String tokenValue) {
        oAuth2AccessTokenMongoRepository.delete(tokenValue);
    }

    @Override
    public void storeRefreshToken(OAuth2RefreshToken oAuth2RefreshToken, OAuth2Authentication oAuth2Authentication) {
        OAuth2RefreshTokenMongo oAuth2RefreshTokenMongo = convert(oAuth2RefreshToken, oAuth2Authentication);
        oAuth2RefreshTokenMongoRepository.save(oAuth2RefreshTokenMongo);
    }

    @Override
    public Optional<OAuth2RefreshToken> readRefreshToken(String tokenValue) {
        OAuth2RefreshTokenMongo oAuth2RefreshTokenMongo = oAuth2RefreshTokenMongoRepository.findOne(tokenValue);
        return Optional.ofNullable((oAuth2RefreshTokenMongo == null) ? null : convert(oAuth2RefreshTokenMongo));
    }

    @Override
    public Optional<OAuth2Authentication> readAuthenticationForRefreshToken(OAuth2RefreshToken oAuth2RefreshToken) {
        return readAuthenticationForRefreshToken(oAuth2RefreshToken.getValue());
    }

    private Optional<OAuth2Authentication> readAuthenticationForRefreshToken(String tokenValue) {
        OAuth2RefreshTokenMongo oAuth2RefreshTokenMongo = oAuth2RefreshTokenMongoRepository.findOne(tokenValue);
        return Optional.ofNullable((oAuth2RefreshTokenMongo == null) ? null : deserializeAuthentication(oAuth2RefreshTokenMongo.getAuthentication()));
    }

    @Override
    public void removeRefreshToken(OAuth2RefreshToken oAuth2RefreshToken) {
        removeRefreshToken(oAuth2RefreshToken.getValue());
    }

    private void removeRefreshToken(String tokenValue) {
        oAuth2RefreshTokenMongoRepository.delete(tokenValue);
    }

    @Override
    public void removeAccessTokenUsingRefreshToken(OAuth2RefreshToken oAuth2RefreshToken) {
        removeAccessTokenUsingRefreshToken(oAuth2RefreshToken.getValue());
    }

    private void removeAccessTokenUsingRefreshToken(String refreshTokenValue) {
        oAuth2AccessTokenMongoRepository.deleteByRefreshToken(refreshTokenValue);
    }

    @Override
    public Optional<OAuth2AccessToken> getAccessToken(OAuth2Authentication oAuth2Authentication) {
        OAuth2AccessToken accessToken = null;
        String key = authenticationKeyGenerator.extractKey(oAuth2Authentication);
        OAuth2AccessTokenMongo oAuth2AccessTokenMongo = oAuth2AccessTokenMongoRepository.findByAuthenticationKey(key);
        if (oAuth2AccessTokenMongo != null) {
            accessToken = convert(oAuth2AccessTokenMongo);
        }

        if (accessToken != null) {
            Optional<OAuth2Authentication> optExtractedAuthentication = readAuthentication(accessToken.getValue());
           if ((!optExtractedAuthentication.isPresent() || !key.equals(authenticationKeyGenerator.extractKey(optExtractedAuthentication.get())))) {
               removeAccessToken(accessToken.getValue());
               // Keep the store consistent (maybe the same user is represented by this authentication but the details have
               // changed)
               storeAccessToken(accessToken, oAuth2Authentication);

               // something happens with authentication (different serialization object)
               // Keep the refresh token consistent
               if (!optExtractedAuthentication.isPresent() && accessToken.getRefreshToken() != null) {
                   storeRefreshToken(accessToken.getRefreshToken(), oAuth2Authentication);
               }
           }
        }
        return Optional.ofNullable(accessToken);
    }

    @Override
    public Collection<OAuth2AccessToken> findTokensByClientIdAndUserName(String clientId, String userName) {
        return oAuth2AccessTokenMongoRepository
                .findByClientIdAndUserName(clientId, userName)
                .stream()
                .map(this::convert)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<OAuth2AccessToken> findTokensByClientId(String clientId) {
        return oAuth2AccessTokenMongoRepository
                .findByClientId(clientId)
                .stream()
                .map(this::convert)
                .collect(Collectors.toList());
    }

    private OAuth2RefreshToken convert(OAuth2RefreshTokenMongo oAuth2RefreshTokenMongo) {
        OAuth2RefreshToken oAuth2RefreshToken = new OAuth2RefreshToken(oAuth2RefreshTokenMongo.getValue());
        oAuth2RefreshToken.setExpiration(oAuth2RefreshTokenMongo.getExpiration());

        return oAuth2RefreshToken;
    }

    private OAuth2RefreshTokenMongo convert(OAuth2RefreshToken oAuth2RefreshToken, OAuth2Authentication oAuth2Authentication) {
        OAuth2RefreshTokenMongo oAuth2RefreshTokenMongo = new OAuth2RefreshTokenMongo();
        oAuth2RefreshTokenMongo.setValue(oAuth2RefreshToken.getValue());
        oAuth2RefreshTokenMongo.setExpiration(oAuth2RefreshToken.getExpiration());
        oAuth2RefreshTokenMongo.setAuthentication(serializeAuthentication(oAuth2Authentication));

        return oAuth2RefreshTokenMongo;
    }


    private OAuth2AccessToken convert(OAuth2AccessTokenMongo oAuth2AccessTokenMongo) {
        OAuth2AccessToken oAuth2AccessToken = new OAuth2AccessToken(oAuth2AccessTokenMongo.getValue());
        oAuth2AccessToken.setAdditionalInformation(oAuth2AccessTokenMongo.getAdditionalInformation());

        // get refresh token
        OAuth2RefreshTokenMongo oAuth2RefreshTokenMongo = oAuth2RefreshTokenMongoRepository.findOne(oAuth2AccessTokenMongo.getRefreshToken());
        if (oAuth2RefreshTokenMongo != null) {
            OAuth2RefreshToken oAuth2RefreshToken = new OAuth2RefreshToken(oAuth2AccessTokenMongo.getRefreshToken());
            oAuth2RefreshToken.setExpiration(oAuth2RefreshTokenMongo.getExpiration());
            oAuth2AccessToken.setRefreshToken(oAuth2RefreshToken);
        }
        oAuth2AccessToken.setExpiration(oAuth2AccessTokenMongo.getExpiration());
        oAuth2AccessToken.setScope(oAuth2AccessTokenMongo.getScope());
        oAuth2AccessToken.setTokenType(oAuth2AccessTokenMongo.getTokenType());

        return oAuth2AccessToken;
    }

    private OAuth2AccessTokenMongo convert(OAuth2AccessToken oAuth2AccessToken, String oAuth2RefreshToken, OAuth2Authentication oAuth2Authentication) {
        OAuth2AccessTokenMongo oAuth2AccessTokenMongo = new OAuth2AccessTokenMongo();
        oAuth2AccessTokenMongo.setValue(oAuth2AccessToken.getValue());
        oAuth2AccessTokenMongo.setAdditionalInformation(oAuth2AccessToken.getAdditionalInformation());
        oAuth2AccessTokenMongo.setRefreshToken(oAuth2RefreshToken);
        oAuth2AccessTokenMongo.setExpiration(oAuth2AccessToken.getExpiration());
        oAuth2AccessTokenMongo.setScope(oAuth2AccessToken.getScope());
        oAuth2AccessTokenMongo.setTokenType(oAuth2AccessToken.getTokenType());
        oAuth2AccessTokenMongo.setUserName(oAuth2Authentication.isClientOnly() ? null : oAuth2Authentication.getName());
        oAuth2AccessTokenMongo.setClientId(oAuth2Authentication.getOAuth2Request().getClientId());
        oAuth2AccessTokenMongo.setAuthenticationKey(authenticationKeyGenerator.extractKey(oAuth2Authentication));
        oAuth2AccessTokenMongo.setAuthentication(serializeAuthentication(oAuth2Authentication));

        return oAuth2AccessTokenMongo;
    }

    private OAuth2Authentication deserializeAuthentication(byte[] authentication) {
        try {
            return SerializationUtils.deserialize(authentication);
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] serializeAuthentication(OAuth2Authentication authentication) {
        try {
            return SerializationUtils.serialize(authentication);
        } catch (Exception e) {
            return null;
        }
    }
}
