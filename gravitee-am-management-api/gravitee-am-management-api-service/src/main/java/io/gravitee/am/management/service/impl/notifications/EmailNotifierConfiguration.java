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
package io.gravitee.am.management.service.impl.notifications;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class EmailNotifierConfiguration {
    @Autowired
    private io.gravitee.node.api.configuration.Configuration configuration;

    private String host;
    private int port;
    private String username;
    private String password;
    private String from;
    private String to;
    private String subject;
    private String body;
    private boolean startTLSEnabled;
    private boolean sslTrustAll;
    private String sslKeyStore;
    private String sslKeyStorePassword;

    public EmailNotifierConfiguration() {
    }

    public EmailNotifierConfiguration(EmailNotifierConfiguration other) {
        this.host = other.getHost();
        this.port = other.getPort();
        this.username = other.getUsername();
        this.password = other.getPassword();
        this.from = other.getFrom();
        this.to = other.getTo();
        this.subject = other.getSubject();
        this.body = other.getBody();
        this.startTLSEnabled = other.isStartTLSEnabled();
        this.sslTrustAll = other.isSslTrustAll();
        this.sslKeyStore = other.getSslKeyStore();
        this.sslKeyStorePassword = other.getSslKeyStorePassword();
    }

    public String getHost() {
        if (configuration == null) {
            return host;
        }
        return configuration.getProperty("notifiers.email.host");
    }

    public int getPort() {
        if (configuration == null) {
            return port;
        }
        return configuration.getProperty("notifiers.email.port", Integer.class, 587);
    }

    public String getUsername() {
        if (configuration == null) {
            return username;
        }
        return configuration.getProperty("notifiers.email.username");
    }

    public String getPassword() {
        if (configuration == null) {
            return password;
        }
        return configuration.getProperty("notifiers.email.password");
    }

    public String getFrom() {
        if (configuration == null) {
            return from;
        }
        return configuration.getProperty("notifiers.email.from");
    }

    public String getTo() {
        if (configuration == null) {
            return to;
        }
        return configuration.getProperty("notifiers.email.to");
    }

    public String getSubject() {
        if (configuration == null) {
            return subject;
        }
        return configuration.getProperty("notifiers.email.subject");
    }

    public String getBody() {
        if (configuration == null) {
            return body;
        }
        return configuration.getProperty("notifiers.email.body");
    }

    public boolean isStartTLSEnabled() {
        if (configuration == null) {
            return startTLSEnabled;
        }
        return configuration.getProperty("notifiers.email.startTLSEnabled", Boolean.class, false);
    }

    public boolean isSslTrustAll() {
        if (configuration == null) {
            return sslTrustAll;
        }
        return configuration.getProperty("notifiers.email.sslTrustAll", Boolean.class, false);
    }

    public String getSslKeyStore() {
        if (configuration == null) {
            return sslKeyStore;
        }
        return configuration.getProperty("notifiers.email.sslKeyStore");
    }

    public String getSslKeyStorePassword() {
        if (configuration == null) {
            return sslKeyStorePassword;
        }
        return configuration.getProperty("notifiers.email.sslKeyStorePassword");
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
