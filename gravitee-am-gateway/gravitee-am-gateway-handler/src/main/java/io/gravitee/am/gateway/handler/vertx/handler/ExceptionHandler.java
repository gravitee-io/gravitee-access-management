package io.gravitee.am.gateway.handler.vertx.handler;

import io.gravitee.am.gateway.handler.oauth2.exception.OAuth2Exception;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ExceptionHandler implements Handler<RoutingContext> {

    @Override
    public void handle(RoutingContext routingContext) {
        if (routingContext.failed()) {
            Throwable throwable = routingContext.failure();
            if (throwable instanceof OAuth2Exception) {
                OAuth2Exception oAuth2Exception = (OAuth2Exception) throwable;
                routingContext
                        .response()
                        .setStatusCode(oAuth2Exception.getHttpStatusCode())
                        .end();
            } else {
                if (routingContext.statusCode() != -1) {
                    routingContext.response().end();
                } else {
                    routingContext
                            .response()
                            .setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR_500)
                            .end();
                }
            }
        }
    }
}
