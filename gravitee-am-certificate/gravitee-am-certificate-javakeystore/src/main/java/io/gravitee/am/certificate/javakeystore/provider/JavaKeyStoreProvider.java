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
package io.gravitee.am.certificate.javakeystore.provider;

import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.certificate.javakeystore.JavaKeyStoreConfiguration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JavaKeyStoreProvider implements CertificateProvider, InitializingBean {

    private KeyPair keyPair;

    @Autowired
    private JavaKeyStoreConfiguration configuration;

    @Override
    public void afterPropertiesSet() throws Exception {
        FileInputStream is = new FileInputStream(configuration.getJks());
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        keystore.load(is, configuration.getStorepass().toCharArray());
        Key key = keystore.getKey(configuration.getAlias(), configuration.getKeypass().toCharArray());
        if (key instanceof PrivateKey) {
            // Get certificate of public key
            Certificate cert = keystore.getCertificate(configuration.getAlias());
            // Get public key
            PublicKey publicKey = cert.getPublicKey();
            // Return a key pair
            keyPair = new KeyPair(publicKey, (PrivateKey) key);
        } else {
            throw new IllegalArgumentException("A RSA Signer must be supplied");
        }
    }

    @Override
    public String sign(String payload) {
        return Jwts.builder().setPayload(payload).signWith(SignatureAlgorithm.RS512, keyPair.getPrivate()).compact();
    }

    @Override
    public String publicKey() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
       /* encode the "ssh-rsa" string */
        try {
            byte[] sshrsa = new byte[] {0, 0, 0, 7, 's', 's', 'h', '-', 'r', 's', 'a'};
            out.write(sshrsa);
            /* Encode the public exponent */
            BigInteger e = ((RSAPublicKey) keyPair.getPublic()).getPublicExponent();
            byte[] data = e.toByteArray();
            encodeUInt32(data.length, out);
            out.write(data);
            /* Encode the modulus */
            BigInteger m = ((RSAPublicKey) keyPair.getPublic()).getModulus();
            data = m.toByteArray();
            encodeUInt32(data.length, out);
            out.write(data);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }

    private void encodeUInt32(int value, OutputStream out) throws IOException {
        byte[] tmp = new byte[4];
        tmp[0] = (byte)((value >>> 24) & 0xff);
        tmp[1] = (byte)((value >>> 16) & 0xff);
        tmp[2] = (byte)((value >>> 8) & 0xff);
        tmp[3] = (byte)(value & 0xff);
        out.write(tmp);
    }


}
