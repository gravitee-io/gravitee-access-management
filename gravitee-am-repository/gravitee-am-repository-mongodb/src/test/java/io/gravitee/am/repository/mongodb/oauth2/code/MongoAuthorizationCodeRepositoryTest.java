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
package io.gravitee.am.repository.mongodb.oauth2.code;

import io.gravitee.am.repository.mongodb.oauth2.utils.RequestTokenFactory;
import io.gravitee.am.repository.mongodb.oauth2.utils.TestAuthentication;
import io.gravitee.am.repository.oauth2.model.OAuth2Authentication;
import io.gravitee.am.repository.oauth2.model.code.OAuth2AuthorizationCode;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 *
 * TODO : check OAuth2Authentication equality
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = MongoAuthorizationCodeRepositoryTestContextConfiguration.class, loader = AnnotationConfigContextLoader.class)
public class MongoAuthorizationCodeRepositoryTest {

    @Autowired
    private MongoAuthorizationCodeRepository mongoAuthorizationCodeRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @After
    public void tearDown() {
        mongoTemplate.getDb().dropDatabase();
    }

    @Test
    public void testStoreCode() {
        String code = "testCode";
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(RequestTokenFactory.createOAuth2Request("id", false), new TestAuthentication("test2", false));
        OAuth2AuthorizationCode oAuth2AuthorizationCode = new OAuth2AuthorizationCode();
        oAuth2AuthorizationCode.setCode(code);
        oAuth2AuthorizationCode.setOAuth2Authentication(expectedAuthentication);
        mongoAuthorizationCodeRepository.store(oAuth2AuthorizationCode);

        OAuth2Authentication oAuth2Authentication = mongoAuthorizationCodeRepository.remove(code).get();
        assertNotNull(oAuth2Authentication);
        //assertEquals(expectedAuthentication, oAuth2Authentication);
        assertFalse(mongoAuthorizationCodeRepository.remove(code).isPresent());
    }

    @Test
    public void testStoreCodeTwice() {
        String code = "testCode";
        OAuth2Authentication expectedAuthentication = new OAuth2Authentication(RequestTokenFactory.createOAuth2Request( "id", false), new TestAuthentication("test2", false));
        OAuth2AuthorizationCode oAuth2AuthorizationCode = new OAuth2AuthorizationCode();
        oAuth2AuthorizationCode.setCode(code);
        oAuth2AuthorizationCode.setOAuth2Authentication(expectedAuthentication);
        mongoAuthorizationCodeRepository.store(oAuth2AuthorizationCode);
        mongoAuthorizationCodeRepository.store(oAuth2AuthorizationCode);
        OAuth2Authentication oAuth2Authentication = mongoAuthorizationCodeRepository.remove(code).get();
        assertNotNull(oAuth2Authentication);
        //assertEquals(expectedAuthentication, oAuth2Authentication);
        assertFalse(mongoAuthorizationCodeRepository.remove(code).isPresent());
    }

    @Test
    public void testReadingCodeThatDoesNotExist() {
        assertFalse(mongoAuthorizationCodeRepository.remove("codeThatDoesNotExist").isPresent());
    }

}