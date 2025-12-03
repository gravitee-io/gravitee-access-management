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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static java.util.Objects.isNull;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
@Configuration
public class EmailConfiguration {

    private static final String EMAIL_ALLOW_LIST = "email.allowedfrom[%d]";
    private static final String EMAIL_PROPERTIES_PREFIX = "email.properties";
    private static final String MAILAPI_PROPERTIES_PREFIX = "mail.smtp.";
    private static final String DEFAULT_ALLOWED_FORM = "*@*.*";

    // OAuth2 configuration keys
    private static final String EMAIL_AUTH_METHOD = "email.authMethod";
    private static final String EMAIL_OAUTH2_TOKEN_ENDPOINT = "email.oauth2.tokenEndpoint";
    private static final String EMAIL_OAUTH2_CLIENT_ID = "email.oauth2.clientId";
    private static final String EMAIL_OAUTH2_CLIENT_SECRET = "email.oauth2.clientSecret";
    private static final String EMAIL_OAUTH2_REFRESH_TOKEN = "email.oauth2.refreshToken";
    private static final String EMAIL_OAUTH2_SCOPE = "email.oauth2.scope";
    private static final String EMAIL_OAUTH2_USERNAME = "email.oauth2.username";

    // Authentication methods
    private static final String AUTH_METHOD_BASIC = "basic";
    private static final String AUTH_METHOD_OAUTH2 = "oauth2";

    private final ConfigurableEnvironment environment;
    private final List<String> allowedFrom;
    private io.gravitee.node.api.configuration.Configuration configuration;

    @Autowired(required = false)
    private OAuth2TokenService oauth2TokenService;

    public EmailConfiguration(ConfigurableEnvironment environment) {
        this.environment = environment;
        this.allowedFrom = initializeAllowList();
    }

    /**
     * Register OAuth2 SASL provider on startup if OAuth2 authentication is configured.
     */
    @PostConstruct
    public void init() {
        // Register OAuth2 SASL provider if needed
        if (configuration != null && AUTH_METHOD_OAUTH2.equalsIgnoreCase(getAuthMethod())) {
            log.info("Registering OAuth2 SASL provider for email authentication");
            OAuth2SaslClientProvider.register();
        }
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

        // Configure authentication based on authMethod
        String authMethod = getAuthMethod();
        if (AUTH_METHOD_OAUTH2.equalsIgnoreCase(authMethod)) {
            configureOAuth2Authentication(javaMailSender);
        } else {
            // Default to basic authentication
            configureBasicAuthentication(javaMailSender);
        }

        javaMailSender.setProtocol(getProtocol());
        javaMailSender.setJavaMailProperties(loadProperties());
        return javaMailSender;
    }

    /**
     * Configure basic username/password authentication.
     */
    private void configureBasicAuthentication(JavaMailSenderImpl javaMailSender) {
        log.debug("Configuring basic authentication for email");
        javaMailSender.setUsername(getUsername());
        javaMailSender.setPassword(getPassword());
    }

    /**
     * Configure OAuth2 (XOAUTH2) authentication.
     */
    private void configureOAuth2Authentication(JavaMailSenderImpl javaMailSender) {
        log.info("Configuring OAuth2 authentication for email");

        // Validate OAuth2 configuration
        String tokenEndpoint = getOAuth2TokenEndpoint();
        String clientId = getOAuth2ClientId();
        String clientSecret = getOAuth2ClientSecret();
        String refreshToken = getOAuth2RefreshToken();
        String scope = getOAuth2Scope();
        String username = getOAuth2Username();

        if (tokenEndpoint == null || tokenEndpoint.isEmpty()) {
            throw new IllegalStateException("OAuth2 token endpoint (email.oauth2.tokenEndpoint) is required when authMethod is 'oauth2'");
        }
        if (clientId == null || clientId.isEmpty()) {
            throw new IllegalStateException("OAuth2 client ID (email.oauth2.clientId) is required when authMethod is 'oauth2'");
        }
        if (clientSecret == null || clientSecret.isEmpty()) {
            throw new IllegalStateException("OAuth2 client secret (email.oauth2.clientSecret) is required when authMethod is 'oauth2'");
        }
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new IllegalStateException("OAuth2 refresh token (email.oauth2.refreshToken) is required when authMethod is 'oauth2'");
        }
        if (scope == null || scope.isEmpty()) {
            throw new IllegalStateException("OAuth2 scope (email.oauth2.scope) is required when authMethod is 'oauth2'");
        }
        if (username == null || username.isEmpty()) {
            throw new IllegalStateException("OAuth2 username (email.oauth2.username) is required when authMethod is 'oauth2'");
        }

        // Initialize OAuth2 token service
        if (oauth2TokenService == null) {
            throw new IllegalStateException("OAuth2TokenService is not available. Cannot configure OAuth2 authentication.");
        }

        // Register OAuth2 SASL provider
        OAuth2SaslClientProvider.register();

        oauth2TokenService.initialize(tokenEndpoint, clientId, clientSecret, refreshToken, scope);

        // Get fresh access token
        String accessToken = oauth2TokenService.getAccessToken();

        // Set username and access token for XOAUTH2
        javaMailSender.setUsername(username);
        javaMailSender.setPassword(accessToken);

        log.info("OAuth2 authentication configured for user: {}", username);
    }

    private Properties loadProperties() {
        Map<String, Object> envProperties = EnvironmentUtils.getPropertiesStartingWith(environment, EMAIL_PROPERTIES_PREFIX);

        Properties properties = new Properties();
        envProperties.forEach((key, value) -> properties.setProperty(
                MAILAPI_PROPERTIES_PREFIX + key.substring(EMAIL_PROPERTIES_PREFIX.length() + 1),
                value.toString()));

        // Add OAuth2-specific properties if using OAuth2 authentication
        String authMethod = getAuthMethod();
        if (AUTH_METHOD_OAUTH2.equalsIgnoreCase(authMethod)) {
            // Enable OAuth2 SASL mechanism
            properties.setProperty(MAILAPI_PROPERTIES_PREFIX + "auth.mechanisms", "XOAUTH2");

            // Disable PLAIN and LOGIN mechanisms for security
            properties.setProperty(MAILAPI_PROPERTIES_PREFIX + "auth.plain.disable", "true");
            properties.setProperty(MAILAPI_PROPERTIES_PREFIX + "auth.login.disable", "true");

            // Ensure auth is enabled
            properties.setProperty(MAILAPI_PROPERTIES_PREFIX + "auth", "true");

            log.debug("OAuth2 SASL properties configured: auth.mechanisms=XOAUTH2");
        }

        return properties;
    }

    public String getHost() {
        return configuration.getProperty("email.host" );
    }

    public String getPort() {
        return configuration.getProperty("email.port");
    }

    /**
     * Get the authentication method (basic or oauth2).
     * Defaults to "basic" for backward compatibility.
     */
    public String getAuthMethod() {
        return configuration.getProperty(EMAIL_AUTH_METHOD, AUTH_METHOD_BASIC);
    }

    /**
     * Get the OAuth2 token endpoint.
     */
    public String getOAuth2TokenEndpoint() {
        return configuration.getProperty(EMAIL_OAUTH2_TOKEN_ENDPOINT);
    }

    /**
     * Get the OAuth2 client ID.
     */
    public String getOAuth2ClientId() {
        return configuration.getProperty(EMAIL_OAUTH2_CLIENT_ID);
    }

    /**
     * Get the OAuth2 client secret.
     */
    public String getOAuth2ClientSecret() {
        return configuration.getProperty(EMAIL_OAUTH2_CLIENT_SECRET);
    }

    /**
     * Get the OAuth2 refresh token.
     */
    public String getOAuth2RefreshToken() {
        return configuration.getProperty(EMAIL_OAUTH2_REFRESH_TOKEN);
    }

    /**
     * Get the OAuth2 scope.
     */
    public String getOAuth2Scope() {
        return configuration.getProperty(EMAIL_OAUTH2_SCOPE);
    }

    /**
     * Get the OAuth2 username (email address).
     */
    public String getOAuth2Username() {
        return configuration.getProperty(EMAIL_OAUTH2_USERNAME);
    }

    public String getUsername() {
        return configuration.getProperty("email.username");
    }

    public String getPassword() {
        return configuration.getProperty("email.password");
    }

    public String getProtocol() {
        return configuration.getProperty("email.protocol:smtp");
    }

    public String getFrom() {
        return configuration.getProperty("email.from");
    }

    public boolean isEnabled() {
        return Boolean.valueOf(configuration.getProperty("email.enabled", "false"));
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
        final Map<String, Object> emailProperties = EnvironmentUtils.getPropertiesStartingWith(environment, EMAIL_PROPERTIES_PREFIX);
        if (emailProperties.containsKey(EMAIL_PROPERTIES_PREFIX + "." + propName)) {
            return (T) emailProperties.get(EMAIL_PROPERTIES_PREFIX + "." + propName);
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
