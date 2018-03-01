package io.gravitee.am.gateway.handler.vertx.endpoint;

import io.gravitee.am.gateway.handler.vertx.OAuth2TestBase;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenEndpointHandlerTest extends OAuth2TestBase {

    @Test
    public void shouldNotInvokeTokenEndpoint_noClient() throws Exception {
        testRequest(
                HttpMethod.POST, "/oauth/token",
                HttpStatusCode.UNAUTHORIZED_401, "Unauthorized");
    }

    @Test
    public void shouldInvokeTokenEndpoint_withClientCredentials() throws Exception {
        testRequest(
                HttpMethod.POST, "/oauth/token?client_id=my-client&client_secret=my-secret",
                HttpStatusCode.OK_200, "OK");
    }

    @Test
    public void shouldNotInvokeTokenEndpoint_withClientCredentials() throws Exception {
        testRequest(HttpMethod.POST, "/oauth/token", 400, "Bad Request");
    }

    @Test
    public void should() throws Exception {
        testRequest(HttpMethod.POST, "/oauth/token?grant_type=xxxxx", 200, "OK");
    }

}
