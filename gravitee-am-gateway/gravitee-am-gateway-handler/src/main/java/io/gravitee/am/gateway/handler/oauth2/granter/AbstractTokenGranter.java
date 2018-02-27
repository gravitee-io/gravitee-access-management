package io.gravitee.am.gateway.handler.oauth2.granter;

import io.gravitee.am.gateway.handler.oauth2.client.ClientService;
import io.gravitee.am.gateway.handler.oauth2.exception.UnauthorizedClientException;
import io.gravitee.am.gateway.handler.oauth2.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.token.AccessToken;
import io.gravitee.am.model.Client;
import io.reactivex.Single;
import io.reactivex.functions.Function;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AbstractTokenGranter implements TokenGranter {

    private final String grantType;

    private ClientService clientService;

    public AbstractTokenGranter(final String grantType) {
        this.grantType = grantType;
    }

    @Override
    public Single<AccessToken> grant(TokenRequest tokenRequest) {
        if (!this.grantType.equals(tokenRequest.getGrantType())) {
            return null;
        }

        return clientService.findByClientId(tokenRequest.getClientId())
                .map(new Function<Client, AccessToken>() {
            @Override
            public AccessToken apply(Client client) throws Exception {
                // Is client allowed to use such grant type ?
                if (client.getAuthorizedGrantTypes() != null && !client.getAuthorizedGrantTypes().isEmpty()
                        && !client.getAuthorizedGrantTypes().contains(grantType)) {
                    throw new UnauthorizedClientException("Unauthorized grant type: " + grantType);
                }

                return null;
            }
        }).toSingle();
    }

    public void setClientService(ClientService clientService) {
        this.clientService = clientService;
    }
}
