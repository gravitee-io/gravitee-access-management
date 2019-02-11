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
package io.gravitee.am.identityprovider.ldap.authentication.encoding;

import org.ldaptive.Credential;
import org.ldaptive.LdapException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class MessageDigestPasswordEncoder implements PasswordEncoder {

    private String algorithm;

    public MessageDigestPasswordEncoder(String algorithm) {
        this.algorithm = algorithm;
    }

    @Override
    public byte[] digestCredential(Credential credential) throws LdapException {
        try {
            final MessageDigest md = MessageDigest.getInstance(algorithm);
            md.update(credential.getBytes());
            return md.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new LdapException(e);
        }
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }
}
