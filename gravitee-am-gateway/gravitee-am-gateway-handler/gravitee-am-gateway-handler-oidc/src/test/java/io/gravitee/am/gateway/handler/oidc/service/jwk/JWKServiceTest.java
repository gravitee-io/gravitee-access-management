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
package io.gravitee.am.gateway.handler.oidc.service.jwk;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JWEAlgorithm;
import io.gravitee.am.certificate.api.CertificateProvider;
import io.gravitee.am.gateway.handler.common.certificate.CertificateManager;
import io.gravitee.am.gateway.handler.oidc.service.jwk.impl.JWKServiceImpl;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.model.jose.ECKey;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.jose.OCTKey;
import io.gravitee.am.model.jose.OKPKey;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.*;

/**
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class JWKServiceTest {

    private static final String JWKS_URI = "http://client/jwk/uri";
    private static JWKSet JWK_SET;

    @InjectMocks
    private JWKService jwkService = new JWKServiceImpl();

    @Mock
    public WebClient webClient;

    @Mock
    private CertificateManager certificateManager;

    @BeforeClass
    public static void setUp() {
        RSAKey rsaEnc = new RSAKey();
        rsaEnc.setKty("RSA");
        rsaEnc.setKid("rsaEnc");
        rsaEnc.setUse("enc");
        rsaEnc.setN("lFAsvOm58TV5q9zyb3psQSESezZtYLZryGjq8LMnuqRt9cdPQCvMrnjcqdFWiXkD4ZXRO2Wp1iyzgprecx3dAnaD-KHlZR7vsFEmDh27DgNvEx5jKRSy5N2quI2LJw66Jb9JeMqoX6vtv_z3PRHb-zUhnIw6tBwZtuNE-AZSC6atr8ZCLXn6RPqJq_eoGgG-xaAzWPyRXDIqWPVO0RD3odjs6er7BcqVyHg54DyylrmRI4m6xERxpuNYI57bQN5_7a_3tR7hLeHJ8J1mNraMLH7H5_aAM_oSqKBEG9jHSTR7JsI3gSvsNOG-nP9jYxw7fH_c1XfRuTEJfBPEZxzD2Q");

        RSAKey rsaSig = new RSAKey();
        rsaSig.setKty("RSA");
        rsaSig.setKid("rsaSig");
        rsaSig.setUse("sig");

        ECKey ecEnc = new ECKey();
        ecEnc.setKty("EC");
        ecEnc.setKid("ecEnc");

        ECKey ecSig = new ECKey();
        ecSig.setKty("EC");
        ecSig.setKid("ecSig");

        OCTKey oct128 = new OCTKey();
        oct128.setKty("oct");
        oct128.setKid("octEnc128");
        oct128.setUse("enc");
        oct128.setK("d8unGeXwCEDFsYBiaWuyKg");//128bits (16 bytes)

        OCTKey oct192 = new OCTKey();
        oct192.setKty("oct");
        oct192.setKid("octEnc192");
        oct192.setUse("enc");
        oct192.setK("G9jUYv3b0-0wZWCGxAnIUH6gI0kjeXj4");//192bits (24 bytes)

        OCTKey oct256 = new OCTKey();
        oct256.setKty("oct");
        oct256.setKid("octEnc256");
        oct256.setUse("enc");
        oct256.setK("RlrxxWClnDX_dpa47lvC29vBiB-ZDCg-b8n70Ugefyo");//256bits (32 bytes)

        OCTKey oct384 = new OCTKey();
        oct384.setKty("oct");
        oct384.setKid("octEnc384");
        oct384.setUse("enc");
        oct384.setK("MBNrGN8nwS7hlOVfqEy6qA98bzyo1BLGxr-kyN1E4UXYWQDkBg4L7AQRwpZdrKKS");//384bits (48 bytes)

        OCTKey oct512 = new OCTKey();
        oct512.setKty("oct");
        oct512.setKid("octEnc512");
        oct512.setUse("enc");
        oct512.setK("LfWisS5p-ohMbNbeWdiSapnHgA62XPu8DXzyzNZQHtQPglHf0Lb6NUM-8aQGj_YWErvODY5rQkpKeolrBKkcmg");//512bits (64 bytes)

        OCTKey octSig = new OCTKey();
        octSig.setKty("oct");
        octSig.setKid("octSig");
        octSig.setUse("sig");

        RSAKey mtlsKeys = new RSAKey();
        rsaSig.setKty("RSA");
        rsaSig.setKid("rsaSig");
        rsaSig.setUse("mtls");

        JWK_SET = new JWKSet();
        JWK_SET.setKeys(Arrays.asList(rsaEnc, rsaSig, ecEnc, ecSig, oct128, oct192, oct256, oct384, oct512, octSig, mtlsKeys));
    }

    @Test
    public void testGetKeys_UriException() {
        TestObserver testObserver = jwkService.getKeys("blabla").test();

        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void testGetKeys_errorResponse() {

        HttpRequest<Buffer> request = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> response = Mockito.mock(HttpResponse.class);


        when(webClient.getAbs(anyString())).thenReturn(request);
        when(request.rxSend()).thenReturn(Single.just(response));

        TestObserver testObserver = jwkService.getKeys(JWKS_URI).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void testGetKeys_parseException() {

        HttpRequest<Buffer> request = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> response = Mockito.mock(HttpResponse.class);


        when(webClient.getAbs(anyString())).thenReturn(request);
        when(request.rxSend()).thenReturn(Single.just(response));
        when(response.bodyAsString()).thenReturn("{\"unknown\":[]}");

        TestObserver testObserver = jwkService.getKeys(JWKS_URI).test();

        testObserver.assertError(InvalidClientMetadataException.class);
        testObserver.assertNotComplete();
    }

    @Test
    public void testGetKeys() {

        HttpRequest<Buffer> request = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> response = Mockito.mock(HttpResponse.class);

        String bodyAsString = "{\"keys\":[{\"kty\": \"RSA\",\"use\": \"enc\",\"kid\": \"KID\",\"n\": \"modulus\",\"e\": \"exponent\"}]}";

        when(webClient.getAbs(anyString())).thenReturn(request);
        when(request.rxSend()).thenReturn(Single.just(response));
        when(response.bodyAsString()).thenReturn(bodyAsString);

        TestObserver testObserver = jwkService.getKeys(JWKS_URI).test();

        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(jwkSet -> ((JWKSet)jwkSet).getKeys().get(0).getKid().equals("KID"));
    }

    @Test
    public void testGetKey_noKid() {

        JWK jwk = Mockito.mock(JWK.class);
        JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(Arrays.asList(jwk));

        TestObserver testObserver = jwkService.getKey(jwkSet,null).test();

        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertResult();//Expect empty result
    }

    @Test
    public void testGetKey_noKFound() {

        JWK jwk = Mockito.mock(JWK.class);
        JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(Arrays.asList(jwk));

        when(jwk.getKid()).thenReturn("notTheExpectedOne");

        TestObserver testObserver = jwkService.getKey(jwkSet,"expectedKid").test();

        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertResult();//Expect empty result
    }

    @Test
    public void testGetKey_ok() {

        JWK jwk = Mockito.mock(JWK.class);
        JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(Arrays.asList(jwk));

        when(jwk.getKid()).thenReturn("expectedKid");

        TestObserver testObserver = jwkService.getKey(jwkSet,"expectedKid").test();

        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertResult(jwk);
    }

    @Test
    public void testGetClientKeys_noKeys() {
        TestObserver testObserver = jwkService.getKeys(new Client()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertResult();//Expect empty result
    }

    @Test
    public void testGetClientKeys_fromJksProperty() {
        JWK jwk = Mockito.mock(JWK.class);
        JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(Arrays.asList(jwk));
        Client client = new Client();
        client.setJwks(jwkSet);

        TestObserver testObserver = jwkService.getKeys(client).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertResult(client.getJwks());
    }

    @Test
    public void testGetClientKeys_fromJksUriProperty() {
        Client client = new Client();
        client.setJwksUri(JWKS_URI);

        HttpRequest<Buffer> request = Mockito.mock(HttpRequest.class);
        HttpResponse<Buffer> response = Mockito.mock(HttpResponse.class);

        String bodyAsString = "{\"keys\":[{\"kty\": \"RSA\",\"use\": \"enc\",\"kid\": \"KID\",\"n\": \"modulus\",\"e\": \"exponent\"}]}";

        when(webClient.getAbs(anyString())).thenReturn(request);
        when(request.rxSend()).thenReturn(Single.just(response));
        when(response.bodyAsString()).thenReturn(bodyAsString);

        TestObserver testObserver = jwkService.getKeys(client).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(jwkSet -> ((JWKSet)jwkSet).getKeys().get(0).getKid().equals("KID"));
    }

    @Test
    public void testFilter_nullOrEmpty() {
        JWKSet jwkSet = null;
        testFilter_expectEmptyResult(jwkSet);
        jwkSet = new JWKSet();
        testFilter_expectEmptyResult(jwkSet);
        jwkSet.setKeys(Arrays.asList());
        testFilter_expectEmptyResult(jwkSet);
    }

    private void testFilter_expectEmptyResult(JWKSet jwkSet) {
        TestObserver testObserver = jwkService.filter(jwkSet,null).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertResult();//Expect empty result
    }

    @Test
    public void testFilter_RSA() {
        TestObserver testObserver = jwkService.filter(JWK_SET, JWKFilter.RSA_KEY_ENCRYPTION()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(jwk -> ((JWK)jwk).getKid().equals("rsaEnc"));
    }

    @Test
    public void testFilter_EC() {
        TestObserver testObserver = jwkService.filter(JWK_SET, JWKFilter.CURVE_KEY_ENCRYPTION()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(jwk -> ((JWK)jwk).getKid().equals("ecEnc"));
    }

    @Test
    public void testFilter_AES_notMatchingAlgorithm() {
        TestObserver testObserver = jwkService.filter(JWK_SET, JWKFilter.OCT_KEY_ENCRYPTION(JWEAlgorithm.parse("none"))).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertResult();
    }

    @Test
    public void testFilter_AES_128_keys() {
        TestObserver testObserver = jwkService.filter(JWK_SET, JWKFilter.OCT_KEY_ENCRYPTION(JWEAlgorithm.A128KW)).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(jwk -> ((JWK)jwk).getKid().equals("octEnc128"));

        testObserver = jwkService.filter(JWK_SET, JWKFilter.OCT_KEY_ENCRYPTION(JWEAlgorithm.A128GCMKW)).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(jwk -> ((JWK)jwk).getKid().equals("octEnc128"));
    }


    @Test
    public void testFilter_AES_no_128_keys() {
        OCTKey oct192 = new OCTKey();
        oct192.setKty("oct");
        oct192.setKid("octEnc192");
        oct192.setUse("enc");
        oct192.setK("G9jUYv3b0-0wZWCGxAnIUH6gI0kjeXj4");//192bits (24 bytes)

        OCTKey oct256 = new OCTKey();
        oct256.setKty("oct");
        oct256.setKid("octEnc256");
        oct256.setUse("enc");
        oct256.setK("RlrxxWClnDX_dpa47lvC29vBiB-ZDCg-b8n70Ugefyo");//256bits (32 bytes)

        OCTKey octSig = new OCTKey();
        octSig.setKty("oct");
        octSig.setKid("octSig");
        octSig.setUse("sig");

        JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(Arrays.asList(oct192, oct256, octSig));

        TestObserver testObserver = jwkService.filter(jwkSet, JWKFilter.OCT_KEY_ENCRYPTION(JWEAlgorithm.A128KW)).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertResult();

        testObserver = jwkService.filter(jwkSet, JWKFilter.OCT_KEY_ENCRYPTION(JWEAlgorithm.A128GCMKW)).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertResult();
    }

    @Test
    public void testFilter_AES_192_keys() {
        TestObserver testObserver = jwkService.filter(JWK_SET, JWKFilter.OCT_KEY_ENCRYPTION(JWEAlgorithm.A192KW)).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(jwk -> ((JWK)jwk).getKid().equals("octEnc192"));

        testObserver = jwkService.filter(JWK_SET, JWKFilter.OCT_KEY_ENCRYPTION(JWEAlgorithm.A192GCMKW)).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(jwk -> ((JWK)jwk).getKid().equals("octEnc192"));
    }

    @Test
    public void testFilter_AES_no_192_keys() {
        OCTKey oct128 = new OCTKey();
        oct128.setKty("oct");
        oct128.setKid("octEnc128");
        oct128.setUse("enc");
        oct128.setK("d8unGeXwCEDFsYBiaWuyKg");//128bits (16 bytes)

        OCTKey oct256 = new OCTKey();
        oct256.setKty("oct");
        oct256.setKid("octEnc256");
        oct256.setUse("enc");
        oct256.setK("RlrxxWClnDX_dpa47lvC29vBiB-ZDCg-b8n70Ugefyo");//256bits (32 bytes)

        OCTKey octSig = new OCTKey();
        octSig.setKty("oct");
        octSig.setKid("octSig");
        octSig.setUse("sig");

        JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(Arrays.asList(oct128, oct256, octSig));

        TestObserver testObserver = jwkService.filter(jwkSet, JWKFilter.OCT_KEY_ENCRYPTION(JWEAlgorithm.A192KW)).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertResult();

        testObserver = jwkService.filter(jwkSet, JWKFilter.OCT_KEY_ENCRYPTION(JWEAlgorithm.A192GCMKW)).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertResult();
    }

    @Test
    public void testFilter_AES_256_keys() {
        TestObserver testObserver = jwkService.filter(JWK_SET, JWKFilter.OCT_KEY_ENCRYPTION(JWEAlgorithm.A256KW)).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(jwk -> ((JWK)jwk).getKid().equals("octEnc256"));

        testObserver = jwkService.filter(JWK_SET, JWKFilter.OCT_KEY_ENCRYPTION(JWEAlgorithm.A256GCMKW)).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(jwk -> ((JWK)jwk).getKid().equals("octEnc256"));
    }

    @Test
    public void testFilter_AES_no_256_keys() {
        OCTKey oct128 = new OCTKey();
        oct128.setKty("oct");
        oct128.setKid("octEnc128");
        oct128.setUse("enc");
        oct128.setK("d8unGeXwCEDFsYBiaWuyKg");//128bits (16 bytes)

        OCTKey oct192 = new OCTKey();
        oct192.setKty("oct");
        oct192.setKid("octEnc192");
        oct192.setUse("enc");
        oct192.setK("G9jUYv3b0-0wZWCGxAnIUH6gI0kjeXj4");//192bits (24 bytes)

        OCTKey octSig = new OCTKey();
        octSig.setKty("oct");
        octSig.setKid("octSig");
        octSig.setUse("sig");

        JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(Arrays.asList(oct128, oct192, octSig));

        TestObserver testObserver = jwkService.filter(jwkSet, JWKFilter.OCT_KEY_ENCRYPTION(JWEAlgorithm.A256KW)).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertResult();

        testObserver = jwkService.filter(jwkSet, JWKFilter.OCT_KEY_ENCRYPTION(JWEAlgorithm.A256GCMKW)).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertResult();
    }

    @Test
    public void testFilter_OCT() {
        TestObserver testObserver = jwkService.filter(JWK_SET, JWKFilter.OCT_KEY_ENCRYPTION()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(jwk -> ((JWK)jwk).getKid().equals("octEnc128"));
    }

    @Test
    public void testFilter_OCT_byEnc() {
        TestObserver testObserver = jwkService.filter(JWK_SET, JWKFilter.OCT_KEY_ENCRYPTION(EncryptionMethod.A128GCM)).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(jwk -> ((JWK)jwk).getKid().equals("octEnc128"));

        testObserver = jwkService.filter(JWK_SET, JWKFilter.OCT_KEY_ENCRYPTION(EncryptionMethod.A128CBC_HS256)).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(jwk -> ((JWK)jwk).getKid().equals("octEnc256"));

        testObserver = jwkService.filter(JWK_SET, JWKFilter.OCT_KEY_ENCRYPTION(EncryptionMethod.A192GCM)).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(jwk -> ((JWK)jwk).getKid().equals("octEnc192"));

        testObserver = jwkService.filter(JWK_SET, JWKFilter.OCT_KEY_ENCRYPTION(EncryptionMethod.A192CBC_HS384)).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(jwk -> ((JWK)jwk).getKid().equals("octEnc384"));

        testObserver = jwkService.filter(JWK_SET, JWKFilter.OCT_KEY_ENCRYPTION(EncryptionMethod.A256GCM)).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(jwk -> ((JWK)jwk).getKid().equals("octEnc256"));

        testObserver = jwkService.filter(JWK_SET, JWKFilter.OCT_KEY_ENCRYPTION(EncryptionMethod.A256CBC_HS512)).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(jwk -> ((JWK)jwk).getKid().equals("octEnc512"));
    }

    @Test
    public void testFilter_OKP() {
        OKPKey okpEnc = new OKPKey();
        okpEnc.setKty("OKP");
        okpEnc.setKid("okpEnc");
        okpEnc.setCrv("X25519");

        OKPKey okpSig = new OKPKey();
        okpSig.setKty("OKP");
        okpSig.setKid("okpSig");
        okpSig.setCrv("Ed25519");

        JWKSet okpSet = new JWKSet();
        okpSet.setKeys(Arrays.asList(okpEnc,okpSig));


        TestObserver testObserver = jwkService.filter(okpSet, JWKFilter.CURVE_KEY_ENCRYPTION()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertValue(jwk -> ((JWK)jwk).getKid().equals("okpEnc"));
    }

    @Test
    public void testFilter_RSA_weakKey() {
        //key size is <2048 bits
        RSAKey rsaEnc = new RSAKey();
        rsaEnc.setKty("RSA");
        rsaEnc.setKid("rsaEnc");
        rsaEnc.setUse("enc");
        rsaEnc.setN("nRuv8E_c8aLRlyMz4h2SKWKHkzmDO49TVXppes1IqRdFACg_7cEhKfV5-jiNVxH3nKFGcHw6IG3qCJe_-pEJhnTbIdYS98UJmVZuudD_7lH5JgVhaV3ZwY6aQIMsoE5YhMyi55jbHPS-GqSIGonlVlgpHX_VjxKtj-u_-824xZU");

        RSAKey rsaSig = new RSAKey();
        rsaSig.setKty("RSA");
        rsaSig.setKid("rsaSig");
        rsaSig.setUse("sig");

        JWKSet rsaSet = new JWKSet();
        rsaSet.setKeys(Arrays.asList(rsaEnc, rsaSig));


        TestObserver testObserver = jwkService.filter(rsaSet, JWKFilter.RSA_KEY_ENCRYPTION()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertResult();
    }

    @Test
    public void testFilter_noneMatch() {
        OKPKey okpSig = new OKPKey();
        okpSig.setKty("OKP");
        okpSig.setKid("okpSig");
        okpSig.setCrv("Ed25519");

        JWKSet okpSet = new JWKSet();
        okpSet.setKeys(Arrays.asList(okpSig));


        TestObserver testObserver = jwkService.filter(okpSet, JWKFilter.CURVE_KEY_ENCRYPTION()).test();
        testObserver.assertNoErrors();
        testObserver.assertComplete();
        testObserver.assertResult();
    }


    @Test
    public void shouldGetJWKSet_singleKey() throws Exception  {
        io.gravitee.am.model.jose.JWK key = new io.gravitee.am.model.jose.RSAKey();
        key.setKid("my-test-key");

        CertificateProvider certificateProvider = mock(CertificateProvider.class);
        when(certificateProvider.keys()).thenReturn(Flowable.just(key));

        when(certificateManager.providers()).thenReturn(Collections.singletonList(new io.gravitee.am.gateway.certificate.CertificateProvider(certificateProvider)));

        TestObserver<JWKSet> testObserver = jwkService.getKeys().test();

        testObserver.await(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(jwkSet -> "my-test-key".equals(jwkSet.getKeys().get(0).getKid()));
    }

    @Test
    public void shouldGetJWKSet_noCertificateProvider() throws Exception  {
        when(certificateManager.providers()).thenReturn(Collections.emptySet());

        TestObserver<JWKSet> testObserver = jwkService.getKeys().test();
        testObserver.await(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(jwkSet -> jwkSet.getKeys().isEmpty());
    }

    @Test
    public void shouldGetJWKSet_multipleKeys() throws Exception {
        io.gravitee.am.model.jose.JWK key = new io.gravitee.am.model.jose.RSAKey();
        key.setKid("my-test-key");
        io.gravitee.am.model.jose.JWK key2 = new io.gravitee.am.model.jose.RSAKey();
        key2.setKid("my-test-key-2");

        CertificateProvider certificateProvider = mock(CertificateProvider.class);
        when(certificateProvider.keys()).thenReturn(Flowable.just(key));
        CertificateProvider certificateProvider2 = mock(CertificateProvider.class);
        when(certificateProvider2.keys()).thenReturn(Flowable.just(key2));

        List<io.gravitee.am.gateway.certificate.CertificateProvider> certificateProviders = new ArrayList<>();
        certificateProviders.add(new io.gravitee.am.gateway.certificate.CertificateProvider(certificateProvider));
        certificateProviders.add(new io.gravitee.am.gateway.certificate.CertificateProvider(certificateProvider2));

        when(certificateManager.providers()).thenReturn(certificateProviders);

        TestObserver<JWKSet> testObserver = jwkService.getKeys().test();
        testObserver.await(5, TimeUnit.SECONDS);

        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(jwkSet -> jwkSet.getKeys().size() == 2);
    }

    @Test
    public void shouldGetJWKSet_filter_mtls() throws Exception {
        io.gravitee.am.model.jose.JWK key = new io.gravitee.am.model.jose.RSAKey();
        key.setKid("my-test-key");
        key.setUse("sig");
        io.gravitee.am.model.jose.JWK key2 = new io.gravitee.am.model.jose.RSAKey();
        key.setKid("my-test-key-2");
        key.setUse("enc");
        io.gravitee.am.model.jose.JWK key3 = new io.gravitee.am.model.jose.RSAKey();
        key3.setKid("my-test-key-3");
        key3.setUse("mtls");

        CertificateProvider certificateProvider = mock(CertificateProvider.class);
        when(certificateProvider.keys()).thenReturn(Flowable.just(key));
        CertificateProvider certificateProvider2 = mock(CertificateProvider.class);
        when(certificateProvider2.keys()).thenReturn(Flowable.just(key2));
        CertificateProvider certificateProvider3 = mock(CertificateProvider.class);
        when(certificateProvider3.keys()).thenReturn(Flowable.just(key3));

        List<io.gravitee.am.gateway.certificate.CertificateProvider> certificateProviders = new ArrayList<>();
        certificateProviders.add(new io.gravitee.am.gateway.certificate.CertificateProvider(certificateProvider));
        certificateProviders.add(new io.gravitee.am.gateway.certificate.CertificateProvider(certificateProvider2));
        certificateProviders.add(new io.gravitee.am.gateway.certificate.CertificateProvider(certificateProvider3));

        when(certificateManager.providers()).thenReturn(certificateProviders);

        TestObserver<JWKSet> testObserver = jwkService.getKeys().test();

        testObserver.await(5, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(jwkSet -> jwkSet.getKeys().size() == 2);
    }

}
