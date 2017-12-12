/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.gateway.handler.oauth2.provider.endpoint;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2RefreshToken;
import org.springframework.security.oauth2.common.exceptions.InvalidGrantException;
import org.springframework.security.oauth2.common.exceptions.InvalidTokenException;
import org.springframework.security.oauth2.common.exceptions.OAuth2Exception;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.endpoint.FrameworkEndpoint;
import org.springframework.security.oauth2.provider.error.DefaultWebResponseExceptionTranslator;
import org.springframework.security.oauth2.provider.error.WebResponseExceptionTranslator;
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@FrameworkEndpoint
public class RevokeTokenEndpoint {

    @Autowired
    private ResourceServerTokenServices resourceServerTokenServices;

    @Autowired
    private TokenStore tokenStore;

    protected final Logger logger = LoggerFactory.getLogger(RevokeTokenEndpoint.class);

    private WebResponseExceptionTranslator exceptionTranslator = new DefaultWebResponseExceptionTranslator();

    public RevokeTokenEndpoint(TokenStore tokenStore, ResourceServerTokenServices resourceServerTokenServices) {
        this.tokenStore = tokenStore;
        this.resourceServerTokenServices = resourceServerTokenServices;
    }

    /**
     * @param exceptionTranslator the exception translator to set
     */
    public void setExceptionTranslator(WebResponseExceptionTranslator exceptionTranslator) {
        this.exceptionTranslator = exceptionTranslator;
    }

    @RequestMapping(value = "/oauth/revoke")
    @ResponseBody
    public ResponseEntity<Void> revokeToken(
            @RequestParam("token") String token,
            @RequestParam(value = "token_hint", required = false) final String tokenHint,
            final Principal principal) {
        logger.info("POST {}, /oauth/revoke; token = {}, tokenHint = {}", token, tokenHint);

        // Invalid token revocations (token does not exist) still respond
        // with HTTP 200. Still, log the result anyway for posterity.
        // See: https://tools.ietf.org/html/rfc7009#section-2.2
        if (!revokeToken(token, tokenHint, (Authentication) principal)) {
            logger.debug("No token with value {} was revoked.", token);
        }
        return new ResponseEntity<>(HttpStatus.OK);
    }

    private boolean revokeToken(final String token, final String tokenHint, final Authentication clientAuth) {
        logger.debug("revokeToken; token = {}, tokenHint = {}, clientAuth = {}", token, tokenHint, clientAuth);

        // Check the refresh_token store first. Fall back to the access token store if we don't
        // find anything. See RFC 7009, Sec 2.1: https://tools.ietf.org/html/rfc7009#section-2.1
        if (tokenHint != null && tokenHint.equals("refresh_token")) {
            return revokeRefreshToken(token, clientAuth) || revokeAccessToken(token, clientAuth);
        }

        // The user didn't hint that this is a refresh token, so it MAY be an access
        // token. If we don't find an access token... check if it's a refresh token.
        return revokeAccessToken(token, clientAuth) || revokeRefreshToken(token, clientAuth);
    }

    private boolean revokeRefreshToken(final String token, final Authentication clientAuth) {
        final OAuth2RefreshToken refreshToken = tokenStore.readRefreshToken(token);
        if (refreshToken != null) {
            logger.debug("Found refresh token {}.", token);
            final OAuth2Authentication authToRevoke = tokenStore.readAuthenticationForRefreshToken(refreshToken);
            checkIfTokenIsIssuedToClient(clientAuth, authToRevoke);
            tokenStore.removeAccessTokenUsingRefreshToken(refreshToken);
            tokenStore.removeRefreshToken(refreshToken);
            logger.debug("Successfully removed refresh token {} (and any associated access token).", refreshToken);
            return true;
        }

        logger.debug("No refresh token {} found in the token store.", token);
        return false;
    }

    private boolean revokeAccessToken(final String token, final Authentication clientAuth) {
        final OAuth2AccessToken accessToken = resourceServerTokenServices.readAccessToken(token);
        if (accessToken != null) {
            logger.debug("Found access token {}.", token);
            final OAuth2Authentication authToRevoke = resourceServerTokenServices.loadAuthentication(token);
            checkIfTokenIsIssuedToClient(clientAuth, authToRevoke);
            if (accessToken.getRefreshToken() != null) {
                tokenStore.removeRefreshToken(accessToken.getRefreshToken());
            }
            tokenStore.removeAccessToken(accessToken);
            logger.debug("Successfully removed access token {} (and any associated refresh token).", token);
            return true;
        }

        logger.debug("No access token {} found in the token store.", token);
        return false;
    }


    private void checkIfTokenIsIssuedToClient(final Authentication clientAuth,
                                              final OAuth2Authentication authToRevoke) {
        final String requestingClientId = clientAuth.getName();
        final String tokenClientId = authToRevoke.getOAuth2Request().getClientId();
        if (!requestingClientId.equals(tokenClientId)) {
            logger.debug("Revoke FAILED: requesting client = {}, token's client = {}.", requestingClientId, tokenClientId);
            throw new InvalidGrantException("Cannot revoke tokens issued to other clients.");
        }
        logger.debug("OK to revoke; token is issued to client \"{}\"", requestingClientId);
    }


    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<OAuth2Exception> handleException(Exception e) throws Exception {
        logger.info("Handling error: " + e.getClass().getSimpleName() + ", " + e.getMessage());
        // This isn't an oauth resource, so we don't want to send an
        // unauthorized code here. The client has already authenticated
        // successfully with basic auth and should just
        // get back the invalid token error.
        @SuppressWarnings("serial")
        InvalidTokenException e400 = new InvalidTokenException(e.getMessage()) {
            @Override
            public int getHttpErrorCode() {
                return 400;
            }
        };
        return exceptionTranslator.translate(e400);
    }
}