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

import io.gravitee.am.service.spring.email.EmailConfiguration;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmailNotifierConfiguration {
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

    public EmailNotifierConfiguration(EmailConfiguration emailConfiguration, boolean sslTrustAll, String keytore, String storePasswd) {
        this.setHost(emailConfiguration.getHost());
        if (emailConfiguration.getPort() != null) {
            this.setPort(Integer.parseInt(emailConfiguration.getPort()));
        }
        this.setFrom(emailConfiguration.getFrom());

        if (emailConfiguration.useAuth()) {
            this.setPassword(emailConfiguration.getPassword());
            this.setUsername(emailConfiguration.getUsername());
        }

        this.setStartTLSEnabled(emailConfiguration.useStartTls());
        this.setSslTrustAll(sslTrustAll);
        this.setSslKeyStore(keytore);
        this.setSslKeyStorePassword(storePasswd);
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public boolean isStartTLSEnabled() {
        return startTLSEnabled;
    }

    public void setStartTLSEnabled(boolean startTLSEnabled) {
        this.startTLSEnabled = startTLSEnabled;
    }

    public boolean isSslTrustAll() {
        return sslTrustAll;
    }

    public void setSslTrustAll(boolean sslTrustAll) {
        this.sslTrustAll = sslTrustAll;
    }

    public String getSslKeyStore() {
        return sslKeyStore;
    }

    public void setSslKeyStore(String sslKeyStore) {
        this.sslKeyStore = sslKeyStore;
    }

    public String getSslKeyStorePassword() {
        return sslKeyStorePassword;
    }

    public void setSslKeyStorePassword(String sslKeyStorePassword) {
        this.sslKeyStorePassword = sslKeyStorePassword;
    }
}
