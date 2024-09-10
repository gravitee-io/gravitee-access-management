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

import io.gravitee.am.common.env.RepositoriesEnvironment;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import static io.r2dbc.spi.ConnectionFactoryOptions.SSL;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TlsOptionsHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(TlsOptionsHelper.class);
    private static final String POSTGRESQL = "postgresql";
    private static final String MYSQL = "mysql";
    private static final String MARIADB = "mariadb";
    private static final String SQLSERVER = "sqlserver";
    public static final String SSL_MODE = "sslMode";
    public static final String SSL_SERVER_CERT = "sslServerCert";
    public static final String VERIFY_CA = "VERIFY_CA";
    public static final String TRUST_STORE_PATH = "trustStore.path";
    public static final String TRUST_STORE_PASSWORD = "trustStore.password";
    public static final String FALSE = "false";

    public static ConnectionFactoryOptions.Builder setSSLOptions(ConnectionFactoryOptions.Builder builder, RepositoriesEnvironment environment, String prefix, String driver) {
        final boolean useSSL = Boolean.parseBoolean(environment.getProperty(prefix + "sslEnabled", FALSE));
        if (useSSL) {
            switch (driver) {
                case POSTGRESQL:
                    builder = postgresOptions(builder, environment, prefix);
                    break;
                case MYSQL:
                    builder = mysqlOptions(builder, environment, prefix);
                    break;
                case MARIADB:
                    builder = mariadbOptions(builder, environment, prefix);
                    break;
                case SQLSERVER:
                    builder = sqlServerOptions(builder, environment, prefix);
                    break;
                default:
                    LOGGER.warn("Unknown driver {}, skip SSL configuration", driver);
            }
        } else {
            switch (driver) {
                case POSTGRESQL:
                    builder.option(SSL, false).option(Option.valueOf(SSL_MODE), "disable");
                    break;
                case MARIADB:
                    builder.option(Option.valueOf("SslMode"), "disable");
                    break;
                case MYSQL:
                    builder.option(Option.valueOf(SSL_MODE), "DISABLED");
                    break;
                case SQLSERVER:
                    builder.option(Option.valueOf("encrypt"), FALSE);
                    break;
                default:
                    LOGGER.warn("Unknown driver {}, skipping SSL disable configuration for JDBC url", driver);
            }
        }

        return builder;
    }

    private static ConnectionFactoryOptions.Builder postgresOptions(ConnectionFactoryOptions.Builder builder, RepositoriesEnvironment environment, String prefix) {
        final String sslMode = environment.getProperty(prefix + SSL_MODE, "verify-ca");
        final String sslServerCert = environment.getProperty(prefix + SSL_SERVER_CERT);
        return builder
                .option(SSL, true)
                .option(Option.valueOf(SSL_MODE), sslMode)
                .option(Option.valueOf("sslRootCert"), sslServerCert);
    }

    private static ConnectionFactoryOptions.Builder mysqlOptions(ConnectionFactoryOptions.Builder builder, RepositoriesEnvironment environment, String prefix) {
        final String sslMode = environment.getProperty(prefix + SSL_MODE, VERIFY_CA);
        final String tlsProtocol = environment.getProperty(prefix + "tlsProtocol", "TLSv1.2");
        final String sslServerCert = environment.getProperty(prefix + SSL_SERVER_CERT);
        return builder
                .option(SSL, true)
                .option(Option.valueOf(SSL_MODE), sslMode)
                .option(Option.valueOf("sslCa"), sslServerCert)
                .option(Option.valueOf("tlsVersion"), tlsProtocol);
    }

    private static ConnectionFactoryOptions.Builder mariadbOptions(ConnectionFactoryOptions.Builder builder, RepositoriesEnvironment environment, String prefix) {
        final String sslMode = environment.getProperty(prefix + SSL_MODE, VERIFY_CA);
        final String tlsProtocol = environment.getProperty(prefix + "tlsProtocol", "TLSv1.2");
        final String sslServerCert = environment.getProperty(prefix + SSL_SERVER_CERT);
        return builder
                .option(SSL, true)
                .option(Option.valueOf(SSL_MODE), sslMode)
                .option(Option.valueOf("serverSslCert"), sslServerCert)
                .option(Option.valueOf("tlsVersion"), tlsProtocol);
    }

    private static ConnectionFactoryOptions.Builder sqlServerOptions(ConnectionFactoryOptions.Builder builder, RepositoriesEnvironment environment, String prefix) {
        final String trustStore = environment.getProperty(prefix + TRUST_STORE_PATH);
        final String trustStorePassword = environment.getProperty(prefix + TRUST_STORE_PASSWORD);
        return builder
                .option(SSL, true)
                .option(Option.valueOf("trustStore"), trustStore)
                .option(Option.valueOf("trustStorePassword"), trustStorePassword);
    }

    public static String setSSLOptions(String jdbcUrl, RepositoriesEnvironment environment, String prefix, String driver) {
        final boolean useSSL = Boolean.parseBoolean(environment.getProperty(prefix + "sslEnabled", FALSE));
        String jdbcUrlWithSSL = jdbcUrl;

        if (useSSL) {
            switch (driver) {
                case POSTGRESQL:
                    jdbcUrlWithSSL = postgresOptions(jdbcUrl, environment, prefix);
                    break;
                case MYSQL:
                    jdbcUrlWithSSL = mysqlOptions(jdbcUrl, environment, prefix);
                    break;
                case MARIADB:
                    jdbcUrlWithSSL = mariadbOptions(jdbcUrl, environment, prefix);
                    break;
                case SQLSERVER:
                    jdbcUrlWithSSL = sqlServerOptions(jdbcUrl, environment, prefix);
                    break;
                default:
                    LOGGER.warn("Unknown driver {}, skip SSL configuration for JDBC url", driver);
            }
        } else {
            switch (driver) {
                case POSTGRESQL:
                    jdbcUrlWithSSL = jdbcUrl + "?ssl=false&sslmode=disable";
                    break;
                case MYSQL:
                    jdbcUrlWithSSL = jdbcUrl + "?useSSL=false&allowPublicKeyRetrieval=true";
                    break;
                case MARIADB:
                    jdbcUrlWithSSL = jdbcUrl + "?sslMode=disable";
                    break;
                case SQLSERVER:
                    jdbcUrlWithSSL = jdbcUrl + ";encrypt=false";
                    break;
                default:
                    LOGGER.warn("Unknown driver {}, skipping SSL disable configuration for JDBC url", driver);
            }
        }

        return jdbcUrlWithSSL;
    }


    private static String postgresOptions(String jdbcUrl, RepositoriesEnvironment environment, String prefix) {
        final String sslMode = environment.getProperty(prefix + SSL_MODE, "verify-ca");
        final String sslServerCert = environment.getProperty(prefix + SSL_SERVER_CERT);

        StringBuilder builder = new StringBuilder(jdbcUrl)
                .append("?ssl=true")
                .append("&sslmode=").append(sslMode);

        if  (sslServerCert != null) {
            builder = builder.append("&sslrootcert=").append(sslServerCert);
        }

        return builder.toString();
    }

    private static String mysqlOptions(String jdbcUrl, RepositoriesEnvironment environment, String prefix) {
        final String sslMode = environment.getProperty(prefix + SSL_MODE, VERIFY_CA);
        final String trustStore = environment.getProperty(prefix + TRUST_STORE_PATH);
        final String trustStorePassword = environment.getProperty(prefix + TRUST_STORE_PASSWORD);

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

    private static String mariadbOptions(String jdbcUrl, RepositoriesEnvironment environment, String prefix) {
        final String sslMode = environment.getProperty(prefix + SSL_MODE, VERIFY_CA);
        final String sslServerCert = environment.getProperty(prefix + SSL_SERVER_CERT);
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

    private static String sqlServerOptions(String jdbcUrl, RepositoriesEnvironment environment, String prefix) {
        final String trustServerCertificate = environment.getProperty(prefix + "trustServerCertificate");
        final String trustStore = environment.getProperty(prefix + TRUST_STORE_PATH);
        final String trustStorePassword = environment.getProperty(prefix + TRUST_STORE_PASSWORD);

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
