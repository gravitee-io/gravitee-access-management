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
package io.gravitee.am.gateway.handler.oidc.service.idtoken;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.User;
import io.gravitee.gateway.api.ExecutionContext;
import io.reactivex.Single;

import java.util.Set;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface IDTokenService {

    /**
     * Set of claims to exclude from the IDToken
     */
    Set<String> EXCLUDED_CLAIMS = Set.of(
            ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY,
            ConstantKeys.OIDC_PROVIDER_ID_ACCESS_TOKEN_KEY
    );

    default Single<String>  create(OAuth2Request oAuth2Request, Client client, User user) {
        return create(oAuth2Request, client, user, null);
    }

    Single<String> create(OAuth2Request oAuth2Request, Client client, User user, ExecutionContext executionContext);

    Single<User> extractUser(String idToken, Client client);
}
