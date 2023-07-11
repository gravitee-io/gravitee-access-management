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
package io.gravitee.am.common.web;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class UriBuilderTest {

    public static Stream<Arguments> param_that_must_test_from_uri() {
        return Stream.of(Arguments.of("http://localhost:8080/callback", 8080, "http", "localhost", null, "/callback", null, null, true),
                Arguments.of("https://admin:password@localhost/callback", -1, "https", "localhost", "admin:password", "/callback", null, null, true),
                Arguments.of("https://gravitee.is?the=best", -1, "https", "gravitee.is", null, "", "the=best", null, true),
                Arguments.of("myapp://callback#token=fragment", -1, "myapp", "callback", null, "", null, "token=fragment", false),
                Arguments.of("https://op-test:60001/requests/something?the=best#fragment", 60001, "https", "op-test", null, "/requests/something", "the=best", "fragment", true),
                Arguments.of("com.google.app:/callback", -1, "com.google.app", null, null, "/callback", null, null, false)
        );
    }

    @ParameterizedTest
    @MethodSource("param_that_must_test_from_uri")
    public void must_test_from_uri(
            String uriStr,
            int port,
            String scheme,
            String host,
            String userInfo,
            String path,
            String query,
            String fragment,
            boolean isHttp) throws Exception {
        URI uri = UriBuilder.fromURIString(uriStr).build();
        assertEquals(scheme, uri.getScheme());
        assertEquals(userInfo, uri.getUserInfo());
        assertEquals(host, uri.getHost());
        assertEquals(port, uri.getPort());
        assertEquals(path, uri.getPath());
        assertEquals(query, uri.getQuery());
        assertEquals(fragment, uri.getFragment());
        assertEquals(isHttp, UriBuilder.isHttp(uri.getScheme()));
    }

    public static Stream<Arguments> param_that_must_test_from_http() {
        return Stream.of(Arguments.of("http://localhost:8080/callback", 8080, "http", "localhost", null, "/callback", null, null, true),
                Arguments.of("https://admin:password@localhost/callback", -1, "https", "localhost", "admin:password", "/callback", null, null, true),
                Arguments.of("https://gravitee.is?the=best", -1, "https", "gravitee.is", null, "", "the=best", null, true),
                Arguments.of("https://op-test:60001/requests/something?the=best#fragment", 60001, "https", "op-test", null, "/requests/something", "the=best", "fragment", true)
        );
    }

    @ParameterizedTest
    @MethodSource("param_that_must_test_from_http")
    public void must_test_from_http(
            String uriStr,
            int port,
            String scheme,
            String host,
            String userInfo,
            String path,
            String query,
            String fragment,
            boolean isHttp
    ) throws Exception {
        URI uri = UriBuilder.fromHttpUrl(uriStr).build();
        assertEquals(scheme, uri.getScheme());
        assertEquals(userInfo, uri.getUserInfo());
        assertEquals(host, uri.getHost());
        assertEquals(port, uri.getPort());
        assertEquals(path, uri.getPath());
        assertEquals(query, uri.getQuery());
        assertEquals(fragment, uri.getFragment());
        assertEquals(isHttp, UriBuilder.isHttp(uri.getScheme()));
    }


    public static Stream<Arguments> param_that_must_test_and_fail_from_http() {
        return Stream.of(
                Arguments.of("myapp://callback#token=fragment", IllegalArgumentException.class),
                Arguments.of("com.google.app:/callback", IllegalArgumentException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("param_that_must_test_and_fail_from_http")
    public void must_test_and_fail_from_http(String uri, Class<? extends Throwable> expected) throws Exception {
        assertThrows(expected, () -> UriBuilder.fromHttpUrl(uri).build());
    }
}
