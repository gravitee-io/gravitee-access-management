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

import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class UriBuilderTest {

    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"http://localhost:8080/callback", 8080, new UriParts("http", "localhost", null, "/callback", null, null), true},
                {"https://admin:password@localhost/callback", -1, new UriParts("https", "localhost", "admin:password", "/callback", null, null), true},
                {"https://gravitee.is?the=best", -1, new UriParts("https", "gravitee.is", null, "", "the=best", null), true},
                {"myapp://callback#token=fragment", -1, new UriParts("myapp", "callback", null, "", null, "token=fragment"), false},
                {"https://op-test:60001/requests/something?the=best#fragment", 60001,
                        new UriParts("https", "op-test", null, "/requests/something", "the=best", "fragment"), true},
                {"com.google.app:/callback", -1, new UriParts("com.google.app", null, null, "/callback", null, null), false},
                {"https://gravitee3.local:2000/test?xxx=123&param2={#context.attributes['client'].applicationType}&param3=321321&yyy={#test}#asd#asda", 2000, new UriParts("https", "gravitee3.local", null, "/test", "xxx=123&param2={#context.attributes['client'].applicationType}&param3=321321&yyy={#test}", "asd#asda"), true},
                {"http://gravitee3.local:2000/test?xxx=123&param2={#context.attributes['client'].applicationType}&param3=321321&yyy={#test}#asd#asda", 2000, new UriParts("http", "gravitee3.local", null, "/test", "xxx=123&param2={#context.attributes['client'].applicationType}&param3=321321&yyy={#test}", "asd#asda"), true},
                {"https://gravitee3.local/test?xxx=123&param2={#context.attributes['client'].applicationType}&param3=321321&yyy={#test}#asd#asda", -1, new UriParts("https", "gravitee3.local", null, "/test", "xxx=123&param2={#context.attributes['client'].applicationType}&param3=321321&yyy={#test}", "asd#asda"), true},
                {"https://gravitee3.local:2000?xxx=123&param2={#context.attributes['client'].applicationType}&param3=321321&yyy={#test}", 2000, new UriParts("https", "gravitee3.local", null, "", "xxx=123&param2={#context.attributes['client'].applicationType}&param3=321321&yyy={#test}", null), true},
                {"http://gravitee3.local/?xxx=123&param2={#context.attributes['client'].applicationType}&param3=321321&yyy={#test}", -1, new UriParts("http", "gravitee3.local", null, "/", "xxx=123&param2={#context.attributes['client'].applicationType}&param3=321321&yyy={#test}", null), true},
                {"https://gravitee3.local:2000?param2={#context.attributes['client'].applicationType}&param3=321321&yyy={#test}", 2000, new UriParts("https", "gravitee3.local", null, "", "param2={#context.attributes['client'].applicationType}&param3=321321&yyy={#test}", null), true},
                {"https://[::1:2:3:4%eth0]:8080/callback", 8080, new UriParts("https", "[::1:2:3:4%eth0]", null, "/callback", null, null), true},
        });
    }

    record UriParts(String scheme,
                    String host,
                    String userinfo,
                    String path,
                    String query,
                    String fragment) {

    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    void testFromUri2(String testUri, int port, UriParts uriParts, boolean isHttp) throws Exception {
        URI builtUri = UriBuilder.fromURIString(testUri).build();
        assertEquals(uriParts.scheme(), builtUri.getScheme(), "scheme");
        assertEquals(uriParts.userinfo(), builtUri.getUserInfo(), "user info");
        assertEquals(uriParts.host(), builtUri.getHost(), "host");
        assertEquals(port, builtUri.getPort(), "port");
        assertEquals(uriParts.path(), builtUri.getPath(), "path");
        assertEquals(uriParts.query(), builtUri.getQuery(), "query");
        assertEquals(uriParts.fragment(), builtUri.getFragment(), "fragment");
        assertEquals(isHttp, UriBuilder.isHttp(builtUri.getScheme()), "Scheme isHttp does not match");
    }

    @Test
    public void testFromUriString_longUri() {
        String baseUri = "https://login.microsoftonline.com/12345678-90ab-cdef-1234-567890abcde/oauth2/v2.0/logout?post_logout_redirect_uri=https://my.gateway/my-domain/logout/callback&id_token_hint=";
        String token = "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWUsImlhdCI6MTUxNjIzOTAyMn0.ANCf_8p1AE4ZQs7QuqGAyyfTEgYrKSjKWkhBk5cIn1_2QVr2jEjmM-1tu7EgnyOf_fAsvdFXva8Sv05iTGzETg";
        var sb = new StringBuilder(baseUri);
        sb.append(token.repeat(100));

        UriBuilder uriBuilder = UriBuilder.fromURIString(sb.toString());

        assertEquals(sb.toString(), uriBuilder.buildString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("data")
    public void testFromHttp(String testUri, int port, UriParts uriParts, boolean isHttp) throws Exception {
        if (isHttp) {
            URI uri = UriBuilder.fromHttpUrl(testUri).build();
            assertEquals(uriParts.scheme(), uri.getScheme(), "scheme");
            assertEquals(uriParts.userinfo(), uri.getUserInfo(), "user info");
            assertEquals(uriParts.host(), uri.getHost(), "host");
            assertEquals(port, uri.getPort(), "port");
            assertEquals(uriParts.path(), uri.getPath(), "path");
            assertEquals(uriParts.query(), uri.getQuery(), "query");
            assertEquals(uriParts.fragment(), uri.getFragment(), "fragment");
            assertEquals(isHttp, UriBuilder.isHttp(uri.getScheme()), "Scheme isHttp does not match");
        } else {
            boolean assertThrowException = false;
            try {
                UriBuilder.fromHttpUrl(testUri).build();
            } catch (IllegalArgumentException ex) {
                assertThrowException = true;
            }
            Assertions.assertTrue(assertThrowException, "We expecting an exception, but did not happen");
        }
    }

    @Test
    public void errorKeepsQueryParams() throws Exception {
        var mockError = new ErrorInfo("test", "T01", "{test failed}", null);
        var uri = UriBuilder.buildErrorRedirect("https://redirect.example.com?test=1", mockError, false);
        assertThat(uri.split("\\?")[1].split("&")).hasSize(4)

                .containsExactlyInAnyOrder(
                        "error=" + uriEncoded(mockError.error()),
                        "error_code=" + uriEncoded(mockError.code()),
                        "error_description=" + uriEncoded(mockError.description()),
                        "test=1");
    }

    private String uriEncoded(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }


}
