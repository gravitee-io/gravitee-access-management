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
package io.gravitee.am.gateway.handler.utils;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class URIBuilder {

    private static final Pattern QUERY_PARAM_PATTERN = Pattern.compile("([^&=]+)(=?)([^&]+)?");

    private static final String SCHEME_PATTERN = "([^:/?#]+):";

    private static final String HTTP_PATTERN = "(?i)(http|https):";

    private static final String USERINFO_PATTERN = "([^@\\[/?#]*)";

    private static final String HOST_IPV4_PATTERN = "[^\\[/?#:]*";

    private static final String HOST_IPV6_PATTERN = "\\[[\\p{XDigit}\\:\\.]*[%\\p{Alnum}]*\\]";

    private static final String HOST_PATTERN = "(" + HOST_IPV6_PATTERN + "|" + HOST_IPV4_PATTERN + ")";

    private static final String PORT_PATTERN = "(\\d*(?:\\{[^/]+?\\})?)";

    private static final String PATH_PATTERN = "([^?#]*)";

    private static final String QUERY_PATTERN = "([^#]*)";

    private static final String LAST_PATTERN = "(.*)";

    // Regex patterns that matches URIs. See RFC 3986, appendix B
    private static final Pattern URI_PATTERN = Pattern.compile(
            "^(" + SCHEME_PATTERN + ")?" + "(//(" + USERINFO_PATTERN + "@)?" + HOST_PATTERN + "(:" + PORT_PATTERN +
                    ")?" + ")?" + PATH_PATTERN + "(\\?" + QUERY_PATTERN + ")?" + "(#" + LAST_PATTERN + ")?");

    private static final Pattern HTTP_URL_PATTERN = Pattern.compile(
            "^" + HTTP_PATTERN + "(//(" + USERINFO_PATTERN + "@)?" + HOST_PATTERN + "(:" + PORT_PATTERN + ")?" + ")?" +
                    PATH_PATTERN + "(\\?" + LAST_PATTERN + ")?");

    private String scheme;
    private String host;
    private int port = -1;
    private String userInfo;
    private String path;
    private String query;
    private String fragment;

    public static URIBuilder newInstance() {
        return new URIBuilder();
    }

    public static URIBuilder fromURIString(String uri) {
        Matcher matcher = URI_PATTERN.matcher(uri);
        if (matcher.matches()) {
            URIBuilder builder = new URIBuilder();
            String scheme = matcher.group(2);
            String userInfo = matcher.group(5);
            String host = matcher.group(6);
            String port = matcher.group(8);
            String path = matcher.group(9);
            String query = matcher.group(11);
            String fragment = matcher.group(13);
            builder.scheme(scheme);
            builder.host(host);
            builder.port(Integer.valueOf(port));
            builder.userInfo(userInfo);
            builder.path(path);
            builder.query(query);
            builder.fragment(fragment);
            return builder;
        }
        else {
            throw new IllegalArgumentException("[" + uri + "] is not a valid URI");
        }
    }

    public static URIBuilder fromHttpUrl(String httpUrl) {
        Matcher matcher = HTTP_URL_PATTERN.matcher(httpUrl);
        if (matcher.matches()) {
            URIBuilder builder = new URIBuilder();
            String scheme = matcher.group(1);
            builder.scheme(scheme != null ? scheme.toLowerCase() : null);
            builder.userInfo(matcher.group(4));
            String host = matcher.group(5);
            if ((scheme != null && !scheme.isEmpty()) && (host == null || host.isEmpty())) {
                throw new IllegalArgumentException("[" + httpUrl + "] is not a valid HTTP URL");
            }
            builder.host(host);
            String port = matcher.group(7);
            if (port != null && !port.isEmpty()) {
                builder.port(Integer.valueOf(port));
            }
            builder.path(matcher.group(8));
            builder.query(matcher.group(10));
            return builder;
        }
        else {
            throw new IllegalArgumentException("[" + httpUrl + "] is not a valid HTTP URL");
        }
    }

    /**
     * Convert a String to the application/x-www-form-urlencoded MIME format
     */
    public static String encodeURIComponent(String s) {
        String result;
        try {
            result = URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            result = s;
        }
        return result;
    }

    public URIBuilder scheme(String scheme) {
        this.scheme = scheme;
        return this;
    }

    public URIBuilder host(String host) {
        this.host = host;
        return this;
    }

    public URIBuilder port(int port) {
        this.port = port;
        return this;
    }

    public URIBuilder userInfo(String userInfo) {
        this.userInfo = userInfo;
        return this;
    }

    public URIBuilder path(String path) {
        this.path = path;
        return this;
    }

    public URIBuilder query(String query) {
        this.query = query;
        return this;
    }

    public URIBuilder fragment(String fragment) {
        this.fragment = fragment;
        return this;
    }

    public URIBuilder addParameter(String parameter, String value) {
        if (query == null) {
            query = "";
        }
        if (query.length() > 0) {
            query += "&";
        }
        query += parameter + "=" + value;
        return this;
    }

    public URIBuilder addFragmentParameter(String parameter, String value) {
        if (fragment == null) {
            fragment = "";
        }
        if (fragment.length() > 0) {
            fragment += "&";
        }
        fragment += parameter + "=" + value;
        return this;
    }

    public URI build() throws URISyntaxException {
        return new URI(scheme, userInfo, host, port, path, query, fragment);
    }
}
