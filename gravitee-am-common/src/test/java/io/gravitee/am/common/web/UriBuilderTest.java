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

import static org.junit.runners.Parameterized.Parameters;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(Parameterized.class)
public class UriBuilderTest {

    private String uri;
    private String scheme;
    private String host;
    private int port;
    private String userinfo;
    private String path;
    private String query;
    private String fragment;
    private boolean isHttp;

    public UriBuilderTest(String uri, int port, String[] params, boolean isHttp) {
        this.uri = uri;
        this.port = port;
        this.scheme = params[0];
        this.host = params[1];
        this.userinfo = params[2];
        this.path = params[3];
        this.query = params[4];
        this.fragment = params[5];
        this.isHttp = isHttp;
    }

    @Parameters(name = "Testing {0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(
            new Object[][] {
                {
                    "http://localhost:8080/callback",
                    8080,
                    Arrays.asList("http", "localhost", null, "/callback", null, null).toArray(new String[6]),
                    true,
                },
                {
                    "https://admin:password@localhost/callback",
                    -1,
                    Arrays.asList("https", "localhost", "admin:password", "/callback", null, null).toArray(new String[6]),
                    true,
                },
                {
                    "https://gravitee.is?the=best",
                    -1,
                    Arrays.asList("https", "gravitee.is", null, "", "the=best", null).toArray(new String[6]),
                    true,
                },
                {
                    "myapp://callback#token=fragment",
                    -1,
                    Arrays.asList("myapp", "callback", null, "", null, "token=fragment").toArray(new String[6]),
                    false,
                },
                {
                    "https://op-test:60001/requests/something?the=best#fragment",
                    60001,
                    Arrays.asList("https", "op-test", null, "/requests/something", "the=best", "fragment").toArray(new String[6]),
                    true,
                },
                {
                    "com.google.app:/callback",
                    -1,
                    Arrays.asList("com.google.app", null, null, "/callback", null, null).toArray(new String[6]),
                    false,
                },
            }
        );
    }

    @Test
    public void testFromUri() throws Exception {
        URI uri = UriBuilder.fromURIString(this.uri).build();
        Assert.assertEquals("scheme", this.scheme, uri.getScheme());
        Assert.assertEquals("user info", this.userinfo, uri.getUserInfo());
        Assert.assertEquals("host", this.host, uri.getHost());
        Assert.assertEquals("port", this.port, uri.getPort());
        Assert.assertEquals("path", this.path, uri.getPath());
        Assert.assertEquals("query", this.query, uri.getQuery());
        Assert.assertEquals("fragment", this.fragment, uri.getFragment());
        Assert.assertEquals("Scheme isHttp does not match", this.isHttp, UriBuilder.isHttp(uri.getScheme()));
    }

    @Test
    public void testFromHttp() throws Exception {
        if (this.isHttp) {
            URI uri = UriBuilder.fromHttpUrl(this.uri).build();
            Assert.assertEquals("scheme", this.scheme, uri.getScheme());
            Assert.assertEquals("user info", this.userinfo, uri.getUserInfo());
            Assert.assertEquals("host", this.host, uri.getHost());
            Assert.assertEquals("port", this.port, uri.getPort());
            Assert.assertEquals("path", this.path, uri.getPath());
            Assert.assertEquals("query", this.query, uri.getQuery());
            Assert.assertEquals("fragment", this.fragment, uri.getFragment());
            Assert.assertEquals("Scheme isHttp does not match", this.isHttp, UriBuilder.isHttp(uri.getScheme()));
        } else {
            boolean assertThrowException = false;
            try {
                UriBuilder.fromHttpUrl(this.uri).build();
            } catch (IllegalArgumentException ex) {
                assertThrowException = true;
            }
            Assert.assertTrue("We expecting an exception, but did not happen", assertThrowException);
        }
    }
}
