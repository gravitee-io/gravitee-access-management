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
package io.gravitee.am.management.handlers.oauth2.provider.jwt;

import io.gravitee.common.spring.factory.AbstractAutowiringFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.provider.token.store.KeyStoreKeyFactory;

import java.security.KeyPair;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JwtKeyPairFactory extends AbstractAutowiringFactoryBean<KeyPair> {

    /**
     * How to generate a new keystore:
     *
     * keytool -genkeypair -alias mytestkey -keyalg RSA -dname "CN=Web Server,OU=Unit,O=Organization,L=City,S=State,C=US"
     *          -keypass changeme -keystore server.jks -storepass letmein
     */

    @Override
    protected KeyPair doCreateInstance() throws Exception {
        return new KeyStoreKeyFactory(new ClassPathResource("server.jks"), "letmein".toCharArray())
                .getKeyPair("mytestkey", "changeme".toCharArray());
    }

    @Override
    public Class<?> getObjectType() {
        return KeyPair.class;
    }
}
