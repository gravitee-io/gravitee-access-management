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
package io.gravitee.am.gateway.handler.vertx.auth.provider;

import io.gravitee.am.gateway.handler.auth.EndUserAuthentication;
import io.gravitee.am.gateway.handler.auth.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserAuthenticationProvider implements AuthProvider {

    private final static String USERNAME_PARAMETER = "username";
    private final static String PASSWORD_PARAMETER = "password";
    private UserAuthenticationManager userAuthenticationManager;

    public UserAuthenticationProvider() {}

    public UserAuthenticationProvider(UserAuthenticationManager userAuthenticationManager) {
        this.userAuthenticationManager = userAuthenticationManager;
    }

    @Override
    public void authenticate(JsonObject authInfo, Handler<AsyncResult<User>> resultHandler) {
        String username = authInfo.getString(USERNAME_PARAMETER);
        String password = authInfo.getString(PASSWORD_PARAMETER);
        String clientId = authInfo.getString(OAuth2Constants.CLIENT_ID);

        userAuthenticationManager.authenticate(clientId, new EndUserAuthentication(username, password))
                .subscribe(new SingleObserver<io.gravitee.am.identityprovider.api.User>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onSuccess(io.gravitee.am.identityprovider.api.User user) {
                        resultHandler.handle(Future.succeededFuture(new io.gravitee.am.gateway.handler.vertx.auth.user.User(user)));
                    }

                    @Override
                    public void onError(Throwable e) {
                        resultHandler.handle(Future.failedFuture(e));
                    }
                });
    }

    public void setUserAuthenticationManager(UserAuthenticationManager userAuthenticationManager) {
        this.userAuthenticationManager = userAuthenticationManager;
    }
}
