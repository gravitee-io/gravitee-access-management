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
package io.gravitee.am.repository.mongodb.oauth2.token;

import io.gravitee.am.repository.mongodb.oauth2.utils.RequestTokenFactory;
import io.gravitee.am.repository.mongodb.oauth2.utils.TestAuthentication;
import io.gravitee.am.repository.oauth2.model.OAuth2AccessToken;
import io.gravitee.am.repository.oauth2.model.OAuth2Authentication;
import io.gravitee.am.repository.oauth2.model.OAuth2RefreshToken;
import io.gravitee.am.repository.oauth2.model.request.OAuth2Request;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.util.Collection;
import java.util.Date;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 *
 * TODO : check OAuth2Authentication equality
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MongoTokenRepositoryTestContextConfiguration.class, loader = AnnotationConfigContextLoader.class)
public class MongoTokenRepositoryTest {

    @Autowired
    private MongoTokenRepository mongoTokenRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @After
    public void tearDown() {
        mongoTemplate.getDb().dropDatabase();
    }

    @Test
    public void testStoreAccessToken() {
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(RequestTokenFactory.createOAuth2Request("id", false), new TestAuthentication("test2", false));
        OAuth2AccessToken expectedOAuth2AccessToken = new OAuth2AccessToken("testToken");
        mongoTokenRepository.storeAccessToken(expectedOAuth2AccessToken, expectedAuthentication);

        OAuth2AccessToken actualOAuth2AccessToken = mongoTokenRepository.readAccessToken("testToken").get();
        assertEquals(expectedOAuth2AccessToken.getValue(), actualOAuth2AccessToken.getValue());
        //assertEquals(expectedAuthentication, mongoTokenRepository.readAuthentication(expectedOAuth2AccessToken));
        mongoTokenRepository.removeAccessToken(expectedOAuth2AccessToken);
        assertFalse(mongoTokenRepository.readAccessToken("testToken").isPresent());
        assertFalse(mongoTokenRepository.readAuthentication(expectedOAuth2AccessToken.getValue()).isPresent());
    }

    @Test
    public void testStoreAccessTokenTwice() {
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(RequestTokenFactory.createOAuth2Request( "id", false), new TestAuthentication("test2", false));
        OAuth2AccessToken expectedOAuth2AccessToken = new OAuth2AccessToken("testToken");
        mongoTokenRepository.storeAccessToken(expectedOAuth2AccessToken, expectedAuthentication);
        mongoTokenRepository.storeAccessToken(expectedOAuth2AccessToken, expectedAuthentication);

        OAuth2AccessToken actualOAuth2AccessToken = mongoTokenRepository.readAccessToken("testToken").get();
        assertEquals(expectedOAuth2AccessToken.getValue(), actualOAuth2AccessToken.getValue());
        //assertEquals(expectedAuthentication, mongoTokenRepository.readAuthentication(expectedOAuth2AccessToken));
        mongoTokenRepository.removeAccessToken(expectedOAuth2AccessToken);
        assertFalse(mongoTokenRepository.readAccessToken("testToken").isPresent());
        assertFalse(mongoTokenRepository.readAuthentication(expectedOAuth2AccessToken.getValue()).isPresent());
    }

    @Test
    public void testRetrieveAccessToken() {
        //Test approved request
        OAuth2Request storedOAuth2Request = RequestTokenFactory.createOAuth2Request("id", true);
        OAuth2Authentication authentication = new OAuth2Authentication(storedOAuth2Request, new TestAuthentication("test2", true));
        OAuth2AccessToken expectedOAuth2AccessToken = new OAuth2AccessToken("testToken");
        expectedOAuth2AccessToken.setExpiration(new Date(Long.MAX_VALUE-1));
        mongoTokenRepository.storeAccessToken(expectedOAuth2AccessToken, authentication);

        //Test unapproved request
        storedOAuth2Request = RequestTokenFactory.createOAuth2Request("id", false);
        authentication = new OAuth2Authentication(storedOAuth2Request, new TestAuthentication("test2", true));
        OAuth2AccessToken actualOAuth2AccessToken = mongoTokenRepository.getAccessToken(authentication).get();
        assertEquals(expectedOAuth2AccessToken.getValue(), actualOAuth2AccessToken.getValue());
        //assertEquals(authentication.getUserAuthentication(), mongoTokenRepository.readAuthentication(expectedOAuth2AccessToken.getValue()).getUserAuthentication());
        // The authorizationRequest does not match because it is unapproved, but the token was granted to an approved request
        // assertFalse(storedOAuth2Request.equals(mongoTokenRepository.readAuthentication(expectedOAuth2AccessToken.getValue()).getOAuth2Request()));
        actualOAuth2AccessToken = mongoTokenRepository.getAccessToken(authentication).get();
        assertEquals(expectedOAuth2AccessToken.getValue(), actualOAuth2AccessToken.getValue());
        mongoTokenRepository.removeAccessToken(expectedOAuth2AccessToken);
        assertFalse(mongoTokenRepository.readAccessToken("testToken").isPresent());
        assertFalse(mongoTokenRepository.readAuthentication(expectedOAuth2AccessToken.getValue()).isPresent());
        assertFalse(mongoTokenRepository.getAccessToken(authentication).isPresent());
    }

    @Test
    public void testFindAccessTokensByClientIdAndUserName() {
        String clientId = "id" + UUID.randomUUID();
        String name = "test2" + UUID.randomUUID();
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(RequestTokenFactory.createOAuth2Request(clientId, false), new TestAuthentication(name, false));
        expectedAuthentication.setName(name);
        OAuth2AccessToken expectedOAuth2AccessToken = new OAuth2AccessToken("testToken");
        mongoTokenRepository.storeAccessToken(expectedOAuth2AccessToken, expectedAuthentication);

        Collection<OAuth2AccessToken> actualOAuth2AccessTokens = mongoTokenRepository.findTokensByClientIdAndUserName(clientId, name);
        assertEquals(1, actualOAuth2AccessTokens.size());
    }

    @Test
    public void testFindAccessTokensByClientId() {
        String clientId = "id" + UUID.randomUUID();
        String userName = "test2";
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(RequestTokenFactory.createOAuth2Request(clientId, false), new TestAuthentication(userName, false));
        expectedAuthentication.setName(userName);
        OAuth2AccessToken expectedOAuth2AccessToken = new OAuth2AccessToken("testToken");
        mongoTokenRepository.storeAccessToken(expectedOAuth2AccessToken, expectedAuthentication);

        Collection<OAuth2AccessToken> actualOAuth2AccessTokens = mongoTokenRepository.findTokensByClientId(clientId);
        assertEquals(1, actualOAuth2AccessTokens.size());
    }

    @Test
    public void testReadingAccessTokenForTokenThatDoesNotExist() {
        assertFalse(mongoTokenRepository.readAccessToken("tokenThatDoesNotExist").isPresent());
    }

    @Test
    public void testRefreshTokenIsNotStoredDuringAccessToken() {
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(RequestTokenFactory.createOAuth2Request("id", false), new TestAuthentication("test2", false));
        OAuth2AccessToken expectedOAuth2AccessToken = new OAuth2AccessToken("testToken");
        expectedOAuth2AccessToken.setRefreshToken(new OAuth2RefreshToken("refreshToken"));
        mongoTokenRepository.storeAccessToken(expectedOAuth2AccessToken, expectedAuthentication);

        OAuth2AccessToken actualOAuth2AccessToken = mongoTokenRepository.readAccessToken("testToken").get();
        assertNotNull(actualOAuth2AccessToken.getRefreshToken());

        assertFalse(mongoTokenRepository.readRefreshToken("refreshToken").isPresent());
    }

    @Test
    public void testStoreRefreshToken() {
        String refreshToken = "testToken" + UUID.randomUUID();
        OAuth2RefreshToken expectedRefreshToken = new OAuth2RefreshToken(refreshToken);
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(RequestTokenFactory.createOAuth2Request("id", false), new TestAuthentication("test2", false));
        mongoTokenRepository.storeRefreshToken(expectedRefreshToken, expectedAuthentication);

        OAuth2RefreshToken actualExpiringRefreshToken = mongoTokenRepository.readRefreshToken(refreshToken).get();
        assertEquals(expectedRefreshToken.getValue(), actualExpiringRefreshToken.getValue());
        //assertEquals(expectedAuthentication, mongoTokenRepository.readAuthenticationForRefreshToken(expectedRefreshToken));
        mongoTokenRepository.removeRefreshToken(expectedRefreshToken);
        assertFalse(mongoTokenRepository.readRefreshToken(refreshToken).isPresent());
        assertFalse(mongoTokenRepository.readAuthentication(expectedRefreshToken.getValue()).isPresent());
    }

    @Test
    public void testReadingRefreshTokenForTokenThatDoesNotExist() {
        assertFalse(mongoTokenRepository.readRefreshToken("tokenThatDoesNotExist").isPresent());
    }

    @Test
    public void testGetAccessTokenForDeletedUser() throws Exception {
        //Test approved request
        OAuth2Request storedOAuth2Request = RequestTokenFactory.createOAuth2Request("id", true);
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(storedOAuth2Request, new TestAuthentication("test", true));
        OAuth2AccessToken expectedOAuth2AccessToken = new OAuth2AccessToken("testToken");
        mongoTokenRepository.storeAccessToken(expectedOAuth2AccessToken, expectedAuthentication);
        assertEquals(expectedOAuth2AccessToken.getValue(), mongoTokenRepository.getAccessToken(expectedAuthentication).get().getValue());
        //assertEquals(expectedAuthentication, mongoTokenRepository.readAuthentication(expectedOAuth2AccessToken.getValue()));

        //Test unapproved request
        storedOAuth2Request = RequestTokenFactory.createOAuth2Request("id", false);
        OAuth2Authentication anotherAuthentication = new OAuth2Authentication(storedOAuth2Request, new TestAuthentication("test", true));
        assertEquals(expectedOAuth2AccessToken.getValue(), mongoTokenRepository.getAccessToken(anotherAuthentication).get().getValue());
        // The generated key for the authentication is the same as before, but the two auths are not equal. This could
        // happen if there are 2 users in a system with the same username, or (more likely), if a user account was
        // deleted and re-created.
        // assertEquals(anotherAuthentication.getUserAuthentication(), mongoTokenRepository.readAuthentication(expectedOAuth2AccessToken.getValue()).getUserAuthentication());
        // The authorizationRequest does not match because it is unapproved, but the token was granted to an approved request
        assertFalse(storedOAuth2Request.equals(mongoTokenRepository.readAuthentication(expectedOAuth2AccessToken.getValue()).get().getOAuth2Request()));
    }

    @Test
    public void testRemoveRefreshToken() {
        OAuth2RefreshToken expectedRefreshToken = new OAuth2RefreshToken("testToken");
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(RequestTokenFactory.createOAuth2Request("id", false), new TestAuthentication("test2", false));
        mongoTokenRepository.storeRefreshToken(expectedRefreshToken, expectedAuthentication);
        mongoTokenRepository.removeRefreshToken(expectedRefreshToken);

        assertFalse(mongoTokenRepository.readRefreshToken("testToken").isPresent());
    }

    @Test
    public void testRemovedTokenCannotBeFoundByUsername() {
        OAuth2AccessToken token = new OAuth2AccessToken("testToken");
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(RequestTokenFactory.createOAuth2Request(
                "id", false), new TestAuthentication("test2", false));
        mongoTokenRepository.storeAccessToken(token, expectedAuthentication);
        mongoTokenRepository.removeAccessToken(token);
        Collection<OAuth2AccessToken> tokens = mongoTokenRepository.findTokensByClientIdAndUserName("id", "test2");
        assertFalse(tokens.contains(token));
        assertTrue(tokens.isEmpty());
    }
}