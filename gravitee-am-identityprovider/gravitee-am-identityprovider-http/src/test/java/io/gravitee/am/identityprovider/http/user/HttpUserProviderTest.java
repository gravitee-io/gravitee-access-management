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
package io.gravitee.am.identityprovider.http.user;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.UserProvider;
import io.gravitee.am.identityprovider.http.user.spring.HttpUserProviderConfiguration;
import io.gravitee.am.service.exception.UserAlreadyExistsException;
import io.gravitee.am.service.exception.UserNotFoundException;
import io.gravitee.common.http.HttpHeaders;
import io.reactivex.observers.TestObserver;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { HttpUserProviderTestConfiguration.class, HttpUserProviderConfiguration.class }, loader = AnnotationConfigContextLoader.class)
public class HttpUserProviderTest {

    @Autowired
    private UserProvider userProvider;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().port(19998));

    @Test
    public void shouldCreateUser() {
        DefaultUser user = new DefaultUser("johndoe");

        stubFor(post(urlPathEqualTo("/api/users"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing("application/"))
                .withRequestBody(matching(".*"))
                .willReturn(okJson("{\"id\" : \"123456789\", \"username\" : \"johndoe\"}")));

        TestObserver<User> testObserver = userProvider.create(user).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "123456789".equals(u.getId()));
        testObserver.assertValue(u -> "johndoe".equals(u.getUsername()));
    }

    @Test
    public void shouldCreateUser_id_number_type() {
        DefaultUser user = new DefaultUser("johndoe");

        stubFor(post(urlPathEqualTo("/api/users"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing("application/"))
                .withRequestBody(matching(".*"))
                .willReturn(okJson("{\"id\" : 80100, \"username\" : \"johndoe\"}")));

        TestObserver<User> testObserver = userProvider.create(user).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "80100".equals(u.getId()));
        testObserver.assertValue(u -> "johndoe".equals(u.getUsername()));
    }

    @Test
    public void shouldCreateUser_userAlreadyExists() {
        DefaultUser user = new DefaultUser("johndoe");

        stubFor(post(urlPathEqualTo("/api/users"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing("application/"))
                .withRequestBody(matching(".*"))
                .willReturn(badRequest()));

        TestObserver<User> testObserver = userProvider.create(user).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertError(UserAlreadyExistsException.class);
    }

    @Test
    public void shouldFindUserByUsername() {
        stubFor(get(urlPathEqualTo("/api/users"))
                .withQueryParam("username", new EqualToPattern("johndoe"))
                .willReturn(okJson("{\"id\" : \"123456789\", \"username\" : \"johndoe\"}")));

        TestObserver<User> testObserver = userProvider.findByUsername("johndoe").test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "123456789".equals(u.getId()));
        testObserver.assertValue(u -> "johndoe".equals(u.getUsername()));
    }

    @Test
    public void shouldFindUserByUsername_arrayResponse() {
        stubFor(get(urlPathEqualTo("/api/users"))
                .withQueryParam("username", new EqualToPattern("johndoe"))
                .willReturn(okJson("[{\"id\" : \"123456789\", \"username\" : \"johndoe\"}]")));

        TestObserver<User> testObserver = userProvider.findByUsername("johndoe").test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "123456789".equals(u.getId()));
        testObserver.assertValue(u -> "johndoe".equals(u.getUsername()));
    }

    @Test
    public void shouldFindUserByEmail() {
        stubFor(get(urlPathEqualTo("/api/users"))
                .withQueryParam("email", new EqualToPattern("johndoe@mail.com"))
                .willReturn(okJson("{\"id\" : \"123456789\", \"username\" : \"johndoe\"}")));

        TestObserver<User> testObserver = userProvider.findByEmail("johndoe@mail.com").test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "123456789".equals(u.getId()));
        testObserver.assertValue(u -> "johndoe".equals(u.getUsername()));
    }

    @Test
    public void shouldFindUserByEmail_arrayResponse() {
        stubFor(get(urlPathEqualTo("/api/users"))
                .withQueryParam("email", new EqualToPattern("johndoe@mail.com"))
                .willReturn(okJson("[{\"id\" : \"123456789\", \"username\" : \"johndoe\"}]")));

        TestObserver<User> testObserver = userProvider.findByEmail("johndoe@mail.com").test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "123456789".equals(u.getId()));
        testObserver.assertValue(u -> "johndoe".equals(u.getUsername()));
    }

    @Test
    public void shouldUpdateUser() {
        DefaultUser user = new DefaultUser("johndoe");

        stubFor(put(urlPathEqualTo("/api/users/123456789"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing("application/"))
                .withRequestBody(matching(".*"))
                .willReturn(okJson("{\"id\" : \"123456789\", \"username\" : \"johndoe\"}")));

        TestObserver<User> testObserver = userProvider.update("123456789", user).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "123456789".equals(u.getId()));
        testObserver.assertValue(u -> "johndoe".equals(u.getUsername()));
    }

    @Test
    public void shouldUpdatePasswrd() {
        DefaultUser user = new DefaultUser("johndoe");
        user.setId("123456789");

        stubFor(put(urlPathEqualTo("/api/users/123456789"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing("application/"))
                .withRequestBody(matching(".*"))
                .willReturn(okJson("{\"id\" : \"123456789\", \"username\" : \"johndoe\"}")));

        TestObserver<User> testObserver = userProvider.updatePassword(user, "password").test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(u -> "123456789".equals(u.getId()));
        testObserver.assertValue(u -> "johndoe".equals(u.getUsername()));
    }

    @Test
    public void shouldUpdateUser_userNotFound() {
        DefaultUser user = new DefaultUser("johndoe");

        stubFor(put(urlPathEqualTo("/api/users/123456789"))
                .withHeader(HttpHeaders.CONTENT_TYPE, containing("application/"))
                .withRequestBody(matching(".*"))
                .willReturn(notFound()));

        TestObserver<User> testObserver = userProvider.update("123456789", user).test();
        testObserver.awaitTerminalEvent();
        testObserver.assertError(UserNotFoundException.class);
    }

    @Test
    public void shouldDeleteUser() {
        stubFor(delete(urlPathEqualTo("/api/users/123456789"))
                .willReturn(ok()));

        TestObserver testObserver = userProvider.delete("123456789").test();
        testObserver.awaitTerminalEvent();
        testObserver.assertComplete();
        testObserver.assertNoErrors();
    }

    @Test
    public void shouldDeleteUser_userNotFound() {
        stubFor(delete(urlPathEqualTo("/api/users/123456789"))
                .willReturn(notFound()));

        TestObserver testObserver = userProvider.delete("123456789").test();
        testObserver.awaitTerminalEvent();
        testObserver.assertNotComplete();
        testObserver.assertError(UserNotFoundException.class);
    }
}
