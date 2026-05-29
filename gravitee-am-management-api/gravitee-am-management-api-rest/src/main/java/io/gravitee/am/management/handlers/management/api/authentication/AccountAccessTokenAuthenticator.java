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
package io.gravitee.am.management.handlers.management.api.authentication;

import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.management.service.OrganizationUserService;
import io.gravitee.am.model.AccountAccessToken;
import io.gravitee.am.model.User;
import io.reactivex.rxjava3.core.Single;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Authenticates opaque user service-account access tokens.
 *
 * @author GraviteeSource Team
 */
public class AccountAccessTokenAuthenticator {

    private final OrganizationUserService userService;
    private final long blockingGetTimeoutMillis;

    public AccountAccessTokenAuthenticator(OrganizationUserService userService, long blockingGetTimeoutMillis) {
        this.userService = userService;
        this.blockingGetTimeoutMillis = blockingGetTimeoutMillis;
    }

    /**
     * Decode and authenticate an opaque bearer value and build a principal.
     *
     * @throws BadCredentialsException if the token is malformed (not valid Base64, or the decoded
     *     payload is not exactly two dot-separated parts).
     */
    public UsernamePasswordAuthenticationToken authenticate(String encodedToken) {
        AccountAccessToken.Decoded token;
        try {
            token = AccountAccessToken.decode(encodedToken);
        } catch (IllegalArgumentException e) {
            throw new BadCredentialsException("Malformed token", e);
        }

        var orgUser = userService.findByAccessToken(token.tokenId(), token.tokenValue())
                .compose(this::applyTimeout)
                .blockingGet();

        DefaultUser user = new DefaultUser(orgUser.getUsername());
        user.setId(orgUser.getId());
        user.setAdditionalInformation(Map.of("accountTokenId", token.tokenId()));
        user.setRoles(orgUser.getRoles());
        return new UsernamePasswordAuthenticationToken(user, token.tokenId(),
                BearerTokenAuthenticator.authoritiesFromRoles(user.getRoles()));
    }

    private Single<User> applyTimeout(Single<User> src) {
        return blockingGetTimeoutMillis > 0
                ? src.timeout(blockingGetTimeoutMillis, TimeUnit.MILLISECONDS)
                : src;
    }
}
