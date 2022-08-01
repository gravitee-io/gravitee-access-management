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
package io.gravitee.am.repository.jdbc.provider.utils;

import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import static io.r2dbc.spi.ConnectionFactoryOptions.SSL;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TlsOptionsHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(TlsOptionsHelper.class);

    public static ConnectionFactoryOptions.Builder setSSLOptions(ConnectionFactoryOptions.Builder builder, Environment environment, String prefix, String driver) {
        final boolean useSSL = Boolean.valueOf(environment.getProperty(prefix + "sslEnabled", "false"));
        if (useSSL) {
            switch (driver) {
                case "postgresql":
                    builder = postgresOptions(builder, environment, prefix);
                    break;
                case "mysql":
                    builder = mysqlOptions(builder, environment, prefix);
                    break;
                case "mariadb":
                    builder = mariadbOptions(builder, environment, prefix);
                    break;
                case "sqlserver":
                    builder = sqlServerOptions(builder, environment, prefix);
                    break;
                default:
                    LOGGER.warn("Unknown driver {}, skip SSL configuration", driver);
            }
        }

        return builder;
    }

    private static ConnectionFactoryOptions.Builder postgresOptions(ConnectionFactoryOptions.Builder builder, Environment environment, String prefix) {
        final String sslMode = environment.getProperty(prefix + "sslMode", "verify-ca");
        final String sslServerCert = environment.getProperty(prefix + "sslServerCert");
        return builder
                .option(SSL, true)
                .option(Option.valueOf("sslMode"), sslMode)
                .option(Option.valueOf("sslRootCert"), sslServerCert);
    }

    private static ConnectionFactoryOptions.Builder mysqlOptions(ConnectionFactoryOptions.Builder builder, Environment environment, String prefix) {
        final String sslMode = environment.getProperty(prefix + "sslMode", "VERIFY_CA");
        final String tlsProtocol = environment.getProperty(prefix + "tlsProtocol", "TLSv1.2");
        final String sslServerCert = environment.getProperty(prefix + "sslServerCert");
        return builder
                .option(SSL, true)
                .option(Option.valueOf("sslMode"), sslMode)
                .option(Option.valueOf("sslCa"), sslServerCert)
                .option(Option.valueOf("tlsVersion"), tlsProtocol);
    }

    private static ConnectionFactoryOptions.Builder mariadbOptions(ConnectionFactoryOptions.Builder builder, Environment environment, String prefix) {
        final String sslMode = environment.getProperty(prefix + "sslMode", "VERIFY_CA");
        final String tlsProtocol = environment.getProperty(prefix + "tlsProtocol", "TLSv1.2");
        final String sslServerCert = environment.getProperty(prefix + "sslServerCert");
        return builder
                .option(SSL, true)
                .option(Option.valueOf("sslMode"), sslMode)
                .option(Option.valueOf("serverSslCert"), sslServerCert)
                .option(Option.valueOf("tlsVersion"), tlsProtocol);
    }

    private static ConnectionFactoryOptions.Builder sqlServerOptions(ConnectionFactoryOptions.Builder builder, Environment environment, String prefix) {
        final String trustStore = environment.getProperty(prefix + "trustStore.path");
        final String trustStorePassword = environment.getProperty(prefix + "trustStore.password");
        return builder
                .option(SSL, true)
                .option(Option.valueOf("trustStore"), trustStore)
                .option(Option.valueOf("trustStorePassword"), trustStorePassword);
    }

    public static String setSSLOptions(String jdbcUrl, Environment environment, String prefix, String driver) {
        final boolean useSSL = Boolean.valueOf(environment.getProperty(prefix + "sslEnabled", "false"));
        String jdbcUrlWithSSL = jdbcUrl;

        if (useSSL) {
            switch (driver) {
                case "postgresql":
                    jdbcUrlWithSSL = postgresOptions(jdbcUrl, environment, prefix);
                    break;
                case "mysql":
                    jdbcUrlWithSSL = mysqlOptions(jdbcUrl, environment, prefix);
                    break;
                case "mariadb":
                    jdbcUrlWithSSL = mariadbOptions(jdbcUrl, environment, prefix);
                    break;
                case "sqlserver":
                    jdbcUrlWithSSL = sqlServerOptions(jdbcUrl, environment, prefix);
                    break;
                default:
                    LOGGER.warn("Unknown driver {}, skip SSL configuration for JDBC url", driver);
            }
        }

        return jdbcUrlWithSSL;
    }


    private static String postgresOptions(String jdbcUrl, Environment environment, String prefix) {
        final String sslMode = environment.getProperty(prefix + "sslMode", "verify-ca");
        final String sslServerCert = environment.getProperty(prefix + "sslServerCert");

        StringBuilder builder = new StringBuilder(jdbcUrl)
                .append("?ssl=true")
                .append("&sslmode=").append(sslMode);

        if  (sslServerCert != null) {
            builder = builder.append("&sslrootcert=").append(sslServerCert);
        }

        return builder.toString();
    }

    private static String mysqlOptions(String jdbcUrl, Environment environment, String prefix) {
        final String sslMode = environment.getProperty(prefix + "sslMode", "VERIFY_CA");
        final String trustStore = environment.getProperty(prefix + "trustStore.path");
        final String trustStorePassword = environment.getProperty(prefix + "trustStore.password");

        StringBuilder builder = new StringBuilder(jdbcUrl)
                .append("?sslMode=").append(sslMode);

        if (trustStore != null) {
            builder = builder.append("&trustCertificateKeyStoreUrl=file:").append(trustStore).append("&trustCertificateKeyStoreType=PKCS12");
        }

        if (trustStorePassword != null) {
            builder = builder.append("&trustCertificateKeyStorePassword=").append(trustStorePassword);
        }

        return builder.toString();
    }

    private static String mariadbOptions(String jdbcUrl, Environment environment, String prefix) {
        final String sslMode = environment.getProperty(prefix + "sslMode", "VERIFY_CA");
        final String sslServerCert = environment.getProperty(prefix + "sslServerCert");
        final String disableSslHostnameVerification = environment.getProperty(prefix + "disableSslHostnameVerification");

        StringBuilder builder = new StringBuilder(jdbcUrl)
                .append("?useSSL=true")
                .append("&sslMode=").append(sslMode);

        if (sslServerCert != null) {
            builder = builder.append("&serverSslCert=").append(sslServerCert);
        }

        if (disableSslHostnameVerification != null) {
            builder = builder.append("&disableSslHostnameVerification=").append(Boolean.valueOf(disableSslHostnameVerification));
        }

        return builder.toString();
    }

    private static String sqlServerOptions(String jdbcUrl, Environment environment, String prefix) {
        final String trustServerCertificate = environment.getProperty(prefix + "trustServerCertificate");
        final String trustStore = environment.getProperty(prefix + "trustStore.path");
        final String trustStorePassword = environment.getProperty(prefix + "trustStore.password");

        StringBuilder builder = new StringBuilder(jdbcUrl)
                .append(";encrypt=true");

        if (trustServerCertificate != null) {
            builder.append(";trustServerCertificate=").append(trustServerCertificate);
        }

        if (trustStore != null) {
            builder.append(";trustStore=").append(trustStore);
        }

        if (trustStorePassword != null) {
            builder.append(";trustStorePassword=").append(trustStorePassword);
        }

        return builder.toString();
    }
}
