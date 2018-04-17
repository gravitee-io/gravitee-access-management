package io.gravitee.am.gateway.handler.oauth2.introspection.impl;

import io.gravitee.am.gateway.handler.oauth2.introspection.IntrospectionRequest;
import io.gravitee.am.gateway.handler.oauth2.introspection.IntrospectionResponse;
import io.gravitee.am.gateway.handler.oauth2.introspection.IntrospectionService;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.gateway.handler.oauth2.token.TokenService;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.functions.Function;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class IntrospectionServiceImpl implements IntrospectionService {

    @Autowired
    private TokenService tokenService;

    @Override
    public Single<IntrospectionResponse> introspect(IntrospectionRequest introspectionRequest) {
        Maybe<AccessToken> token = tokenService.get(introspectionRequest.getToken()).cache();
        return token
                .isEmpty()
                .flatMap(empty -> {
                    if (empty) {
                        return Single.just(new IntrospectionResponse());
                    } else {
                        //TODO implement it !
                        return token.flatMapSingle(new Function<AccessToken, SingleSource<IntrospectionResponse>>() {
                            @Override
                            public SingleSource<IntrospectionResponse> apply(AccessToken accessToken) throws Exception {
                                IntrospectionResponse introspectionResponse = new IntrospectionResponse();
                                introspectionResponse.setActive(true);
                                introspectionResponse.setScope(accessToken.getScope());

                                return Single.just(introspectionResponse);
                            }
                        });
                    }
                });
    }

    public void setTokenService(TokenService tokenService) {
        this.tokenService = tokenService;
    }
}
