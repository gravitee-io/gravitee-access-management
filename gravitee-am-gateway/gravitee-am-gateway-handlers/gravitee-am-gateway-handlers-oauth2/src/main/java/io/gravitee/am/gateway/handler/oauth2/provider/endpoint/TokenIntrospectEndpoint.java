package io.gravitee.am.gateway.handler.oauth2.provider.endpoint;

import io.gravitee.am.gateway.handler.oauth2.provider.token.DefaultIntrospectionAccessTokenConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.endpoint.FrameworkEndpoint;
import org.springframework.security.oauth2.provider.token.AccessTokenConverter;
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
//@Controller
public class TokenIntrospectEndpoint {
    private ResourceServerTokenServices resourceServerTokenServices;
    private AccessTokenConverter accessTokenConverter = new DefaultIntrospectionAccessTokenConverter();

    protected final Logger logger = LoggerFactory.getLogger(TokenIntrospectEndpoint.class);

    public TokenIntrospectEndpoint(ResourceServerTokenServices resourceServerTokenServices) {
        this.resourceServerTokenServices = resourceServerTokenServices;
    }

    /**
     * @param accessTokenConverter the accessTokenConverter to set
     */
    public void setAccessTokenConverter(AccessTokenConverter accessTokenConverter) {
        this.accessTokenConverter = accessTokenConverter;
    }

    @RequestMapping(value = "/introspect")
    @ResponseBody
    public Map<String, ?> introspectToken(@RequestParam("token") String value,
                                          @RequestParam(value = "resource_id", required = false) String resourceId,
                                          @RequestParam(value = "token_type_hint", required = false) String tokenType) {

        OAuth2AccessToken token = resourceServerTokenServices.readAccessToken(value);
        if (token == null || token.isExpired()) {
            Map<String, Object> response = new HashMap<>();
            response.put("active",false);
            return response;
        }

        OAuth2Authentication authentication = resourceServerTokenServices.loadAuthentication(token.getValue());

        return accessTokenConverter.convertAccessToken(token, authentication);
    }
}