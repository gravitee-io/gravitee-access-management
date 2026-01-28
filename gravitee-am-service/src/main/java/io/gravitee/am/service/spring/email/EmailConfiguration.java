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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        javaMailSender.setUsername(getUsername());
        javaMailSender.setPassword(getPassword());
        javaMailSender.setProtocol(getProtocol());
        javaMailSender.setJavaMailProperties(propertiesLoader.load(environment));
        return javaMailSender;
    }

    public String getHost() {
        return configuration.getProperty("email.host" );
    }

    public String getPort() {
        return configuration.getProperty("email.port");
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
