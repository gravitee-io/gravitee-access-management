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
package io.gravitee.am.reporter.tcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.gravitee.am.reporter.api.ReporterConfiguration;
import io.gravitee.secrets.api.annotation.Secret;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * UI-configurable settings for the TCP reporter plugin.
 *
 * <p>These fields are stored in the database and managed through the management console.
 * Fallback file settings ({@code reporters.tcp.fallback.*}) are <em>not</em> included here;
 * they are read from {@code gravitee.yaml} via Spring's {@code Environment}.</p>
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_ABSENT)
@EqualsAndHashCode
public class TcpReporterConfiguration implements ReporterConfiguration {

    /** TCP server hostname or IP address. */
    private String host = "localhost";

    /** TCP server port. */
    private int port = 9000;

    /** Maximum time (ms) to wait for a TCP connection to be established. */
    private int connectTimeout = 10000;

    /** Number of reconnect attempts before giving up. {@code -1} for infinite. */
    private int reconnectAttempts = 10;

    /** Time (ms) between reconnect attempts. */
    private long reconnectInterval = 500L;

    /** Time (ms) to wait before starting a new reconnect cycle. */
    private long retryTimeout = 5000L;

    /** Audit event serialisation format: {@code JSON}, {@code MESSAGE_PACK}, {@code CSV}, or {@code ELASTICSEARCH}. */
    private String output = "JSON";

    /** TLS / SSL settings. Never {@code null}; defaults to disabled. */
    private SslConfiguration ssl = new SslConfiguration();

    // -------------------------------------------------------------------------
    // Nested configuration objects
    // -------------------------------------------------------------------------

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class SslConfiguration {

        private boolean enabled = false;

        /** When {@code true} any server certificate is accepted (not recommended for production). */
        private boolean trustAll = false;

        /** Verify that the server hostname matches the TLS certificate. */
        private boolean verifyHost = true;

        private KeyStoreConfiguration keystore;
        private TrustStoreConfiguration truststore;
    }

    /**
     * Client keystore for mutual TLS authentication.
     *
     * <ul>
     *   <li><b>JKS / PKCS12</b>: populate {@code value} (base64-encoded keystore bytes) and
     *       {@code password}.</li>
     *   <li><b>PEM</b>: populate {@code certValue} (PEM certificate) and {@code keyValue}
     *       (PEM private key).</li>
     * </ul>
     */
    @Getter
    @Setter
    @EqualsAndHashCode
    public static class KeyStoreConfiguration {

        /** Keystore type: {@code jks}, {@code pkcs12}, or {@code pem}. */
        private String type;

        /** Base64-encoded keystore content (JKS or PKCS12 only). */
        @Secret
        private String value;

        @Secret
        private String password;

        /** PEM certificate content (PEM type only). */
        @Secret
        private String certValue;

        /** PEM private key content (PEM type only). */
        @Secret
        private String keyValue;
    }

    /**
     * Truststore for server certificate verification.
     *
     * <ul>
     *   <li><b>JKS / PKCS12</b>: populate {@code value} (base64-encoded truststore bytes) and
     *       {@code password}.</li>
     *   <li><b>PEM</b>: populate {@code certValue} (PEM CA certificate).</li>
     * </ul>
     */
    @Getter
    @Setter
    @EqualsAndHashCode
    public static class TrustStoreConfiguration {

        /** Truststore type: {@code jks}, {@code pkcs12}, or {@code pem}. */
        private String type;

        /** Base64-encoded truststore content (JKS or PKCS12 only). */
        @Secret
        private String value;

        @Secret
        private String password;

        /** PEM CA certificate content (PEM type only). */
        @Secret
        private String certValue;
    }
}
