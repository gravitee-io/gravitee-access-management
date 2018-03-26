package io.gravitee.am.gateway.handler.auth.impl;

import io.gravitee.am.gateway.handler.auth.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.auth.exception.BadCredentialsException;
import io.gravitee.am.gateway.handler.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.User;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserAuthenticationManagerImpl implements UserAuthenticationManager {

    private final Logger logger = LoggerFactory.getLogger(UserAuthenticationManagerImpl.class);

    private ClientService clientService;

    private IdentityProviderManager identityProviderManager;

    @Override
    public Single<User> authenticate(String clientId, Authentication authentication) {
        logger.debug("Trying to authenticate [{}]", authentication);

        // TODO: look for a way to send a BadCredentialsException instead of a NoSuchElementException
        // lastorError() always throw a NoSuchElementException without a way to switch for an other exception type
        // Get identity providers associated to a client
        // For each idp, try to authenticate a user
        // Try to authenticate while the user can not be authenticated
        // If user can't be authenticated, send an exception
        return clientService.findByClientId(clientId)
                .flatMapObservable(client -> Observable.fromIterable(client.getIdentities()))
                .flatMapMaybe(authProvider -> identityProviderManager.get(authProvider))
                .flatMapMaybe(authenticationProvider -> {
                    User user = authenticationProvider.loadUserByUsername(authentication);
                    return (user == null) ? Maybe.empty() : Maybe.just(user);
                })
                .takeUntil((Predicate<User>) Objects::nonNull)
                .lastOrError()
                .flatMap(user -> {
                    if (user == null) {
                        return Single.error(new BadCredentialsException());
                    }
                    return Single.just(user);
                });

    }

    public void setClientService(ClientService clientService) {
        this.clientService = clientService;
    }

    public void setIdentityProviderManager(IdentityProviderManager identityProviderManager) {
        this.identityProviderManager = identityProviderManager;
    }
}
