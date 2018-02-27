package io.gravitee.am.gateway.handler.oauth2.auth.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;

/**
 * Created by david on 22/02/2018.
 */
public class Client implements User {
    @Override
    public User isAuthorized(String s, Handler<AsyncResult<Boolean>> handler) {
        return null;
    }

    @Override
    public User clearCache() {
        return null;
    }

    @Override
    public JsonObject principal() {
        return null;
    }

    @Override
    public void setAuthProvider(AuthProvider authProvider) {

    }
}
