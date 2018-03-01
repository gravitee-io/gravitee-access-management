package io.gravitee.am.gateway.handler.vertx;

import io.vertx.ext.web.WebTestBase;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class OAuth2TestBase extends WebTestBase {

    @Override
    public void setUp() throws Exception {
        super.setUp();

        VertxSecurityDomainHandler handler = new VertxSecurityDomainHandler();
        handler.setVertx(vertx);

        // Set OAuth2 handler
        router = handler.oauth2(router);
    }
}
