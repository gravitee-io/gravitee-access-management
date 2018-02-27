package io.gravitee.am.gateway.handler.oauth2.granter;

import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Single;
import io.reactivex.functions.Function;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CompositeTokenGranter implements TokenGranter {

    private final List<TokenGranter> tokenGranters;

    public CompositeTokenGranter(List<TokenGranter> tokenGranters) {
        this.tokenGranters = new ArrayList<>(tokenGranters);
    }

    public Single<AccessToken> grant(TokenRequest tokenRequest) {
        return Observable
                .fromIterable(tokenGranters)
                .flatMap(new Function<TokenGranter, ObservableSource<? extends AccessToken>>() {
                            @Override
                            public ObservableSource<? extends AccessToken> apply(TokenGranter tokenGranter) throws Exception {
                                return null; //tokenGranter.grant(tokenRequest);
                            }
                        })
                .singleOrError();
    }

    public void addTokenGranter(TokenGranter tokenGranter) {
        Objects.requireNonNull(tokenGranter);

        tokenGranters.add(tokenGranter);
    }
}
