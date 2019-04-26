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

import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.AESDecrypter;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import io.gravitee.am.gateway.handler.common.jwa.utils.JWAlgorithmUtils;
import io.gravitee.am.gateway.handler.common.jwe.impl.JWEServiceImpl;
import io.gravitee.am.gateway.handler.common.jwk.JWKService;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.jose.OCTKey;
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

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

import static com.nimbusds.jose.JWEAlgorithm.*;
import static org.junit.Assert.fail;
import static org.junit.runners.Parameterized.Parameters;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(Parameterized.class)
public class JWEAesTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @InjectMocks
    private JWEService jweService = new JWEServiceImpl();

    @Mock
    private JWKService jwkService;

    private String alg;
    private String enc;

    public JWEAesTest(String alg, String enc) {
        this.alg = alg;
        this.enc = enc;
    }

    @Parameters(name="Encrypt with AES alg {0}, enc {1}")
    public static Collection<Object[]> data() {

        //return list of Object[]{Algorithm, Encryption}
        return Arrays.asList(A128KW, A128GCMKW, A192KW, A192GCMKW, A256KW, A256GCMKW).stream().flatMap(
                algorithm -> JWAlgorithmUtils.getSupportedIdTokenResponseEnc().stream().map(
                        enc -> new Object[]{algorithm.getName(),enc}
                )
        ).collect(Collectors.toList());
    }

    @Test
    public void encryptIdToken() {
        try {
            int keySize = 128;
            if (alg.startsWith("A192")) {
                keySize = 192;
            } else if (alg.startsWith("A256")) {
                keySize = 256;
            }

            // Generate a secret AES key with 128 bits
            KeyGenerator gen = KeyGenerator.getInstance("AES");
            gen.init(keySize);
            SecretKey aesKey = gen.generateKey();

            // Convert to JWK format
            OctetSequenceKey jwk = new OctetSequenceKey.Builder(aesKey).build();

            OCTKey key = new OCTKey();
            key.setKid("octEnc");
            key.setUse("enc");
            key.setK(jwk.getKeyValue().toString());

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
                jwe.decrypt(new AESDecrypter(jwk));
                return "JWT".equals(jwe.getPayload().toString());
            });
        }
        catch(NoSuchAlgorithmException e) {
            fail(e.getMessage());
        }
    }
}
