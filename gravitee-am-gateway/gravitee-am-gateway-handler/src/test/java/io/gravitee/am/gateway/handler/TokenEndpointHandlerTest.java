package io.gravitee.am.gateway.handler;

import io.gravitee.am.gateway.handler.vertx.endpoint.TokenEndpointHandler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.WebTestBase;
import org.junit.Test;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenEndpointHandlerTest extends WebTestBase {

    @Test
    public void shouldGetBadRequest_invalid_request() throws Exception {
        router.route().handler(new TokenEndpointHandler());

        testRequest(HttpMethod.POST, "/", 400, "Bad Request");
    }

    @Test
    public void should() throws Exception {
        router.route().handler(new TokenEndpointHandler());
        router.route().handler(rc -> { rc.response().end(); });
        testRequest(HttpMethod.POST, "/?grant_type=xxxxx", 200, "OK");
    }

}
