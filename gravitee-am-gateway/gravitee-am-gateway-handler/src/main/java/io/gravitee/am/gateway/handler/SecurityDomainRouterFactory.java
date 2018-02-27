package io.gravitee.am.gateway.handler;

import io.gravitee.am.gateway.handler.vertx.VertxSecurityDomainHandler;
import io.gravitee.am.model.Domain;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SecurityDomainRouterFactory {

    public static VertxSecurityDomainHandler create(Domain domain) {
        return new VertxSecurityDomainHandler();
    }
}
