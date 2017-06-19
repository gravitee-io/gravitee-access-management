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
package io.gravitee.am.gateway.handler.oauth2.provider.jwt;

import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.gateway.handler.oauth2.oidc.OIDCClaims;
import io.gravitee.am.gateway.handler.oauth2.provider.client.DelegateClientDetails;
import io.gravitee.am.gateway.handler.oauth2.provider.token.DefaultIntrospectionAccessTokenConverter;
import io.gravitee.am.gateway.handler.oauth2.security.CertificateManager;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.model.Client;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.jwt.JwtHelper;
import org.springframework.security.jwt.crypto.sign.RsaSigner;
import org.springframework.security.jwt.crypto.sign.Signer;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.common.util.JsonParser;
import org.springframework.security.oauth2.common.util.JsonParserFactory;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CustomJwtAccessTokenConverter extends JwtAccessTokenConverter implements InitializingBean {

    @Value("${oidc.iss:http://gravitee.am}")
    private String iss;

    @Autowired
    private KeyPair keyPair;

    @Autowired
    private ClientDetailsService clientService;

    @Autowired
    private CertificateManager certificateManager;

    private Signer signer;

    private JsonParser objectMapper = JsonParserFactory.create();

    private ClientDetails clientDetails;

    private static final int defaultIDTokenExpireIn = 14400;

    private static final String OPEN_ID = "openid";

    private static final String ID_TOKEN = "id_token";

    @Override
    public void afterPropertiesSet() throws Exception {
        this.setKeyPair(keyPair);
        PrivateKey privateKey = keyPair.getPrivate();
        signer = new RsaSigner((RSAPrivateKey) privateKey);
        this.setAccessTokenConverter(new DefaultIntrospectionAccessTokenConverter());
    }

    @Override
    public OAuth2AccessToken enhance(OAuth2AccessToken accessToken, OAuth2Authentication authentication) {
        // fetch client details
        setClientDetails(authentication.getOAuth2Request().getClientId());

        // enhance token
        if (authentication.getOAuth2Request().getScope() != null && authentication.getOAuth2Request().getScope().contains(OPEN_ID)) {
            return enhance0(accessToken, authentication);
        }
        return super.enhance(accessToken, authentication);
    }

    @Override
    protected String encode(OAuth2AccessToken accessToken, OAuth2Authentication authentication) {
        if (clientDetails == null || !(clientDetails instanceof DelegateClientDetails)) {
            return super.encode(accessToken, authentication);
        }

        // encode token with client certificate
        String content;
        try {
            content = objectMapper.formatMap(getAccessTokenConverter().convertAccessToken(accessToken, authentication));
        }
        catch (Exception e) {
            throw new IllegalStateException("Cannot convert access token to JSON", e);
        }

        Client client = ((DelegateClientDetails) clientDetails).getClient();
        if (client.getCertificate() != null) {
            CertificateProvider certificateProvider = certificateManager.get(client.getCertificate());
            if (certificateProvider != null) {
                return certificateProvider.sign(content);
            }
        }

        // default encoding
        return super.encode(accessToken, authentication);
    }

    private OAuth2AccessToken enhance0(OAuth2AccessToken accessToken, OAuth2Authentication authentication) {

        // create ID token
        Map<String, Object> IDToken = new HashMap<>();
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        IDToken.put(OIDCClaims.iss, iss);
        IDToken.put(OIDCClaims.sub, authentication.isClientOnly() ? ((String) authentication.getPrincipal()) : authentication.getName());
        IDToken.put(OIDCClaims.aud, authentication.getOAuth2Request().getClientId());
        IDToken.put(OIDCClaims.iat, calendar.getTimeInMillis() / 1000l);
        calendar.add(Calendar.SECOND, defaultIDTokenExpireIn);
        IDToken.put(OIDCClaims.exp, calendar.getTimeInMillis() / 1000l);

        // enhance ID token with OAuth client information
        CertificateProvider certificateProvider = null;
        if (clientDetails != null && clientDetails instanceof DelegateClientDetails) {
            Client client = ((DelegateClientDetails) clientDetails).getClient();
            calendar.setTimeInMillis((long) IDToken.get(OIDCClaims.iat) * 1000l);
            calendar.add(Calendar.SECOND, client.getIdTokenValiditySeconds());
            IDToken.put(OIDCClaims.exp, calendar.getTimeInMillis() / 1000l);
            // we only override claims for an end-user (grant_type != client_credentials)
            if (!authentication.isClientOnly()
                    && client.getIdTokenCustomClaims() != null
                    && authentication.getUserAuthentication().getPrincipal() instanceof User) {
                // retrieve user attributes
                User user = (User) authentication.getUserAuthentication().getPrincipal();
                client.getIdTokenCustomClaims().forEach((key, value) -> {
                    if (user.getAdditionalInformation().get(key) != null) {
                        IDToken.put(key, user.getAdditionalInformation().get(key));
                    }
                });
            }
            // get client certificate provider if any
            if (client.getCertificate() != null) {
                certificateProvider = certificateManager.get(client.getCertificate());
            }
        }

        // generate ID Token payload
        String payload = objectMapper.formatMap(IDToken);

        // encode ID Token
        if (certificateProvider != null) {
            payload = certificateProvider.sign(payload);
        } else {
            // default encoding
            payload = JwtHelper.encode(payload, signer).getEncoded();
        }

        // enhance access token
        Map<String, Object> additionalInformation = new HashMap<>(accessToken.getAdditionalInformation());
        additionalInformation.put(ID_TOKEN, payload);
        ((DefaultOAuth2AccessToken) accessToken).setAdditionalInformation(additionalInformation);

        return accessToken;
    }

    private void setClientDetails(String clientId) {
        try {
            clientDetails = clientService.loadClientByClientId(clientId);
        } catch (Exception ex) {
        }
    }
}
