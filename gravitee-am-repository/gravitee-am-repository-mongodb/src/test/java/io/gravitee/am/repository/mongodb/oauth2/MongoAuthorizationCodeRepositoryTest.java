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
import io.gravitee.am.repository.oauth2.api.AuthorizationCodeRepository;
import io.gravitee.am.repository.oauth2.model.OAuth2Authentication;
import io.gravitee.am.repository.oauth2.model.code.OAuth2AuthorizationCode;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoAuthorizationCodeRepositoryTest extends AbstractOAuth2RepositoryTest {

    @Autowired
    private AuthorizationCodeRepository authorizationCodeRepository;

    @Test
    public void testStoreCode() {
        String code = "testCode";
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(RequestTokenFactory.createOAuth2Request("id", false), new TestAuthentication("test2", false));
        OAuth2AuthorizationCode oAuth2AuthorizationCode = new OAuth2AuthorizationCode();
        oAuth2AuthorizationCode.setCode(code);
        oAuth2AuthorizationCode.setOAuth2Authentication(expectedAuthentication);
        //authorizationCodeRepository.store(oAuth2AuthorizationCode).blockingGet();

        /*
        TestObserver<OAuth2Authentication> testObserver = authorizationCodeRepository.remove(code).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(oAuth2Authentication -> oAuth2Authentication.getOAuth2Request().getClientId().equals("id"));
        */

        //authorizationCodeRepository.remove(code).test().assertEmpty();
    }

    @Test
    public void testStoreCodeTwice() {
        String code = "testCode";
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(RequestTokenFactory.createOAuth2Request( "id", false), new TestAuthentication("test2", false));
        OAuth2AuthorizationCode oAuth2AuthorizationCode = new OAuth2AuthorizationCode();
        oAuth2AuthorizationCode.setCode(code);
        oAuth2AuthorizationCode.setOAuth2Authentication(expectedAuthentication);
        //authorizationCodeRepository.store(oAuth2AuthorizationCode).blockingGet();
        //authorizationCodeRepository.store(oAuth2AuthorizationCode).blockingGet();

        /*
        TestObserver<OAuth2Authentication> testObserver = authorizationCodeRepository.remove(code).test();
        testObserver.awaitTerminalEvent();

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(oAuth2Authentication -> oAuth2Authentication.getOAuth2Request().getClientId().equals("id"));

*/
        //authorizationCodeRepository.remove(code).test().assertEmpty();
    }

    @Test
    public void testReadingCodeThatDoesNotExist() {
        //authorizationCodeRepository.remove("codeThatDoesNotExist").test().assertEmpty();
    }

}