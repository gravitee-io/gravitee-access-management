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
import com.nimbusds.jose.crypto.ECDHDecrypter;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import io.gravitee.am.gateway.handler.common.jwa.utils.JWAlgorithmUtils;
import io.gravitee.am.gateway.handler.common.jwe.impl.JWEServiceImpl;
import io.gravitee.am.gateway.handler.common.jwk.JWKService;
import io.gravitee.am.model.Client;
import io.gravitee.am.model.jose.ECKey;
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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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
public class JWEEllipticCurveTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @InjectMocks
    private JWEService jweService = new JWEServiceImpl();

    @Mock
    private JWKService jwkService;

    private Curve crv;
    private String alg;
    private String enc;

    public JWEEllipticCurveTest(Curve crv, String alg, String enc) {
        this.crv = crv;
        this.alg = alg;
        this.enc = enc;
    }

    @Parameters(name="Encrypt with Elliptic Curve {0}, alg {1} enc {2}")
    public static Collection<Object[]> data() {

        final List<Curve> curveList = Arrays.asList(Curve.P_256, Curve.P_384, Curve.P_521);
        final List<JWEAlgorithm> algorithmList = Arrays.asList(ECDH_ES, ECDH_ES_A128KW, ECDH_ES_A192KW, ECDH_ES_A256KW);

        //return list of Object[]{Curve, Algorithm, Encryption}
        return curveList.stream().flatMap(
                curve -> algorithmList.stream().flatMap(
                        algorithm -> JWAlgorithmUtils.getSupportedIdTokenResponseEnc().stream().map(
                                enc -> new Object[]{curve,algorithm.getName(),enc}
                        )
                )
        ).collect(Collectors.toList());
    }

    @Test
    public void encryptIdToken() {
        try {
            //prepare encryption private & public key
            com.nimbusds.jose.jwk.ECKey jwk = new ECKeyGenerator(this.crv).generate();

            ECKey key = new ECKey();
            key.setKid("ecEnc");
            key.setUse("enc");
            key.setCrv(jwk.getCurve().getName());
            key.setX(jwk.getX().toString());
            key.setY(jwk.getY().toString());

            Client client = new Client();
            client.setIdTokenEncryptedResponseAlg(alg);
            client.setIdTokenEncryptedResponseEnc(enc);

            when(jwkService.getKeys(client)).thenReturn(Maybe.just(new JWKSet()));
            when(jwkService.filter(any(), any())).thenReturn(Maybe.just(key));

            TestObserver testObserver = jweService.encryptIdToken("JWT", client).test();
            testObserver.assertNoErrors();
            testObserver.assertComplete();
            testObserver.assertValue(jweString -> {
                JWEObject jwe = JWEObject.parse((String) jweString);
                jwe.decrypt(new ECDHDecrypter(jwk));
                return "JWT".equals(jwe.getPayload().toString());
            });
        }
        catch (JOSEException e) {
            fail(e.getMessage());
        }
    }
}
