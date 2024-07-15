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

import org.junit.Test;
import org.junit.jupiter.api.Assertions;

public class DefaultTrustStoreProviderTest {

    @Test
    public void should_return_system_properties_for_default_truststore(){
        // given
        String path = "/var/trust/store";
        String passwd = "passwd";

        // when
        System.setProperty("javax.net.ssl.trustStore", path);
        System.setProperty("javax.net.ssl.trustStorePassword", passwd);

        String defaultTrustStorePath = DefaultTrustStoreProvider.getDefaultTrustStorePath();
        String defaultTrustStorePassword = DefaultTrustStoreProvider.getDefaultTrustStorePassword();

        // then
        Assertions.assertEquals(path, defaultTrustStorePath);
        Assertions.assertEquals(passwd, defaultTrustStorePassword);
    }

}