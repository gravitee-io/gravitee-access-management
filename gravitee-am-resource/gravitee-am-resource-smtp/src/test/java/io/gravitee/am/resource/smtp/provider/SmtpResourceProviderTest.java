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
package io.gravitee.am.resource.smtp.provider;

import io.gravitee.am.resource.smtp.configuration.SmtpResourceConfiguration;
import io.gravitee.am.service.utils.EmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SmtpResourceProviderTest {

    @Test
    void should_pass_credentials_to_the_mail_sender_when_authentication_is_inferred() throws Exception {
        var configuration = baseConfiguration();
        configuration.setUsername("user");
        configuration.setPassword("password");

        var javaMailSender = startAndExtractMailSender(configuration);

        assertEquals("user", javaMailSender.getUsername());
        assertEquals("password", javaMailSender.getPassword());
        assertEquals("true", javaMailSender.getJavaMailProperties().getProperty("mail.smtp.auth"));
    }

    @Test
    void should_not_authenticate_when_no_credentials_are_provided() throws Exception {
        var javaMailSender = startAndExtractMailSender(baseConfiguration());

        assertNull(javaMailSender.getUsername());
        assertNull(javaMailSender.getPassword());
        assertEquals("false", javaMailSender.getJavaMailProperties().getProperty("mail.smtp.auth"));
    }

    private static SmtpResourceConfiguration baseConfiguration() {
        var configuration = new SmtpResourceConfiguration();
        configuration.setHost("localhost");
        configuration.setPort(25);
        // the authentication flag is left unset so the provider has to infer it from the credentials
        return configuration;
    }

    private static JavaMailSenderImpl startAndExtractMailSender(SmtpResourceConfiguration configuration) throws Exception {
        var provider = new SmtpResourceProvider();
        setField(provider, "configuration", configuration);
        setField(provider, "env", new StandardEnvironment());
        provider.start();

        var emailSender = (EmailSender) readField(provider, "mailSender");
        return (JavaMailSenderImpl) readField(emailSender, "mailSender");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object readField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
