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

import org.junit.Ignore;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

public class DefaultTrustStoreProviderTest {

    @Test
    @Ignore("relies on DefaultTrustStoreProvider not being loaded by the classloader before this test is ran, and we can't guarantee that")
    public void should_return_system_properties_for_default_truststore(){
        String defaultTrustStorePath = DefaultTrustStoreProvider.getDefaultTrustStorePath();
        String defaultTrustStorePassword = DefaultTrustStoreProvider.getDefaultTrustStorePassword();

        Assertions.assertEquals(System.getProperty("javax.net.ssl.trustStore"), defaultTrustStorePath);
        Assertions.assertEquals(System.getProperty("javax.net.ssl.trustStorePassword"), defaultTrustStorePassword);
    }

}