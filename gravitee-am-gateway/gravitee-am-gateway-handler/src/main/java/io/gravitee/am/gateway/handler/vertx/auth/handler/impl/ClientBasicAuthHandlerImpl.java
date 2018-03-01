package io.gravitee.am.gateway.handler.vertx.auth.handler.impl;

import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.BasicAuthHandlerImpl;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientBasicAuthHandlerImpl extends BasicAuthHandlerImpl {

    public ClientBasicAuthHandlerImpl(AuthProvider authProvider) {
        super(authProvider, "gravitee-io");
    }

    @Override
    protected String authenticateHeader(RoutingContext context) {
        return "Basic realm=\"" + realm + "\"";
    }
}
