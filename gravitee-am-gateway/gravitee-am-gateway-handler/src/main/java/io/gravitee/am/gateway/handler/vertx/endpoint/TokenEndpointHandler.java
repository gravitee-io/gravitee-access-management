package io.gravitee.am.gateway.handler.vertx.endpoint;

import io.gravitee.am.gateway.handler.oauth2.OAuth2ErrorResponse;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.granter.TokenGranter;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;

import java.util.List;

/**
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-3.2"></a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenEndpointHandler implements Handler<RoutingContext> {

    private TokenGranter tokenGranter;

    @Override
    public void handle(RoutingContext context) {
        // Check if a grant_type is defined
        List<String> grantTypeQueryParameters = context.queryParam("grant_type");
        if (grantTypeQueryParameters == null || grantTypeQueryParameters.isEmpty()) {
            throw new InvalidRequestException();
            /*
            context.response()
                    .setStatusCode(HttpStatusCode.BAD_REQUEST_400)
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8")
                    .end(Json.encodePrettily(new OAuth2ErrorResponse("invalid_request")));
                    */
        } else {
            context.next();
        }
    }

    public TokenGranter getTokenGranter() {
        return tokenGranter;
    }

    public void setTokenGranter(TokenGranter tokenGranter) {
        this.tokenGranter = tokenGranter;
    }
}
