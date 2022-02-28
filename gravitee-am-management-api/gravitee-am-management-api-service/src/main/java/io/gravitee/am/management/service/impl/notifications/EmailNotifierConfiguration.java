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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class EmailNotifierConfiguration {

    @Value("${notifiers.email.host:#{null}}")
    private String host;
    @Value("${notifiers.email.port:587}")
    private int port;
    @Value("${notifiers.email.username:#{null}}")
    private String username;
    @Value("${notifiers.email.password:#{null}}")
    private String password;

    @Value("${notifiers.email.from:#{null}}")
    private String from;
    @Value("${notifiers.email.to:#{null}}")
    private String to;
    @Value("${notifiers.email.subject:#{null}}")
    private String subject;
    @Value("${notifiers.email.body:#{null}}")
    private String body;

    @Value("${notifiers.email.startTLSEnabled:false}")
    private boolean startTLSEnabled;
    @Value("${notifiers.email.sslTrustAll:false}")
    private boolean sslTrustAll;
    @Value("${notifiers.email.sslKeyStore:#{null}}")
    private String sslKeyStore;
    @Value("${notifiers.email.sslKeyStorePassword:#{null}}")
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
        this.startTLSEnabled = other.startTLSEnabled;
        this.sslTrustAll = other.isSslTrustAll();
        this.sslKeyStore = other.getSslKeyStore();
        this.sslKeyStorePassword = other.getSslKeyStorePassword();
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
