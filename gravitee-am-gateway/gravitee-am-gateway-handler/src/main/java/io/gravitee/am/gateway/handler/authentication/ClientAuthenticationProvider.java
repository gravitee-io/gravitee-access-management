package io.gravitee.am.gateway.handler.authentication;

import io.gravitee.am.gateway.handler.oauth2.auth.impl.Client;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientAuthenticationProvider implements AuthProvider {

    @Override
    public void authenticate(JsonObject jsonObject, Handler<AsyncResult<User>> authHandler) {
        authHandler.handle(Future.succeededFuture(new Client()));
    }
}
