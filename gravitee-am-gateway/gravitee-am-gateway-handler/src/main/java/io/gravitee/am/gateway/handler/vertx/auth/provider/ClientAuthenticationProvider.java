package io.gravitee.am.gateway.handler.vertx.auth.provider;

import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.exception.BadClientCredentialsException;
import io.gravitee.am.gateway.handler.vertx.auth.user.Client;
import io.reactivex.MaybeObserver;
import io.reactivex.disposables.Disposable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ClientAuthenticationProvider implements AuthProvider {

    private final Logger logger = LoggerFactory.getLogger(ClientAuthenticationProvider.class);

    private ClientService clientService;

    @Override
    public void authenticate(JsonObject credentials, Handler<AsyncResult<User>> authHandler) {
        String clientId = credentials.getString("username");
        String clientSecret = credentials.getString("password");

        logger.debug("Trying to authenticate a client: clientId[{}]", clientId);

        clientService.findByClientId(clientId)
                .subscribe(new MaybeObserver<io.gravitee.am.model.Client>() {
                    @Override
                    public void onSubscribe(Disposable disposable) {
                    }

                    @Override
                    public void onSuccess(io.gravitee.am.model.Client client) {
                        if (client.getClientSecret().equals(clientSecret)) {
                            authHandler.handle(Future.succeededFuture(new Client(client.getClientId())));
                        } else {
                            authHandler.handle(Future.failedFuture(new BadClientCredentialsException()));
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        logger.error("Unexpected error while looking for a client: clientId[{}]", clientId, throwable);
                        authHandler.handle(Future.failedFuture(throwable));
                    }

                    @Override
                    public void onComplete() {
                        authHandler.handle(Future.failedFuture(new BadClientCredentialsException()));
                    }
                });
    }

    public void setClientService(ClientService clientService) {
        this.clientService = clientService;
    }
}
