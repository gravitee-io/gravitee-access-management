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
package io.gravitee.am.identityprovider.oauth2.authentication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2AuthenticationProvider;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2IdentityProviderConfiguration;
import io.gravitee.am.identityprovider.oauth2.OAuth2GenericIdentityProviderMapper;
import io.gravitee.am.identityprovider.oauth2.authentication.spring.OAuth2GenericAuthenticationProviderConfiguration;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.oauth2.common.util.OAuth2Utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(OAuth2GenericAuthenticationProviderConfiguration.class)
public class OAuth2GenericAuthenticationProvider implements OAuth2AuthenticationProvider {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2GenericAuthenticationProvider.class);
    private static final String CLAIMS_SUB = "sub";
    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private HttpClient client;

    @Autowired
    private OAuth2IdentityProviderConfiguration configuration;

    @Autowired
    private OAuth2GenericIdentityProviderMapper mapper;

    @Override
    public User loadUserByUsername(Authentication authentication) {
        try {
            HttpPost post = new HttpPost(configuration.getAccessTokenUri());
            List<NameValuePair> urlParameters = new ArrayList<>();
            urlParameters.add(new BasicNameValuePair(OAuth2Utils.CLIENT_ID, configuration.getClientId()));
            urlParameters.add(new BasicNameValuePair("client_secret", configuration.getClientSecret()));
            urlParameters.add(new BasicNameValuePair(OAuth2Utils.REDIRECT_URI, (String) authentication.getAdditionalInformation().get(OAuth2Utils.REDIRECT_URI)));
            urlParameters.add(new BasicNameValuePair("code", (String) authentication.getCredentials()));
            urlParameters.add(new BasicNameValuePair(OAuth2Utils.GRANT_TYPE, "authorization_code"));
            post.setEntity(new UrlEncodedFormEntity(urlParameters));

            // authenticate user
            HttpResponse response = client.execute(post);
            BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            String content = read(rd);
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new BadCredentialsException(content);
            }
            JsonNode params = objectMapper.readTree(content);
            String accessToken = params.get("access_token").asText();

            // get user profile
            HttpGet request = new HttpGet(configuration.getUserProfileUri());
            request.addHeader("Authorization", "Bearer " + accessToken);
            response = client.execute(request);
            rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            content = read(rd);
            JsonNode jsonNode = objectMapper.readTree(content);
            return createUser(jsonNode);
        } catch (Exception e) {
            logger.error("Fail to authenticate OAuth 2.0 generic user account", e);
            throw new InternalAuthenticationServiceException(e.getMessage());
        }
    }

    @Override
    public User loadUserByUsername(String username) {
        return null;
    }

    @Override
    public OAuth2IdentityProviderConfiguration configuration() {
        return configuration;
    }

    private User createUser(JsonNode jsonNode) {
        User user = new DefaultUser(jsonNode.get(CLAIMS_SUB).asText());
        // set additional information
        Map<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("sub", jsonNode.get(CLAIMS_SUB).asText());
        if (this.mapper.getMappers() != null) {
            this.mapper.getMappers().forEach((k, v) -> {
                if (jsonNode.get(v) != null) {
                    additionalInformation.put(k, jsonNode.get(v).asText());
                }
            });
        }
        ((DefaultUser) user).setAdditonalInformation(additionalInformation);
        return user;
    }

    private String read(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    private Map<String, String> extractMap(String param) {
        Map<String, String> query_pairs = new LinkedHashMap<>();
        String[] pairs = param.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            query_pairs.put(pair.substring(0, idx), pair.substring(idx + 1));
        }
        return query_pairs;

    }
}
