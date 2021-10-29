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
package io.gravitee.am.resource.twilio.provider;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.http.NetworkHttpClient;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import io.gravitee.am.common.exception.mfa.InvalidCodeException;
import io.gravitee.am.common.exception.mfa.SendChallengeException;
import io.gravitee.am.resource.api.ResourceProvider;
import io.gravitee.am.resource.api.mfa.MFAChallenge;
import io.gravitee.am.resource.api.mfa.MFALink;
import io.gravitee.am.resource.api.mfa.MFAResourceProvider;
import io.gravitee.am.resource.twilio.TwilioVerifyResourceConfiguration;
import io.gravitee.common.util.EnvironmentUtils;
import io.reactivex.Completable;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TwilioVerifyResourceProvider implements MFAResourceProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(TwilioVerifyResourceProvider.class);
    public static final String APPROVED = "approved";

    private static final String TWILIO_API_HOSTNAME = "api.twilio.com";
    private static final Pattern WILCARD_PATTERN = Pattern.compile("\\*\\.");

    @Autowired
    private TwilioVerifyResourceConfiguration configuration;

    @Autowired
    private Environment env;

    @Value("${httpClient.timeout:10000}")
    private int httpClientTimeout;

    @Value("${httpClient.proxy.type:HTTP}")
    private String httpClientProxyType;

    @Value("${httpClient.proxy.https.host:#{systemProperties['https.proxyHost'] ?: 'localhost'}}")
    private String httpClientProxyHttpsHost;

    @Value("${httpClient.proxy.https.port:#{systemProperties['https.proxyPort'] ?: 3128}}")
    private int httpClientProxyHttpsPort;

    @Value("${httpClient.proxy.https.username:#{null}}")
    private String httpClientProxyHttpsUsername;

    @Value("${httpClient.proxy.https.password:#{null}}")
    private String httpClientProxyHttpsPassword;

    @Value("${httpClient.proxy.enabled:false}")
    private boolean isProxyConfigured;

    @Override
    public ResourceProvider start() throws Exception {
        // call init first otherwise HttpClient will be reset to the default one by the setUsername & setPassword done by the init method.
        Twilio.init(configuration.getAccountSid(), configuration.getAuthToken());

        if (configuration.isUseSystemProxy() && isProxyConfigured) {
            final List<String> proxyExcludeHosts = EnvironmentUtils
                    .getPropertiesStartingWith((ConfigurableEnvironment) env, "httpClient.proxy.exclude-hosts")
                    .values()
                    .stream()
                    .map(String::valueOf)
                    .collect(Collectors.toList());

            final boolean ignoreProxy = proxyExcludeHosts.stream().anyMatch(excludedHost -> {
                if (excludedHost.startsWith("*.")) {
                    return TWILIO_API_HOSTNAME.endsWith(WILCARD_PATTERN.matcher(excludedHost).replaceFirst(""));
                } else {
                    return TWILIO_API_HOSTNAME.equals(excludedHost);
                }
            });

            if (!ignoreProxy) {
                HttpClientBuilder httpClientBuild = buildHttpClient();
                final NetworkHttpClient twilioClient = new NetworkHttpClient(httpClientBuild);
                Twilio.setRestClient(new TwilioRestClient.Builder(configuration.getAccountSid(), configuration.getAuthToken()).httpClient(twilioClient).build());
            } else {
                LOGGER.debug("Twilio APIs belongs to the hosts excluded from the proxy settings");
            }
        } else if (configuration.isUseSystemProxy()) {
            LOGGER.warn("Twilio resource expect 'system proxy' but there are no settings");
        }

        return this;
    }

    private HttpClientBuilder buildHttpClient() {
        HttpClientBuilder httpClientBuild = HttpClientBuilder.create();

        String hostname = this.httpClientProxyHttpsHost;
        int port = this.httpClientProxyHttpsPort;
        String username = this.httpClientProxyHttpsUsername;
        String password = this.httpClientProxyHttpsPassword;

        if ("http".equalsIgnoreCase(this.httpClientProxyType)) {
            httpClientBuild.setProxy(new HttpHost(hostname, port));
            if (username != null && password != null) {
                CredentialsProvider credsProvider = new BasicCredentialsProvider();
                credsProvider.setCredentials(
                        new AuthScope(hostname, port),
                        new UsernamePasswordCredentials(username, password));
                httpClientBuild.setDefaultCredentialsProvider(credsProvider);
            }
        } else {
            try {
                Registry<ConnectionSocketFactory> reg = RegistryBuilder.<ConnectionSocketFactory>create()
                        .register("https", new SSLProxySocksConnectionSocketFactory(SSLContext.getDefault(), hostname, port, username, password))
                        .build();
                PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(reg);
                httpClientBuild.setConnectionManager(cm);
            } catch (NoSuchAlgorithmException e) {
                LOGGER.error("Unable to initialize SSLContext for Twilio client using SOCKS proxy", e);
            }
        }

        return httpClientBuild;
    }


    @Override
    public ResourceProvider stop() {
        Twilio.destroy();
        return this;
    }

    @Override
    public Completable send(MFALink target) {
        String channel;
        switch (target.getChannel()) {
            case SMS:
                channel = "sms";
                break;
            case EMAIL:
                channel = "email";
                break;
            case CALL:
                channel = "call";
                break;
            default:
                return Completable.error(new IllegalArgumentException("Unsupported verification channel '" + target.getChannel() + "'"));
        }

        return Completable.create((emitter) -> {
            try {
                Verification verification = Verification.creator(
                                configuration.getSid(),
                                target.getTarget(),
                                channel)
                        .create();

                LOGGER.debug("Twilio Verification code asked with ID '{}'", verification.getSid());
                emitter.onComplete();
            } catch (ApiException e) {
                LOGGER.error("Challenge emission fails", e);
                emitter.onError(new SendChallengeException("Unable to send challenge"));
            }
        });
    }

    @Override
    public Completable verify(MFAChallenge challenge) {
        return Completable.create((emitter) -> {
            try {
                VerificationCheck verification = VerificationCheck.creator(configuration.getSid(), challenge.getCode())
                        .setTo(challenge.getTarget())
                        .create();

                LOGGER.debug("Twilio Verification code with ID '{}' verified with status '{}'", verification.getSid(), verification.getStatus());
                if (!APPROVED.equalsIgnoreCase(verification.getStatus())) {
                    emitter.onError(new InvalidCodeException("Invalid 2FA Code"));
                }
                emitter.onComplete();
            } catch (ApiException e) {
                LOGGER.error("Challenge verification fails", e);
                emitter.onError(new InvalidCodeException("Invalid 2FA Code"));
            }
        });
    }

    private static class SSLProxySocksConnectionSocketFactory extends SSLConnectionSocketFactory {

        private final String hostname;
        private final int port;

        public SSLProxySocksConnectionSocketFactory(SSLContext sslContext, String hostname, int port, String username, String password) {
            super(sslContext);
            this.hostname = hostname;
            this.port = port;
            if (username != null) {
                Authenticator.setDefault(
                        new Authenticator() {
                            @Override
                            protected PasswordAuthentication getPasswordAuthentication() {
                                return new PasswordAuthentication(
                                        username,
                                        password != null ? password.toCharArray() : new char[0]
                                );
                            }
                        }
                );
            }
        }

        @Override
        public Socket createSocket(final HttpContext context) throws IOException {
            InetSocketAddress socksaddr = new InetSocketAddress(hostname, port);
            Proxy proxy = new Proxy(Proxy.Type.SOCKS, socksaddr);
            return new Socket(proxy);
        }
    }
}
