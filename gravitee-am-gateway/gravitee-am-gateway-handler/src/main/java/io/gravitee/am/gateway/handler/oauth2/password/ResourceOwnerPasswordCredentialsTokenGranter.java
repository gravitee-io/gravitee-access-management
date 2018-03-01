package io.gravitee.am.gateway.handler.oauth2.password;

import io.gravitee.am.gateway.handler.auth.EndUserAuthentication;
import io.gravitee.am.gateway.handler.auth.UserAuthenticationManager;
import io.gravitee.am.gateway.handler.oauth2.granter.AbstractTokenGranter;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;

/**
 * Implementation of the Authorization Code Grant Flow
 * See <a href="https://tools.ietf.org/html/rfc6749#section-4.3"></a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ResourceOwnerPasswordCredentialsTokenGranter extends AbstractTokenGranter {

    private final static String GRANT_TYPE = "password";

    private UserAuthenticationManager userAuthenticationManager;

    public ResourceOwnerPasswordCredentialsTokenGranter() {
        super(GRANT_TYPE);
    }

    @Override
    public Single<AccessToken> grant(TokenRequest tokenRequest) {
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>(tokenRequest.getRequestParameters());

        String username = parameters.getFirst("username");
        String password = parameters.getFirst("password");

        userAuthenticationManager.authenticate(tokenRequest.getClientId(), new EndUserAuthentication(username, password))
                .subscribe(new SingleObserver<Object>() {
            @Override
            public void onSubscribe(Disposable disposable) {

            }

            @Override
            public void onSuccess(Object o) {

            }

            @Override
            public void onError(Throwable throwable) {

            }
        });

        return super.grant(tokenRequest);
    }

    public void setUserAuthenticationManager(UserAuthenticationManager userAuthenticationManager) {
        this.userAuthenticationManager = userAuthenticationManager;
    }
}
