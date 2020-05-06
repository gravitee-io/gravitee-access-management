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
package io.gravitee.am.gateway.vertx;

import io.vertx.core.http.HttpServerOptions;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class VertxHttpServerConfiguration implements InitializingBean {

    @Autowired
    private ConfigurableEnvironment environment;

    @Value("${http.port:8092}")
    private int port;

    @Value("${http.host:0.0.0.0}")
    private String host;

    @Value("${http.secured:false}")
    private boolean secured;

    @Value("${http.alpn:false}")
    private boolean alpn;

    @Value("${http.ssl.keystore.path:#{null}}")
    private String keyStorePath;

    @Value("${http.ssl.keystore.password:#{null}}")
    private String keyStorePassword;

    @Value("${http.ssl.keystore.type:#{null}}")
    private String keyStoreType;

    @Value("${http.ssl.truststore.path:#{null}}")
    private String trustStorePath;

    @Value("${http.ssl.truststore.password:#{null}}")
    private String trustStorePassword;

    @Value("${http.ssl.truststore.type:#{null}}")
    private String trustStoreType;

    @Value("${http.compressionSupported:" + HttpServerOptions.DEFAULT_COMPRESSION_SUPPORTED + "}")
    private boolean compressionSupported;

    @Value("${http.idleTimeout:" + HttpServerOptions.DEFAULT_IDLE_TIMEOUT + "}")
    private int idleTimeout;

    @Value("${http.tcpKeepAlive:true}")
    private boolean tcpKeepAlive;

    private ClientAuthMode clientAuth;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public boolean isSecured() {
        return secured;
    }

    public void setSecured(boolean secured) {
        this.secured = secured;
    }

    public String getKeyStorePath() {
        return keyStorePath;
    }

    public void setKeyStorePath(String keyStorePath) {
        this.keyStorePath = keyStorePath;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    public String getTrustStorePath() {
        return trustStorePath;
    }

    public void setTrustStorePath(String trustStorePath) {
        this.trustStorePath = trustStorePath;
    }

    public boolean isCompressionSupported() {
        return compressionSupported;
    }

    public void setCompressionSupported(boolean compressionSupported) {
        this.compressionSupported = compressionSupported;
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(int idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public boolean isTcpKeepAlive() {
        return tcpKeepAlive;
    }

    public void setTcpKeepAlive(boolean tcpKeepAlive) {
        this.tcpKeepAlive = tcpKeepAlive;
    }

    public boolean isAlpn() {
        return alpn;
    }

    public void setAlpn(boolean alpn) {
        this.alpn = alpn;
    }

    public String getKeyStoreType() {
        return keyStoreType;
    }

    public void setKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }

    public String getTrustStoreType() {
        return trustStoreType;
    }

    public void setTrustStoreType(String trustStoreType) {
        this.trustStoreType = trustStoreType;
    }

    public ClientAuthMode getClientAuth() {
        return clientAuth;
    }

    public void setClientAuth(ClientAuthMode clientAuth) {
        this.clientAuth = clientAuth;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        String sClientAuthMode = environment.getProperty("http.ssl.clientAuth", ClientAuthMode.NONE.name());

        if (sClientAuthMode.equalsIgnoreCase(Boolean.TRUE.toString())) {
            clientAuth = ClientAuthMode.REQUIRED;
        } else if (sClientAuthMode.equalsIgnoreCase(Boolean.FALSE.toString())) {
            clientAuth = ClientAuthMode.NONE;
        } else {
            clientAuth = ClientAuthMode.valueOf(sClientAuthMode.toUpperCase());
        }
    }

    public enum ClientAuthMode {
        /**
         * No client authentication is requested or required.
         */
        NONE,

        /**
         * Accept authentication if presented by client. If this option is set and the client chooses
         * not to provide authentication information about itself, the negotiations will continue.
         */
        REQUEST,

        /**
         * Require client to present authentication, if not presented then negotiations will be declined.
         */
        REQUIRED
    }
}
