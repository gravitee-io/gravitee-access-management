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
package io.gravitee.am.identityprovider.mongo.authentication.spring;

import io.gravitee.am.identityprovider.mongo.MongoIdentityProviderConfiguration;
import io.gravitee.am.identityprovider.mongo.utils.PasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.MD5PasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.NoOpPasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.PBKDF2PasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.SHAMD5PasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.SHAPasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.bcrypt.BCryptPasswordEncoder;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class MongoAuthenticationProviderConfigurationTest {

    @Mock
    private MongoIdentityProviderConfiguration configuration;

    @InjectMocks
    private MongoAuthenticationProviderConfiguration mongoAuthenticationProviderConfiguration;

    @Test
    public void shouldUsePasswordEncoder_noConfiguration() {
        when(configuration.getPasswordEncoder()).thenReturn(null);
        var passwordEncoder = mongoAuthenticationProviderConfiguration.passwordEncoder();
        Assert.assertEquals(NoOpPasswordEncoder.class, passwordEncoder.getClass());
    }

    @Test
    public void shouldUsePasswordEncoder_noop_Configuration() {
        when(configuration.getPasswordEncoder()).thenReturn(PasswordEncoder.NONE);
        var passwordEncoder = mongoAuthenticationProviderConfiguration.passwordEncoder();
        Assert.assertEquals(NoOpPasswordEncoder.class, passwordEncoder.getClass());
    }

    @Test
    public void shouldUsePasswordEncoder_bcrypt_Configuration() {
        when(configuration.getPasswordEncoder()).thenReturn(PasswordEncoder.BCRYPT);
        var passwordEncoder = mongoAuthenticationProviderConfiguration.passwordEncoder();
        Assert.assertEquals(BCryptPasswordEncoder.class, passwordEncoder.getClass());
    }

    @Test
    public void shouldUsePasswordEncoder_pbkdf2_sha1Configuration() {
        when(configuration.getPasswordEncoder()).thenReturn(Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA1.name());
        var passwordEncoder = mongoAuthenticationProviderConfiguration.passwordEncoder();
        Assert.assertEquals(PBKDF2PasswordEncoder.class, passwordEncoder.getClass());
    }

    @Test
    public void shouldUsePasswordEncoder_pbkdf2_sha256Configuration() {
        when(configuration.getPasswordEncoder()).thenReturn(Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256.name());
        var passwordEncoder = mongoAuthenticationProviderConfiguration.passwordEncoder();
        Assert.assertEquals(PBKDF2PasswordEncoder.class, passwordEncoder.getClass());
    }

    @Test
    public void shouldUsePasswordEncoder_pbkdf2_sha512Configuration() {
        when(configuration.getPasswordEncoder()).thenReturn(Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA512.name());
        var passwordEncoder = mongoAuthenticationProviderConfiguration.passwordEncoder();
        Assert.assertEquals(PBKDF2PasswordEncoder.class, passwordEncoder.getClass());
    }

    @Test
    public void shouldUsePasswordEncoder_md5_Configuration() {
        when(configuration.getPasswordEncoder()).thenReturn(PasswordEncoder.MD5);
        var passwordEncoder = mongoAuthenticationProviderConfiguration.passwordEncoder();
        Assert.assertEquals(MD5PasswordEncoder.class, passwordEncoder.getClass());
    }

    @Test
    public void shouldUsePasswordEncoder_sha_Configuration() {
        when(configuration.getPasswordEncoder()).thenReturn(PasswordEncoder.SHA);
        var passwordEncoder = mongoAuthenticationProviderConfiguration.passwordEncoder();
        Assert.assertEquals(SHAPasswordEncoder.class, passwordEncoder.getClass());
    }

    @Test
    public void shouldUsePasswordEncoder_shamd5_Configuration() {
        when(configuration.getPasswordEncoder()).thenReturn(PasswordEncoder.SHA + "+" + PasswordEncoder.MD5);
        var passwordEncoder = mongoAuthenticationProviderConfiguration.passwordEncoder();
        Assert.assertEquals(SHAMD5PasswordEncoder.class, passwordEncoder.getClass());
    }
}
