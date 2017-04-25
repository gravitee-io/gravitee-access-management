package io.gravitee.am.gateway.handler.oauth2.provider.request;

import org.springframework.security.oauth2.common.util.OAuth2Utils;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.TokenGranter;
import org.springframework.security.oauth2.provider.TokenRequest;
import org.springframework.security.oauth2.provider.request.DefaultOAuth2RequestFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CustomOAuth2RequestFactory extends DefaultOAuth2RequestFactory {

    public CustomOAuth2RequestFactory(ClientDetailsService clientDetailsService) {
        super(clientDetailsService);
    }

    @Override
    public TokenRequest createTokenRequest(Map<String, String> requestParameters, ClientDetails authenticatedClient) {
        TokenRequest tokenRequest = super.createTokenRequest(requestParameters, authenticatedClient);

        Map<String, String> enhancedRequestParameters = new HashMap<>(tokenRequest.getRequestParameters());
        enhancedRequestParameters.put(OAuth2Utils.CLIENT_ID, authenticatedClient.getClientId());
        tokenRequest.setRequestParameters(enhancedRequestParameters);

        return tokenRequest;
    }

}
