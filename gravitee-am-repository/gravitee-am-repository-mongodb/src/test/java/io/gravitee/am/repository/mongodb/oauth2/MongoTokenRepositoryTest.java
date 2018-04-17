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
package io.gravitee.am.repository.mongodb.oauth2;

import io.gravitee.am.repository.mongodb.oauth2.utils.RequestTokenFactory;
import io.gravitee.am.repository.mongodb.oauth2.utils.TestAuthentication;
import io.gravitee.am.repository.oauth2.model.OAuth2AccessToken;
import io.gravitee.am.repository.oauth2.model.OAuth2Authentication;
import io.gravitee.am.repository.oauth2.model.OAuth2RefreshToken;
import io.gravitee.am.repository.oauth2.model.request.OAuth2Request;
import io.reactivex.observers.TestObserver;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 *
 * TODO : check OAuth2Authentication equality
 */
public class MongoTokenRepositoryTest extends AbstractOAuth2RepositoryTest {

    @Autowired
    private MongoTokenRepository mongoTokenRepository;

    @Test
    public void testStoreAccessToken() {
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(RequestTokenFactory.createOAuth2Request("id", false), new TestAuthentication("test2", false));
        OAuth2AccessToken expectedOAuth2AccessToken = new OAuth2AccessToken("testToken");
        mongoTokenRepository.storeAccessToken(expectedOAuth2AccessToken, expectedAuthentication, "test2").blockingGet();

        TestObserver<OAuth2AccessToken> testObserver = mongoTokenRepository.readAccessToken("testToken").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(actualOAuth2AccessToken -> expectedOAuth2AccessToken.getValue().equals(actualOAuth2AccessToken.getValue()));
        //assertEquals(expectedAuthentication, mongoTokenRepository.readAuthentication(expectedOAuth2AccessToken));
        mongoTokenRepository.removeAccessToken(expectedOAuth2AccessToken).test().awaitTerminalEvent();

        mongoTokenRepository.readAccessToken("testToken").test().assertEmpty();
        mongoTokenRepository.readAuthentication(expectedOAuth2AccessToken.getValue()).test().assertEmpty();
    }

    @Test
    public void testStoreAccessTokenTwice() {
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(RequestTokenFactory.createOAuth2Request( "id", false), new TestAuthentication("test2", false));
        OAuth2AccessToken expectedOAuth2AccessToken = new OAuth2AccessToken("testToken");
        mongoTokenRepository.storeAccessToken(expectedOAuth2AccessToken, expectedAuthentication, "test2").blockingGet();
        mongoTokenRepository.storeAccessToken(expectedOAuth2AccessToken, expectedAuthentication, "test2").blockingGet();

        TestObserver<OAuth2AccessToken> testObserver = mongoTokenRepository.readAccessToken("testToken").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(actualOAuth2AccessToken -> expectedOAuth2AccessToken.getValue().equals(actualOAuth2AccessToken.getValue()));
        //assertEquals(expectedAuthentication, mongoTokenRepository.readAuthentication(expectedOAuth2AccessToken));
        mongoTokenRepository.removeAccessToken(expectedOAuth2AccessToken).test().awaitTerminalEvent();

        mongoTokenRepository.readAccessToken("testToken").test().assertEmpty();
        mongoTokenRepository.readAuthentication(expectedOAuth2AccessToken.getValue()).test().assertEmpty();
    }


    @Test
    public void testRetrieveAccessToken() {
        //Test approved request
        OAuth2Request storedOAuth2Request = RequestTokenFactory.createOAuth2Request("id", true);
        OAuth2Authentication authentication = new OAuth2Authentication(storedOAuth2Request, new TestAuthentication("test2", true));
        OAuth2AccessToken expectedOAuth2AccessToken = new OAuth2AccessToken("testToken");
        expectedOAuth2AccessToken.setExpiration(new Date(Long.MAX_VALUE-1));
        mongoTokenRepository.storeAccessToken(expectedOAuth2AccessToken, authentication, "test2").blockingGet();

        //Test unapproved request
        TestObserver<OAuth2AccessToken> testObserver = mongoTokenRepository.getAccessToken("test2").test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(actualOAuth2AccessToken -> expectedOAuth2AccessToken.getValue().equals(actualOAuth2AccessToken.getValue()));
        //assertEquals(authentication.getUserAuthentication(), mongoTokenRepository.readAuthentication(expectedOAuth2AccessToken.getValue()).getUserAuthentication());
        // The authorizationRequest does not match because it is unapproved, but the token was granted to an approved request
        // assertFalse(storedOAuth2Request.equals(mongoTokenRepository.readAuthentication(expectedOAuth2AccessToken.getValue()).getOAuth2Request()));
        mongoTokenRepository.removeAccessToken(expectedOAuth2AccessToken).test().awaitTerminalEvent();
        mongoTokenRepository.readAccessToken("testToken").test().assertEmpty();
        mongoTokenRepository.readAuthentication(expectedOAuth2AccessToken.getValue()).test().assertEmpty();
        mongoTokenRepository.getAccessToken("test2").test().assertEmpty();
    }

    @Test
    public void testFindAccessTokensByClientIdAndUserName() {
        String clientId = "id" + UUID.randomUUID();
        String name = "test2" + UUID.randomUUID();
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(RequestTokenFactory.createOAuth2Request(clientId, false), new TestAuthentication(name, false));
        expectedAuthentication.setName(name);
        OAuth2AccessToken expectedOAuth2AccessToken = new OAuth2AccessToken("testToken");
        mongoTokenRepository.storeAccessToken(expectedOAuth2AccessToken, expectedAuthentication, "test2").blockingGet();

        TestObserver<List<OAuth2AccessToken>> testObserver = mongoTokenRepository.findTokensByClientIdAndUserName(clientId, name).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
        testObserver.assertValue(l -> l.get(0).getValue().equals("testToken"));
    }

    @Test
    public void testFindAccessTokensByClientId() {
        String clientId = "id" + UUID.randomUUID();
        String userName = "test2";
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(RequestTokenFactory.createOAuth2Request(clientId, false), new TestAuthentication(userName, false));
        expectedAuthentication.setName(userName);
        OAuth2AccessToken expectedOAuth2AccessToken = new OAuth2AccessToken("testToken");
        mongoTokenRepository.storeAccessToken(expectedOAuth2AccessToken, expectedAuthentication, "test2").blockingGet();

        TestObserver<List<OAuth2AccessToken>> testObserver = mongoTokenRepository.findTokensByClientId(clientId).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValueCount(1);
        testObserver.assertValue(l -> l.get(0).getValue().equals("testToken"));
    }


    @Test
    public void testReadingAccessTokenForTokenThatDoesNotExist() {
        mongoTokenRepository.readAccessToken("tokenThatDoesNotExist").test().assertEmpty();
    }

    @Test
    public void testRefreshTokenIsNotStoredDuringAccessToken() {
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(RequestTokenFactory.createOAuth2Request("id", false), new TestAuthentication("test2", false));
        OAuth2AccessToken expectedOAuth2AccessToken = new OAuth2AccessToken("testToken");
        expectedOAuth2AccessToken.setRefreshToken(new OAuth2RefreshToken("refreshToken"));
        mongoTokenRepository.storeAccessToken(expectedOAuth2AccessToken, expectedAuthentication, "test2").blockingGet();

        TestObserver<OAuth2AccessToken> testObserver = mongoTokenRepository.readAccessToken("testToken").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(accessToken -> accessToken.getRefreshToken() == null);
        mongoTokenRepository.readRefreshToken("refreshToken").test().assertEmpty();
    }

    @Test
    public void testStoreRefreshToken() {
        String refreshToken = "testToken" + UUID.randomUUID();
        OAuth2RefreshToken expectedRefreshToken = new OAuth2RefreshToken(refreshToken);
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(RequestTokenFactory.createOAuth2Request("id", false), new TestAuthentication("test2", false));
        mongoTokenRepository.storeRefreshToken(expectedRefreshToken, expectedAuthentication).blockingGet();

        TestObserver<OAuth2RefreshToken> testObserver = mongoTokenRepository.readRefreshToken(refreshToken).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(refreshToken1 -> expectedRefreshToken.getValue().equals(refreshToken1.getValue()));

        //assertEquals(expectedAuthentication, mongoTokenRepository.readAuthenticationForRefreshToken(expectedRefreshToken));
        mongoTokenRepository.removeRefreshToken(expectedRefreshToken).test().awaitTerminalEvent();
        mongoTokenRepository.readRefreshToken(refreshToken).test().assertEmpty();
        mongoTokenRepository.readAuthentication(expectedRefreshToken.getValue()).test().assertEmpty();
    }

    @Test
    public void testReadingRefreshTokenForTokenThatDoesNotExist() {
       mongoTokenRepository.readRefreshToken("tokenThatDoesNotExist").test().assertEmpty();
    }


    @Test
    public void testGetAccessTokenForDeletedUser() {
        //Test approved request
        OAuth2Request storedOAuth2Request = RequestTokenFactory.createOAuth2Request("id", true);
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(storedOAuth2Request, new TestAuthentication("test", true));
        OAuth2AccessToken expectedOAuth2AccessToken = new OAuth2AccessToken("testToken");
        mongoTokenRepository.storeAccessToken(expectedOAuth2AccessToken, expectedAuthentication, "test").blockingGet();
        TestObserver<OAuth2AccessToken> testObserver = mongoTokenRepository.getAccessToken("test").test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(accessToken -> expectedOAuth2AccessToken.getValue().equals(accessToken.getValue()));
        //assertEquals(expectedAuthentication, mongoTokenRepository.readAuthentication(expectedOAuth2AccessToken.getValue()));

        //Test unapproved request
        final OAuth2Request unapprovedStoredOAuth2Request = RequestTokenFactory.createOAuth2Request("id", false);
        // The generated key for the authentication is the same as before, but the two auths are not equal. This could
        // happen if there are 2 users in a system with the same username, or (more likely), if a user account was
        // deleted and re-created.
        // assertEquals(anotherAuthentication.getUserAuthentication(), mongoTokenRepository.readAuthentication(expectedOAuth2AccessToken.getValue()).getUserAuthentication());
        // The authorizationRequest does not match because it is unapproved, but the token was granted to an approved request
        TestObserver<OAuth2Authentication> testObserver1 = mongoTokenRepository.readAuthentication(expectedOAuth2AccessToken.getValue()).test();
        testObserver1.awaitTerminalEvent();

        testObserver1.assertComplete();
        testObserver1.assertNoErrors();
        testObserver1.assertValue(oAuth2Authentication -> unapprovedStoredOAuth2Request.isApproved() != oAuth2Authentication.getOAuth2Request().isApproved());
    }


    @Test
    public void testRemoveRefreshToken() {
        OAuth2RefreshToken expectedRefreshToken = new OAuth2RefreshToken("testToken");
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(RequestTokenFactory.createOAuth2Request("id", false), new TestAuthentication("test2", false));
        mongoTokenRepository.storeRefreshToken(expectedRefreshToken, expectedAuthentication).blockingGet();
        mongoTokenRepository.removeRefreshToken(expectedRefreshToken).test().awaitTerminalEvent();

        mongoTokenRepository.readRefreshToken("testToken").test().assertEmpty();
    }

    @Test
    public void testRemovedTokenCannotBeFoundByUsername() {
        OAuth2AccessToken token = new OAuth2AccessToken("testToken");
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(RequestTokenFactory.createOAuth2Request(
                "id", false), new TestAuthentication("test2", false));
        mongoTokenRepository.storeAccessToken(token, expectedAuthentication, "test2").blockingGet();
        mongoTokenRepository.removeAccessToken(token).test().awaitTerminalEvent();

        mongoTokenRepository.findTokensByClientIdAndUserName("id", "test2").test().assertEmpty();
    }
}