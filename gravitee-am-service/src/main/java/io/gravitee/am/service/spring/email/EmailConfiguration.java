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
package io.gravitee.am.service.spring.email;

import io.gravitee.common.util.EnvironmentUtils;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.angus.mail.auth.OAuth2SaslClientFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.gravitee.am.service.spring.email.EmailPropertiesLoader.AUTH_METHOD_BASIC;
import static io.gravitee.am.service.spring.email.EmailPropertiesLoader.AUTH_METHOD_OAUTH2;
import static java.util.Objects.isNull;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@Configuration
public class EmailConfiguration {

    private static final String EMAIL_ALLOW_LIST = "email.allowedfrom[%d]";
    private static final String DEFAULT_ALLOWED_FORM = "*@*.*";
    private static final String EMAIL_ENABLED = "email.enabled";
    private static final String EMAIL_HOST = "email.host";
    private static final String EMAIL_PORT = "email.port";
    private static final String EMAIL_FROM = "email.from";
    private static final String EMAIL_USERNAME = "email.username";
    private static final String EMAIL_PASSWORD = "email.password";
    private static final String EMAIL_PROTOCOL = "email.protocol";
    private static final String EMAIL_AUTH_METHOD = "email.authMethod";
    private static final String EMAIL_OAUTH2_TOKEN_ENDPOINT = "email.oauth2.tokenEndpoint";
    private static final String EMAIL_OAUTH2_CLIENT_ID = "email.oauth2.clientId";
    private static final String EMAIL_OAUTH2_CLIENT_SECRET = "email.oauth2.clientSecret";
    private static final String EMAIL_OAUTH2_REFRESH_TOKEN = "email.oauth2.refreshToken";
    private static final String EMAIL_OAUTH2_SCOPE = "email.oauth2.scope";


    @Autowired
    private OAuth2TokenService oauth2TokenService;

    private final ConfigurableEnvironment environment;
    private final List<String> allowedFrom;
    private final EmailPropertiesLoader propertiesLoader;
    private io.gravitee.node.api.configuration.Configuration configuration;

    public EmailConfiguration(ConfigurableEnvironment environment) {
        this.environment = environment;
        this.propertiesLoader = new EmailPropertiesLoader();
        this.allowedFrom = initializeAllowList();
    }

    @Lazy
    @Bean
    public JavaMailSender mailSender(io.gravitee.node.api.configuration.Configuration configuration) {
        this.configuration = configuration;
        final JavaMailSenderImpl javaMailSender = new JavaMailSenderImpl();
        javaMailSender.setHost(getHost());
        try {
            javaMailSender.setPort(Integer.valueOf(getPort()));
        } catch (Exception e) {
            log.warn("Cannot configure JavaMail Sender", e);
        }

        String authMethod = getAuthMethod();

        javaMailSender.setProtocol(getProtocol());
        javaMailSender.setJavaMailProperties(propertiesLoader.load(environment, authMethod));

        if (AUTH_METHOD_OAUTH2.equalsIgnoreCase(authMethod)) {
            configureOAuth2Authentication(javaMailSender);
            return new OAuth2JavaMailSenderWrapper(javaMailSender, oauth2TokenService);
        } else {
            configureBasicAuthentication(javaMailSender);
            return javaMailSender;
        }
    }

    private void configureBasicAuthentication(JavaMailSenderImpl javaMailSender) {
        log.debug("Configuring basic authentication for email");
        javaMailSender.setUsername(getUsername());
        javaMailSender.setPassword(getPassword());
    }

    private void configureOAuth2Authentication(JavaMailSenderImpl javaMailSender) {
        log.debug("Configuring OAuth2 authentication for email");

        String tokenEndpoint = getOAuth2TokenEndpoint();
        String clientId = getOAuth2ClientId();
        String clientSecret = getOAuth2ClientSecret();
        String refreshToken = getOAuth2RefreshToken();
        String scope = getOAuth2Scope();
        String username = getUsername();

        OAuth2SaslClientFactory.init();

        oauth2TokenService.initialize(tokenEndpoint, clientId, clientSecret, refreshToken, scope);

        String accessToken = oauth2TokenService.getAccessToken();

        javaMailSender.setUsername(username);
        javaMailSender.setPassword(accessToken);
    }

    public String getHost() {
        return configuration.getProperty(EMAIL_HOST);
    }

    public String getPort() {
        return configuration.getProperty(EMAIL_PORT, "587");
    }

    public String getAuthMethod() {
        return configuration.getProperty(EMAIL_AUTH_METHOD, AUTH_METHOD_BASIC);
    }

    public String getOAuth2TokenEndpoint() {
        return configuration.getProperty(EMAIL_OAUTH2_TOKEN_ENDPOINT);
    }

    public String getOAuth2ClientId() {
        return configuration.getProperty(EMAIL_OAUTH2_CLIENT_ID);
    }

    public String getOAuth2ClientSecret() {
        return configuration.getProperty(EMAIL_OAUTH2_CLIENT_SECRET);
    }

    public String getOAuth2RefreshToken() {
        return configuration.getProperty(EMAIL_OAUTH2_REFRESH_TOKEN);
    }

    public String getOAuth2Scope() {
        return configuration.getProperty(EMAIL_OAUTH2_SCOPE);
    }

    public String getUsername() {
        return configuration.getProperty(EMAIL_USERNAME);
    }

    public String getPassword() {
        return configuration.getProperty(EMAIL_PASSWORD);
    }

    public String getProtocol() {
        return configuration.getProperty(EMAIL_PROTOCOL,"smtp");
    }

    public String getFrom() {
        return configuration.getProperty(EMAIL_FROM);
    }

    public boolean isEnabled() {
        return Boolean.parseBoolean(configuration.getProperty(EMAIL_ENABLED, "false"));
    }

    public List<String> getAllowedFrom() {
        return allowedFrom;
    }

    public boolean useAuth() {
        return getProperty("auth", false);
    }

    public boolean useStartTls() {
        return getProperty("starttls.enable", false);
    }

    private <T> T getProperty(String propName, T defaultValue) {
        final Map<String, Object> emailProperties = EnvironmentUtils.getPropertiesStartingWith(environment, EmailPropertiesLoader.EMAIL_PROPERTIES_PREFIX);
        if (emailProperties.containsKey(EmailPropertiesLoader.EMAIL_PROPERTIES_PREFIX + "." + propName)) {
            return (T) emailProperties.get(EmailPropertiesLoader.EMAIL_PROPERTIES_PREFIX + "." + propName);
        } else {
            return defaultValue;
        }
    }

    private List<String> initializeAllowList() {
        List<String> allowList = new ArrayList<>();
        for (int i = 0; true; i++) {
            var propertyKey = String.format(EMAIL_ALLOW_LIST, i);
            var value = environment.getProperty(propertyKey, String.class);
            if (isNull(value)) {
                break;
            }
            allowList.add(value);
        }
        if (allowList.isEmpty()) {
            allowList.add(DEFAULT_ALLOWED_FORM);
        }
        return allowList;

    }

}

