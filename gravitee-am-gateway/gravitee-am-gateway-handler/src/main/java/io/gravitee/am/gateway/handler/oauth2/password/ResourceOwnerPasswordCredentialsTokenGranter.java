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
package io.gravitee.am.gateway.handler.oauth2.password;

import io.gravitee.am.gateway.handler.auth.EndUserAuthentication;
import io.gravitee.am.gateway.handler.auth.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.oauth2.granter.AbstractTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;

/**
 * Implementation of the Authorization Code Grant Flow
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.3"></a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ResourceOwnerPasswordCredentialsTokenGranter extends AbstractTokenGranter {

    private final static String GRANT_TYPE = "password";

    final static String USERNAME_PARAMETER = "username";
    final static String PASSWORD_PARAMETER = "password";

    private UserAuthenticationManager userAuthenticationManager;

    public ResourceOwnerPasswordCredentialsTokenGranter() {
        super(GRANT_TYPE);
    }

    @Override
    public Single<AccessToken> grant(TokenRequest tokenRequest) {
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>(tokenRequest.getRequestParameters());

        String username = parameters.getFirst(USERNAME_PARAMETER);
        String password = parameters.getFirst(PASSWORD_PARAMETER);

        userAuthenticationManager.authenticate(tokenRequest.getClientId(), new EndUserAuthentication(username, password))
                .subscribe(new SingleObserver<Object>() {
            @Override
            public void onSubscribe(Disposable disposable) {

            }

            @Override
            public void onSuccess(Object o) {

            }

            @Override
            public void onError(Throwable throwable) {

            }
        });

        return super.grant(tokenRequest);
    }

    public void setUserAuthenticationManager(UserAuthenticationManager userAuthenticationManager) {
        this.userAuthenticationManager = userAuthenticationManager;
    }
}
