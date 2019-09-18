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
package io.gravitee.am.gateway.handler.common.jwe;

import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.RSADecrypter;
import io.gravitee.am.gateway.handler.common.jwa.utils.JWAlgorithmUtils;
import io.gravitee.am.gateway.handler.common.jwe.impl.JWEServiceImpl;
import io.gravitee.am.gateway.handler.common.jwk.JWKService;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.model.oidc.JWKSet;
import io.reactivex.Maybe;
import io.reactivex.observers.TestObserver;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.nimbusds.jose.JWEAlgorithm.RSA_OAEP_256;
import static org.junit.Assert.fail;
import static org.junit.runners.Parameterized.Parameters;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(Parameterized.class)
public class JWERsaTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @InjectMocks
    private JWEService jweService = new JWEServiceImpl();

    @Mock
    private JWKService jwkService;

    private int keySize;
    private String alg;
    private String enc;

    public JWERsaTest(int keySize, String alg, String enc) {
        this.keySize = keySize;
        this.alg = alg;
        this.enc = enc;
    }

    @Parameters(name="Encrypt with RSA size {0}, alg {1} enc {2}")
    public static Collection<Object[]> data() {

        List parameters = new ArrayList();

        for(int keySize: Arrays.asList(256*8,384*8,512*8)) {
            for(JWEAlgorithm algorithm: Arrays.asList(RSA_OAEP_256)) {
                for(String enc: JWAlgorithmUtils.getSupportedIdTokenResponseEnc()) {
                    parameters.add(new Object[]{keySize, algorithm.getName(), enc});
                }
            }
        }

        return parameters;
    }

    @Test
    public void encryptIdToken() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(this.keySize);
            KeyPair keyPair = gen.generateKeyPair();

            com.nimbusds.jose.jwk.RSAKey jwk = new com.nimbusds.jose.jwk.RSAKey.Builder((RSAPublicKey)keyPair.getPublic())
                    .privateKey((RSAPrivateKey)keyPair.getPrivate())
                    .build();

            RSAKey key = new RSAKey();
            key.setKid("rsaEnc");
            key.setUse("enc");
            key.setE(jwk.getPublicExponent().toString());
            key.setN(jwk.getModulus().toString());

            Client client = new Client();
            client.setIdTokenEncryptedResponseAlg(this.alg);
            client.setIdTokenEncryptedResponseEnc(this.enc);

            when(jwkService.getKeys(client)).thenReturn(Maybe.just(new JWKSet()));
            when(jwkService.filter(any(),any())).thenReturn(Maybe.just(key));

            TestObserver testObserver = jweService.encryptIdToken("JWT", client).test();
            testObserver.assertNoErrors();
            testObserver.assertComplete();
            testObserver.assertValue(jweString -> {
                JWEObject jwe = JWEObject.parse((String)jweString);
                jwe.decrypt(new RSADecrypter(jwk));
                return "JWT".equals(jwe.getPayload().toString());
            });
        }
        catch(NoSuchAlgorithmException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void encryptUserinfo() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(this.keySize);
            KeyPair keyPair = gen.generateKeyPair();

            com.nimbusds.jose.jwk.RSAKey jwk = new com.nimbusds.jose.jwk.RSAKey.Builder((RSAPublicKey)keyPair.getPublic())
                    .privateKey((RSAPrivateKey)keyPair.getPrivate())
                    .build();

            RSAKey key = new RSAKey();
            key.setKid("rsaEnc");
            key.setUse("enc");
            key.setE(jwk.getPublicExponent().toString());
            key.setN(jwk.getModulus().toString());

            Client client = new Client();
            client.setUserinfoEncryptedResponseAlg(this.alg);
            client.setUserinfoEncryptedResponseEnc(this.enc);

            when(jwkService.getKeys(client)).thenReturn(Maybe.just(new JWKSet()));
            when(jwkService.filter(any(),any())).thenReturn(Maybe.just(key));

            TestObserver testObserver = jweService.encryptUserinfo("JWT", client).test();
            testObserver.assertNoErrors();
            testObserver.assertComplete();
            testObserver.assertValue(jweString -> {
                JWEObject jwe = JWEObject.parse((String)jweString);
                jwe.decrypt(new RSADecrypter(jwk));
                return "JWT".equals(jwe.getPayload().toString());
            });
        }
        catch(NoSuchAlgorithmException e) {
            fail(e.getMessage());
        }
    }
}
