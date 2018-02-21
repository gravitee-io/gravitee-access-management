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
package io.gravitee.am.repository.oauth2.api;

import io.gravitee.am.model.Irrelevant;
import io.gravitee.am.repository.oauth2.model.OAuth2AccessToken;
import io.gravitee.am.repository.oauth2.model.OAuth2Authentication;
import io.gravitee.am.repository.oauth2.model.OAuth2RefreshToken;
import io.reactivex.Maybe;
import io.reactivex.Single;

import java.util.List;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface TokenRepository {

    /**
     * Read the authentication stored under the specified token value.
     *
     * @param token The token value under which the authentication is stored.
     * @return The authentication, or null if none.
     */
    Maybe<OAuth2Authentication> readAuthentication(OAuth2AccessToken token);

    /**
     * Read the authentication stored under the specified token value.
     *
     * @param token The token value under which the authentication is stored.
     * @return The authentication, or null if none.
     */
    Maybe<OAuth2Authentication> readAuthentication(String token);

    /**
     * Store an access token.
     *  @param token The token to store.
     * @param authentication The authentication associated with the token.
     * @param authenticationKey The authentication key generated from the authentication
     */
    Single<OAuth2AccessToken> storeAccessToken(OAuth2AccessToken token, OAuth2Authentication authentication, String authenticationKey);

    /**
     * Read an access token from the store.
     *
     * @param tokenValue The token value.
     * @return The access token to read.
     */
    Maybe<OAuth2AccessToken> readAccessToken(String tokenValue);

    /**
     * Remove an access token from the database.
     *
     * @param token The token to remove from the database.
     */
    Single<Irrelevant> removeAccessToken(OAuth2AccessToken token);

    /**
     * Remove an access token from the database.
     *
     * @param token The token key to remove from the database.
     */
    Single<Irrelevant> removeAccessToken(String token);

    /**
     * Store the specified refresh token in the database.
     *  @param refreshToken The refresh token to store.
     * @param authentication The authentication associated with the refresh token.
     */
    Single<OAuth2RefreshToken> storeRefreshToken(OAuth2RefreshToken refreshToken, OAuth2Authentication authentication);

    /**
     * Read a refresh token from the store.
     *
     * @param tokenValue The value of the token to read.
     * @return The token.
     */
    Maybe<OAuth2RefreshToken> readRefreshToken(String tokenValue);

    /**
     * Read the authentication stored under the specified refresh token value.
     *
     * @param token a refresh token
     * @return the authentication originally used to grant the refresh token
     */
    Maybe<OAuth2Authentication> readAuthenticationForRefreshToken(OAuth2RefreshToken token);

    /**
     * Remove a refresh token from the database.
     *
     * @param token The token to remove from the database.
     */
    Single<Irrelevant> removeRefreshToken(OAuth2RefreshToken token);

    /**
     * Remove an access token using a refresh token. This functionality is necessary so refresh tokens can't be used to
     * create an unlimited number of access tokens.
     *
     * @param refreshToken The refresh token.
     */
    Single<Irrelevant> removeAccessTokenUsingRefreshToken(OAuth2RefreshToken refreshToken);

    /**
     * Retrieve an access token stored against the provided authentication key, if it exists.
     *
     * @param authenticationKey the authentication key for the access token
     *
     * @return the access token or null if there was none
     */
    Maybe<OAuth2AccessToken> getAccessToken(String authenticationKey);

    /**
     * Retrieve access tokens stored against the provided client id.
     *
     * @param clientId the client id to search
     * @param userName the user name to search
     * @return a collection of access tokens
     */
    Single<List<OAuth2AccessToken>> findTokensByClientIdAndUserName(String clientId, String userName);

    /**
     * Retrieve access tokens stored against the provided client id.
     *
     * @param clientId the client id to search
     * @return a collection of access tokens
     */
    Single<List<OAuth2AccessToken>> findTokensByClientId(String clientId);
}
