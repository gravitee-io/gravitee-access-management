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
package io.gravitee.am.identityprovider.github.authentication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.identityprovider.api.Authentication;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2AuthenticationProvider;
import io.gravitee.am.identityprovider.api.oauth2.OAuth2IdentityProviderConfiguration;
import io.gravitee.am.identityprovider.github.authentication.spring.GithubAuthenticationProviderConfiguration;
import io.gravitee.am.identityprovider.github.model.GithubUser;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Import(GithubAuthenticationProviderConfiguration.class)
public class GithubAuthenticationProvider implements OAuth2AuthenticationProvider {

    private static final Logger logger = LoggerFactory.getLogger(GithubAuthenticationProvider.class);
    private static final String CLIENT_ID = "client_id";
    private static final String REDIRECT_URI = "redirect_uri";
    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private HttpClient client;

    @Autowired
    private OAuth2IdentityProviderConfiguration configuration;

    @Override
    public User loadUserByUsername(Authentication authentication) {
        try {
            HttpPost post = new HttpPost(configuration.getAccessTokenUri());
            List<NameValuePair> urlParameters = new ArrayList<>();
            urlParameters.add(new BasicNameValuePair(CLIENT_ID, configuration.getClientId()));
            urlParameters.add(new BasicNameValuePair("client_secret", configuration.getClientSecret()));
            urlParameters.add(new BasicNameValuePair(REDIRECT_URI, (String) authentication.getAdditionalInformation().get(REDIRECT_URI)));
            urlParameters.add(new BasicNameValuePair("code", (String) authentication.getCredentials()));
            post.setEntity(new UrlEncodedFormEntity(urlParameters));

            // authenticate user
            HttpResponse response = client.execute(post);
            BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            String content = read(rd);
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new BadCredentialsException(content);
            }
            Map<String, String> params = extractMap(content);
            String accessToken = params.get("access_token");

            // get user profile
            HttpGet request = new HttpGet(configuration.getUserProfileUri());
            request.addHeader("Authorization", "token " + accessToken);
            response = client.execute(request);
            rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            content = read(rd);
            JsonNode jsonNode = objectMapper.readTree(content);
            return createUser(jsonNode);
        } catch (Exception e) {
            logger.error("Fail to authenticate github user account", e);
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
        User user = new DefaultUser(jsonNode.get(GithubUser.LOGIN).asText());
        // set additional information
        Map<String, Object> additionalInformation = new HashMap<>();
        additionalInformation.put("sub", jsonNode.get(GithubUser.LOGIN).asText());
        additionalInformation.put(GithubUser.AVATAR_URL, jsonNode.get(GithubUser.AVATAR_URL).asText());
        additionalInformation.put(GithubUser.GRAVATAR_ID, jsonNode.get(GithubUser.GRAVATAR_ID).asText());
        additionalInformation.put(GithubUser.URL, jsonNode.get(GithubUser.URL).asText());
        additionalInformation.put(GithubUser.HTML_URL, jsonNode.get(GithubUser.HTML_URL).asText());
        additionalInformation.put(GithubUser.FOLLOWERS_URL, jsonNode.get(GithubUser.FOLLOWERS_URL).asText());
        additionalInformation.put(GithubUser.FOLLOWING_URL, jsonNode.get(GithubUser.FOLLOWING_URL).asText());
        additionalInformation.put(GithubUser.GISTS_URL, jsonNode.get(GithubUser.GISTS_URL).asText());
        additionalInformation.put(GithubUser.STARRED_URL, jsonNode.get(GithubUser.STARRED_URL).asText());
        additionalInformation.put(GithubUser.SUBSCRIPTIONS_URL, jsonNode.get(GithubUser.SUBSCRIPTIONS_URL).asText());
        additionalInformation.put(GithubUser.ORGANIZATIONS_URL, jsonNode.get(GithubUser.ORGANIZATIONS_URL).asText());
        additionalInformation.put(GithubUser.REPOS_URL, jsonNode.get(GithubUser.REPOS_URL).asText());
        additionalInformation.put(GithubUser.EVENTS_URL, jsonNode.get(GithubUser.EVENTS_URL).asText());
        additionalInformation.put(GithubUser.RECEIVED_EVENTS_URL, jsonNode.get(GithubUser.RECEIVED_EVENTS_URL).asText());
        additionalInformation.put(GithubUser.SITE_ADMIN, jsonNode.get(GithubUser.SITE_ADMIN).asText());
        additionalInformation.put(GithubUser.NAME, jsonNode.get(GithubUser.NAME).asText());
        additionalInformation.put(GithubUser.COMPANY, jsonNode.get(GithubUser.COMPANY).asText());
        additionalInformation.put(GithubUser.LOCATION, jsonNode.get(GithubUser.LOCATION).asText());
        additionalInformation.put(GithubUser.EMAIL, jsonNode.get(GithubUser.EMAIL).asText());
        additionalInformation.put(GithubUser.PUBLIC_REPOS, jsonNode.get(GithubUser.PUBLIC_REPOS).asText());
        additionalInformation.put(GithubUser.PUBLIC_GISTS, jsonNode.get(GithubUser.PUBLIC_GISTS).asText());
        additionalInformation.put(GithubUser.FOLLOWERS, jsonNode.get(GithubUser.FOLLOWERS).asText());
        additionalInformation.put(GithubUser.FOLLOWING, jsonNode.get(GithubUser.FOLLOWING).asText());
        additionalInformation.put(GithubUser.LOCATION, jsonNode.get(GithubUser.LOCATION).asText());
        additionalInformation.put(GithubUser.EMAIL, jsonNode.get(GithubUser.EMAIL).asText());
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
