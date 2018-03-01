package io.gravitee.am.gateway.handler.vertx.auth.user;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Client implements User {

    private final String clientId;

    public Client(String clientId) {
        this.clientId = clientId;
    }

    public String getClientId() {
        return clientId;
    }

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
