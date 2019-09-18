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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.X25519Decrypter;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import io.gravitee.am.gateway.handler.common.jwa.utils.JWAlgorithmUtils;
import io.gravitee.am.gateway.handler.common.jwe.impl.JWEServiceImpl;
import io.gravitee.am.gateway.handler.common.jwk.JWKService;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.jose.OKPKey;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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
public class JWEEdwardCurveTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @InjectMocks
    private JWEService jweService = new JWEServiceImpl();

    @Mock
    private JWKService jwkService;

    private String alg;
    private String enc;

    public JWEEdwardCurveTest(String alg, String enc) {
        this.alg = alg;
        this.enc = enc;
    }

    @Parameters(name="Encrypt with Eward Curve, alg {0} enc {1}")
    public static Collection<Object[]> data() {

        List parameters = new ArrayList();

        for(JWEAlgorithm algorithm: Arrays.asList(ECDH_ES, ECDH_ES_A128KW, ECDH_ES_A192KW, ECDH_ES_A256KW)) {
            for(String enc: JWAlgorithmUtils.getSupportedIdTokenResponseEnc()) {
                parameters.add(new Object[]{algorithm.getName(), enc});
            }
        }

        return parameters;
    }

    @Test
    public void encryptIdToken() {
        try {
            final OctetKeyPair jwk = new OctetKeyPairGenerator(Curve.X25519).generate();
            OKPKey key = new OKPKey();
            key.setKid("okpEnc");
            key.setUse("enc");
            key.setCrv(jwk.getCurve().getName());
            key.setX(jwk.getX().toString());

            Client client = new Client();
            client.setIdTokenEncryptedResponseAlg(alg);
            client.setIdTokenEncryptedResponseEnc(enc);

            when(jwkService.getKeys(client)).thenReturn(Maybe.just(new JWKSet()));
            when(jwkService.filter(any(),any())).thenReturn(Maybe.just(key));

            TestObserver testObserver = jweService.encryptIdToken("JWT", client).test();
            testObserver.assertNoErrors();
            testObserver.assertComplete();
            testObserver.assertValue(jweString -> {
                try {
                    JWEObject jwe = JWEObject.parse((String)jweString);
                    jwe.decrypt(new X25519Decrypter(jwk));
                    return "JWT".equals(jwe.getPayload().toString());
                }catch (JOSEException e) {
                    fail(e.getMessage());
                }
                return false;
            });
        }
        catch (JOSEException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void encryptUserinfo() {
        try {
            final OctetKeyPair jwk = new OctetKeyPairGenerator(Curve.X25519).generate();
            OKPKey key = new OKPKey();
            key.setKid("okpEnc");
            key.setUse("enc");
            key.setCrv(jwk.getCurve().getName());
            key.setX(jwk.getX().toString());

            Client client = new Client();
            client.setUserinfoEncryptedResponseAlg(alg);
            client.setUserinfoEncryptedResponseEnc(enc);

            when(jwkService.getKeys(client)).thenReturn(Maybe.just(new JWKSet()));
            when(jwkService.filter(any(),any())).thenReturn(Maybe.just(key));

            TestObserver testObserver = jweService.encryptUserinfo("JWT", client).test();
            testObserver.assertNoErrors();
            testObserver.assertComplete();
            testObserver.assertValue(jweString -> {
                try {
                    JWEObject jwe = JWEObject.parse((String)jweString);
                    jwe.decrypt(new X25519Decrypter(jwk));
                    return "JWT".equals(jwe.getPayload().toString());
                }catch (JOSEException e) {
                    fail(e.getMessage());
                }
                return false;
            });
        }
        catch (JOSEException e) {
            fail(e.getMessage());
        }
    }
}
