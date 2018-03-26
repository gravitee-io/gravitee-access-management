package io.gravitee.am.gateway.handler.vertx.endpoint;

import io.gravitee.am.gateway.handler.vertx.auth.user.Client;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidClientException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidRequestException;
import io.gravitee.am.gateway.handler.oauth2.granter.TokenGranter;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.vertx.request.TokenRequestFactory;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.vertx.core.Handler;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;

/**
 *
 * See <a href="https://tools.ietf.org/html/rfc6749#section-3.2"></a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TokenEndpointHandler implements Handler<RoutingContext> {

    private TokenGranter tokenGranter;

    private final TokenRequestFactory tokenRequestFactory = new TokenRequestFactory();

    @Override
    public void handle(RoutingContext context) {
        TokenRequest tokenRequest = tokenRequestFactory.create(context.request());

        User authenticatedUser = context.user();
        if (authenticatedUser == null || ! (authenticatedUser instanceof Client)) {
            throw new InvalidClientException();
        }

        // Check if a grant_type is defined
        if (tokenRequest.getGrantType() == null) {
            throw new InvalidRequestException();
        }

        Client client = (Client) authenticatedUser;

        // Check that authenticated user is matching the client_id
        if (! client.getClientId().equals(tokenRequest.getClientId())) {
            throw new InvalidClientException();
        }

        tokenGranter.grant(tokenRequest)
                .subscribe(new SingleObserver<AccessToken>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onSuccess(AccessToken accessToken) {
                        context.response().end(accessToken.toString());
                    }

                    @Override
                    public void onError(Throwable e) {
                        //TODO: What is the correct status code in case of access token error ?
                        context.response().setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR_500).end();
                    }
                });
    }

    public TokenGranter getTokenGranter() {
        return tokenGranter;
    }

    public void setTokenGranter(TokenGranter tokenGranter) {
        this.tokenGranter = tokenGranter;
    }
}
