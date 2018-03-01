package io.gravitee.am.gateway.handler.vertx.auth.handler;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.shiro.ShiroAuth;
import io.vertx.ext.auth.shiro.ShiroAuthRealmType;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.AuthHandlerTestBase;
import org.junit.Test;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientCredentialsAuthHandlerTest extends AuthHandlerTestBase {

    @Test
    public void shouldLoginSuccess() throws Exception {
        Handler<RoutingContext> handler = rc -> {
            assertNotNull(rc.user());
            assertEquals("my-client", rc.user().principal().getString("username"));
            rc.response().end();
        };

        JsonObject authConfig = new JsonObject().put("properties_path", "classpath:client/clientusers.properties");
        AuthProvider authProvider = ShiroAuth.create(vertx, ShiroAuthRealmType.PROPERTIES, authConfig);

        router.route("/token/*")
                .handler(ClientCredentialsAuthHandler.create(authProvider))
                .handler(handler);

        testRequest(HttpMethod.GET, "/token/", 401, "Unauthorized");

        // Now try again with credentials
        testRequest(HttpMethod.GET, "/token?client_id=my-client&client_secret=my-secret", resp -> {
            String wwwAuth = resp.headers().get("WWW-Authenticate");
            assertNull(wwwAuth);
        }, 200, "OK", null);
    }

    @Override
    protected AuthHandler createAuthHandler(AuthProvider authProvider) {
        return ClientCredentialsAuthHandler.create(authProvider);
    }
}
