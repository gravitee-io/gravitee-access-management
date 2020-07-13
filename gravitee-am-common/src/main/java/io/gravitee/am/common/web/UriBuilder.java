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

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class UriBuilder {

    //private static final Pattern QUERY_PARAM_PATTERN = Pattern.compile("([^&=]+)(=?)([^&]+)?");

    private static final String SCHEME_REGEX = "([^:/?#]+):";

    private static final String HTTP_REGEX = "(?i)(http|https):";

    private static final String USERINFO_REGEX = "([^@\\[/?#]*)";

    private static final String HOST_IPV4_REGEX = "[^\\[/?#:]*";

    private static final String HOST_IPV6_REGEX = "\\[[\\p{XDigit}\\:\\.]*[%\\p{Alnum}]*\\]";

    private static final String HOST_REGEX = "(" + HOST_IPV6_REGEX + "|" + HOST_IPV4_REGEX + ")";

    private static final String PORT_REGEX = "(\\d*(?:\\{[^/]+?\\})?)";

    private static final String PATH_REGEX = "([^?#]*)";

    private static final String QUERY_REGEX = "([^#]*)";

    private static final String LAST_REGEX = "(.*)";

    // Regex patterns that matches URIs. See RFC 3986, appendix B
    private static final Pattern URI_PATTERN = Pattern.compile(
            "^(" + SCHEME_REGEX + ")?" + "(//(" + USERINFO_REGEX + "@)?" + HOST_REGEX + "(:" + PORT_REGEX +
                    ")?" + ")?" + PATH_REGEX + "(\\?" + QUERY_REGEX + ")?" + "(#" + LAST_REGEX + ")?");

    private static final Pattern HTTP_URL_PATTERN = Pattern.compile(
            "^" + HTTP_REGEX + "(//(" + USERINFO_REGEX + "@)?" + HOST_REGEX + "(:" + PORT_REGEX +
                    ")?" + ")?" + PATH_REGEX + "(\\?" + QUERY_REGEX + ")?" + "(#" + LAST_REGEX + ")?");

    private static final Pattern HTTP_PATTERN = Pattern.compile(HTTP_REGEX.replace(":",""));

    private static final String LOCALHOST_HOST_REGEX = "^localhost$";
    private static final String LOCALHOST_IPV4_REGEX = "^127(?:\\.[0-9]+){0,2}\\.[0-9]+$";
    private static final String LOCALHOST_IPV6_REGEX = "^(?:0*\\:)*?:?0*1$";

    private static final Pattern LOCALHOST_PATTERN = Pattern.compile(
            LOCALHOST_HOST_REGEX +"|"+ LOCALHOST_IPV4_REGEX +"|"+ LOCALHOST_IPV6_REGEX);

    private String scheme;
    private String host;
    private int port = -1;
    private String userInfo;
    private String path;
    private String query;
    private String fragment;

    public static UriBuilder newInstance() {
        return new UriBuilder();
    }

    public static UriBuilder fromURIString(String uri) {
        Matcher matcher = URI_PATTERN.matcher(uri);
        if (matcher.matches()) {
            UriBuilder builder = new UriBuilder();
            String scheme = matcher.group(2);
            String userInfo = matcher.group(5);
            String host = matcher.group(6);
            String port = matcher.group(8);
            String path = matcher.group(9);
            String query = matcher.group(11);
            String fragment = matcher.group(13);
            builder.scheme(scheme);
            builder.host(host);
            if (port != null && !port.isEmpty()) {
                builder.port(Integer.valueOf(port));
            }
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

    public static UriBuilder fromHttpUrl(String httpUrl) {
        Matcher matcher = HTTP_URL_PATTERN.matcher(httpUrl);
        if (matcher.matches()) {
            UriBuilder builder = new UriBuilder();
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
            builder.fragment(matcher.group(12));
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

    public static String decodeURIComponent(String s) {
        String result;
        try {
            result = URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            result = s;
        }
        return result;
    }

    public UriBuilder scheme(String scheme) {
        this.scheme = scheme;
        return this;
    }

    public UriBuilder host(String host) {
        this.host = host;
        return this;
    }

    public UriBuilder port(int port) {
        this.port = port;
        return this;
    }

    public UriBuilder userInfo(String userInfo) {
        this.userInfo = userInfo;
        return this;
    }

    public UriBuilder path(String path) {
        this.path = path;
        return this;
    }

    public UriBuilder query(String query) {
        this.query = query;
        return this;
    }

    public UriBuilder fragment(String fragment) {
        this.fragment = fragment;
        return this;
    }

    public UriBuilder parameters(Map<String, String> parameters) {
        if (parameters != null) {
            parameters.forEach((k, v) -> addParameter(k, v));
        }
        return this;
    }

    public UriBuilder parameters(Iterable<Map.Entry<String, String>> parameters) {
        if (parameters != null) {
            parameters.forEach(e -> addParameter(e.getKey(), e.getValue()));
        }
        return this;
    }

    public UriBuilder addParameter(String parameter, String value) {
        if (query == null) {
            query = "";
        }
        if (query.length() > 0) {
            query += "&";
        }
        query += parameter + "=" + value;
        return this;
    }

    public UriBuilder addFragmentParameter(String parameter, String value) {
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

    public String buildString() {
        final StringBuilder sb = new StringBuilder();
        if (this.scheme != null) {
            sb.append(this.scheme).append(':');
        }
        if (this.host != null) {
            sb.append("//");
            if (this.userInfo != null) {
                sb.append(this.userInfo).append("@");
            }
            sb.append(this.host);
            if (this.port >= 0) {
                sb.append(":").append(this.port);
            }
        }
        if (this.path != null) {
            sb.append(this.path);
        }
        if (this.query != null) {
            sb.append("?").append(this.query);
        }
        if (this.fragment != null) {
            sb.append("#").append(this.fragment);
        }
        return sb.toString();
    }

    public static boolean isHttp(String scheme) {
        return HTTP_PATTERN.matcher(scheme.toLowerCase()).matches();
    }

    public static boolean isLocalhost(String host) {
        return LOCALHOST_PATTERN.matcher(host.toLowerCase()).matches();
    }
}
