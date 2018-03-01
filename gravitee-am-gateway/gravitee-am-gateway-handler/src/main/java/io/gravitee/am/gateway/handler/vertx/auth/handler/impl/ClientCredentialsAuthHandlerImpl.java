package io.gravitee.am.gateway.handler.vertx.auth.handler.impl;

import io.gravitee.am.gateway.handler.oauth2.utils.OAuth2Constants;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.AuthHandlerImpl;
import io.vertx.ext.web.handler.impl.HttpStatusException;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientCredentialsAuthHandlerImpl extends AuthHandlerImpl {

    private static final HttpStatusException UNAUTHORIZED = new HttpStatusException(401);

    public ClientCredentialsAuthHandlerImpl(AuthProvider authProvider) {
        super(authProvider);
    }

    @Override
    public void parseCredentials(RoutingContext context, Handler<AsyncResult<JsonObject>> handler) {
        String clientId = context.request().getParam(OAuth2Constants.CLIENT_ID);
        String clientSecret = context.request().getParam(OAuth2Constants.CLIENT_SECRET);

        if (clientId != null && clientSecret != null) {
            handler.handle(Future.succeededFuture(
                    new JsonObject().put("username", clientId).put("password", clientSecret)));
        } else {
            handler.handle(Future.failedFuture(UNAUTHORIZED));
        }
    }
}
