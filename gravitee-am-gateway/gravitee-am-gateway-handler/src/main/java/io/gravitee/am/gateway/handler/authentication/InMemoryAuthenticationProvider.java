package io.gravitee.am.gateway.handler.authentication;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AbstractUser;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;

import java.util.HashMap;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class InMemoryAuthenticationProvider implements AuthProvider {
    private final Map<String, User> users = new HashMap<>();

    public InMemoryAuthenticationProvider() {

    }


    public static InMemoryAuthenticationProvider create() {
        return new InMemoryAuthenticationProvider();
    }

    @Override
    public void authenticate(JsonObject authInfo, Handler<AsyncResult<User>> resultHandler) {
        String username = authInfo.getString("username");
        User user = users.get(username);
        if (user == null) {
            resultHandler.handle(Future.failedFuture("couldn't find username: " + username));
            return;
        }
        String password = user.principal().getString("password", "");
        if (!authInfo.getString("password", "").equals(password)) {
            resultHandler.handle(Future.failedFuture("incorrect password"));
        } else {
            resultHandler.handle(Future.succeededFuture(user));
        }
    }

    public static class InMemoryUser extends AbstractUser {
        private final JsonObject user;

        public InMemoryUser(JsonObject user) {
            this.user = user;
        }

        @Override
        protected void doIsPermitted(String permission, Handler<AsyncResult<Boolean>> resultHandler) {
            resultHandler.handle(Future.succeededFuture(true));
        }

        @Override
        public JsonObject principal() {
            return user;
        }

        @Override
        public void setAuthProvider(AuthProvider authProvider) {
        }
    }
}