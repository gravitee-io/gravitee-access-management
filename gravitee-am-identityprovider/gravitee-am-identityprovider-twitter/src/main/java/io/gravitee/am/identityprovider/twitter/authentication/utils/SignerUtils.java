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
package io.gravitee.am.identityprovider.twitter.authentication.utils;

import io.gravitee.am.common.exception.authentication.BadCredentialsException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.gravitee.am.common.web.UriBuilder.encodeURIComponent;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SignerUtils {
    private static final String HMAC_SHA1_JAVA_ALGO = "HmacSHA1";

    public static final String OAUTH_CALLBACK = "oauth_callback";
    public static final String OAUTH_CONSUMER_KEY           = "oauth_consumer_key";
    public static final String OAUTH_TOKEN                  = "oauth_token";
    public static final String OAUTH_TOKEN_SECRET           = "oauth_token_secret";
    public static final String OAUTH_NONCE                  = "oauth_nonce";
    public static final String OAUTH_SIGNATURE              = "oauth_signature";
    public static final String OAUTH_SIGNATURE_METHOD       = "oauth_signature_method";
    public static final String OAUTH_SIGNATURE_METHOD_VALUE = "HMAC-SHA1";
    public static final String OAUTH_TIMESTAMP              = "oauth_timestamp";
    public static final String OAUTH_VERSION                = "oauth_version";
    public static final String OAUTH_VERSION_VALUE          = "1.0";
    public static final String OAUTH_VERIFIER               = "oauth_verifier";

    private static String percentEncode(String str) {
        return encodeURIComponent(str)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    public static String buildSignatureBaseString(String method, String url, Map<String, String> parameters, Map<String, String> oauthParams) {
        final TreeMap<String, String> sortedMap = new TreeMap<>(oauthParams);
        sortedMap.putAll(parameters);
        String parametersString = sortedMap.entrySet()
                .stream()
                .map(entry -> percentEncode(entry.getKey()) + "=" + percentEncode(entry.getValue()))
                .collect(Collectors.joining("&"));

        return method+"&"+percentEncode(url)+"&"+percentEncode(parametersString);
    }

    private static String signRequest(String secret, String tokenSecret, String signatureBaseString) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        SecretKeySpec signingKey = new SecretKeySpec((percentEncode(secret) + '&' + (tokenSecret == null ? "" : percentEncode(tokenSecret))).getBytes(), HMAC_SHA1_JAVA_ALGO);
        mac.init(signingKey);
        byte[] text = signatureBaseString.getBytes("UTF-8");
        byte[] signatureBytes = mac.doFinal(text);
        signatureBytes = Base64.getEncoder().encode(signatureBytes);
        String signature = new String(signatureBytes, "UTF-8");
        return signature;
    }

    public static String getAuthorizationHeader(String method, String url, Map<String, String> parameters, Map<String, String> oauthParams, OAuthCredentials credentials) {
        // add generated oauth param (nonce and timestamp)
        final long timestamp = Instant.now().getEpochSecond();
        final String nonce = UUID.randomUUID().toString().replace("-", "");
        oauthParams.put(OAUTH_NONCE, nonce);
        oauthParams.put(OAUTH_TIMESTAMP, String.valueOf(timestamp));

        StringBuilder authorization = new StringBuilder("OAuth ");
        for (Map.Entry<String, String> oauthParam : oauthParams.entrySet()) {
            authorization.append(oauthParam.getKey()).append("=\"").append(percentEncode(oauthParam.getValue())).append("\", ");
        }
        try {
            String baseSignatureString = buildSignatureBaseString(method, url, parameters, oauthParams);
            String signature = signRequest(credentials.getClientSecret(), credentials.getTokenSecret(), baseSignatureString);

            authorization.append(OAUTH_SIGNATURE).append("=\"").append(percentEncode(signature)).append("\"");
            return authorization.toString();
        } catch (Exception e) {
            throw new BadCredentialsException("Unable to sign OAuth request", e);
        }
    }
}
