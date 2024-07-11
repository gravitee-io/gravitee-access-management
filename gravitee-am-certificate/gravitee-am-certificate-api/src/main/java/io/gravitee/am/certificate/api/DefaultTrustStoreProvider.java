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
package io.gravitee.am.certificate.api;


public final class DefaultTrustStoreProvider {
    private static final String JAVA_DEFAULT_TRUSTSTORE_PROPERTY = "javax.net.ssl.trustStore";
    private static final String JAVA_DEFAULT_TRUSTSTORE_PASSWORD_PROPERTY = "javax.net.ssl.trustStorePassword";
    private static final String UNIFORMED_FILE_PREFIX = "file:";

    private DefaultTrustStoreProvider() {
    }

    public static String defaultTrustStorePassword() {
        return System.getProperty(JAVA_DEFAULT_TRUSTSTORE_PASSWORD_PROPERTY);
    }

    public static String defaultTrustStorePath() {
        return System.getProperty(JAVA_DEFAULT_TRUSTSTORE_PROPERTY);
    }

    public static String uniformedDefaultTrustStorePath() {
        return UNIFORMED_FILE_PREFIX + defaultTrustStorePath();
    }

}
