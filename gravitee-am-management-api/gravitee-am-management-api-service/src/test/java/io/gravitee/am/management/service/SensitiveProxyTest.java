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
package io.gravitee.am.management.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author Eric Leleu (eric.leleu@graviteesource.com)
 * @author GraviteeSource Team
 */
public class SensitiveProxyTest {
    private static final String COMPLEX_PASSWORD = "KU!$~25;&vZUP,745lpeO";
    private AbstractSensitiveProxy proxy = new AbstractSensitiveProxy(){};

    @Test
    public void should_extract_userInfo_from_http_url() {
        final var optPwd = proxy.extractPasswordFromUriUserInfo("http://user:" + COMPLEX_PASSWORD + "@localhost:8082/path");
        Assertions.assertTrue(optPwd.isPresent());
        Assertions.assertEquals(COMPLEX_PASSWORD, optPwd.get());
    }

    @Test
    public void should_provide_emptyOption_from_http_url_without_userInfo() {
        final var optPwd = proxy.extractPasswordFromUriUserInfo("http://localhost:8082/path");
        Assertions.assertTrue(optPwd.isEmpty());
    }

    @Test
    public void should_provide_emptyOption_from_http_url_with_userInfo_without_password() {
        final var optPwd = proxy.extractPasswordFromUriUserInfo("http://user@localhost:8082/path");
        Assertions.assertTrue(optPwd.isEmpty());
    }

    @Test
    public void should_provide_password_from_mongo_url_with_UserInfo() {
        final var optPwd = proxy.extractPasswordFromUriUserInfo("mongodb+srv://user-name:"+COMPLEX_PASSWORD+"@mongodb.net/gio-am?retryWrites=true&w=majority&connectTimeoutMS=10000&maxIdleTimeMS=30000h");
        Assertions.assertTrue(optPwd.isPresent());
        Assertions.assertEquals(COMPLEX_PASSWORD, optPwd.get());
    }

    @Test
    public void should_provide_emptyOption_from_mongo_url_with_userInfo_without_password() {
        final var optPwd = proxy.extractPasswordFromUriUserInfo("mongodb+srv://user-name@mongodb.net/gio-am?retryWrites=true&w=majority&connectTimeoutMS=10000&maxIdleTimeMS=30000h");
        Assertions.assertTrue(optPwd.isEmpty());
    }

    @Test
    public void should_provide_password_from_mongo_url_containing_multiple_host_and_with_userInfo() {
        final var optPwd = proxy.extractPasswordFromUriUserInfo("mongodb+srv://user-name:"+COMPLEX_PASSWORD+"@mongodb1.net,mongodb2.net,mongodb3.net/gio-am?retryWrites=true&w=majority&connectTimeoutMS=10000&maxIdleTimeMS=30000h");
        Assertions.assertTrue(optPwd.isPresent());
        Assertions.assertEquals(COMPLEX_PASSWORD, optPwd.get());
    }

    @Test
    public void should_not_provide_password_from_mongo_url_without_userInfo() {
        final var optPwd = proxy.extractPasswordFromUriUserInfo("mongodb://localhost:27017/gravitee-am?connectTimeoutMS=1000&socketTimeoutMS=1000");
        Assertions.assertTrue(optPwd.isEmpty());
    }
}
