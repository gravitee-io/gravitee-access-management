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
package io.gravitee.am.gateway.handler.oidc.service.jwk.converter;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.am.service.exception.InvalidClientMetadataException;
import io.vertx.core.json.Json;
import io.vertx.core.json.jackson.DatabindCodec;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

/**
 * <pre>
 * NEEDED IN gravitee-am-gateway-handler-common because service need to fetch some resource on web and serialize it.
 *
 "keys" : [
    {
        "e" : "AQAB",
        "kid" : "gH9_h6pSirMu9lmygaNcvYSVPBo21EBQ_nTFYQKbcs0",
        "kty" : "RSA",
        "n" : "v_UAFh4B6IFobGMzIMsE0Fuel5iEtT9a15kI1cdnAdj-ojw0O8q3AT_0dfEW-s9mJdBXsI5pWCzbYcqd2olCM0zV7fck4DmgD63bXjP8bycly4ggMlOwZM9ZH7k4zIntb9_8PKWjBQpekyVIo9rEpSfABc0U0c_emrk1arsGYD1pTGoXk-XS1NY7RS2bdVzPUPfM5iS-iwGch7TVPV3-6fE9eWDo8TpOkJDkZDzzxiY_dJTsr9A2aKbU71c0AHg26tluv3glT13NTct2YzLBG1LnVgR3i_Aoh9Jn08QrHt2NfHoNzykqEAxZqOylyzxdO__KokZS2wkqklIidvbmYw",
        "use" : "enc"
    },
    {
        "crv" : "P-256",
        "kid" : "KySDFYfhULs3n-q-PeFddGnsM651PzHhj3KdQw8bQZM",
        "kty" : "EC",
        "use" : "enc",
        "x" : "vBT2JhFHd62Jcf4yyBzSV9NuDBNBssR1zlmnHelgZcs",
        "y" : "up8E8b3TjeKS2v2GCH23UJP0bak0La77lkQ7_n4djqE"
    }
 ]
 * </pre>
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class JWKSetDeserializerTest {

    @Test
    public void test_nullNode() {
        assertNull("expecting null",new JWKSetDeserializer().convert((ObjectNode)null));
    }

    @Test
    public void test_Empty() {
        ObjectNode node = DatabindCodec.mapper().createObjectNode().set("keys",null);
        Optional<JWKSet> result = new JWKSetDeserializer().convert(node);

        assertFalse("Was expecting an empty result",result.isPresent());
    }

    @Test
    public void test_emptyString() {
        ObjectNode node = DatabindCodec.mapper().createObjectNode().put("keys","");
        Optional<JWKSet> result = new JWKSetDeserializer().convert(node);

        assertFalse("Was expecting an empty result",result.isPresent());
    }

    @Test(expected = InvalidClientMetadataException.class)
    public void test_WrongKeyType() {
        ObjectNode rsaKey = DatabindCodec.mapper().createObjectNode()
                .put("kty","wrongKeyType")
                .put("kid","rsaKid")
                .put("use","enc")
                .put("e","exponent")
                .put("n","modulus");

        ArrayNode arrayNode = DatabindCodec.mapper().createArrayNode().add(rsaKey);
        ObjectNode root = DatabindCodec.mapper().createObjectNode().set("keys",arrayNode);

        new JWKSetDeserializer().convert(root);
    }

    @Test(expected = InvalidClientMetadataException.class)
    public void test_WrongECCurve() {
        ObjectNode ecKey = DatabindCodec.mapper().createObjectNode()
                .put("kty","EC")
                .put("kid","ecKid")
                .put("use","enc")
                .put("crv","wrong")
                .put("x","vBT2JhFHd62Jcf4yyBzSV9NuDBNBssR1zlmnHelgZcs")
                .put("y","up8E8b3TjeKS2v2GCH23UJP0bak0La77lkQ7_n4djqE");

        ArrayNode arrayNode = DatabindCodec.mapper().createArrayNode().add(ecKey);
        ObjectNode root = DatabindCodec.mapper().createObjectNode().set("keys",arrayNode);

        new JWKSetDeserializer().convert(root);
    }

    @Test
    public void test_allKeyType() {
        ObjectNode rsaKey = DatabindCodec.mapper().createObjectNode()
                .put("kty","RSA")
                .put("kid","rsaKid")
                .put("use","enc")
                .put("e","AQAB")
                .put("n","v_UAFh4B6IFobGMzIMsE0Fuel5iEtT9a15kI1cdnAdj-ojw0O8q3AT_0dfEW-s9mJdBXsI5pWCzbYcqd2olCM0zV7fck4DmgD63bXjP8bycly4ggMlOwZM9ZH7k4zIntb9_8PKWjBQpekyVIo9rEpSfABc0U0c_emrk1arsGYD1pTGoXk-XS1NY7RS2bdVzPUPfM5iS-iwGch7TVPV3-6fE9eWDo8TpOkJDkZDzzxiY_dJTsr9A2aKbU71c0AHg26tluv3glT13NTct2YzLBG1LnVgR3i_Aoh9Jn08QrHt2NfHoNzykqEAxZqOylyzxdO__KokZS2wkqklIidvbmYw");

        ObjectNode ecKey = DatabindCodec.mapper().createObjectNode()
                .put("kty","EC")
                .put("kid","ecKid")
                .put("use","enc")
                .put("crv","P-256")
                .put("x","vBT2JhFHd62Jcf4yyBzSV9NuDBNBssR1zlmnHelgZcs")
                .put("y","up8E8b3TjeKS2v2GCH23UJP0bak0La77lkQ7_n4djqE");

        ObjectNode okpKey = DatabindCodec.mapper().createObjectNode()
                .put("kty","OKP")
                .put("kid","okpKid")
                .put("use","enc")
                .put("crv","X25519")
                .put("x","vBNW8f19leF79U4U6NrDDQaK_i5kL0iMKghB39AUT2I");

        ObjectNode octKey = DatabindCodec.mapper().createObjectNode()
                .put("kty","oct")
                .put("kid","octKid")
                .put("use","sig")
                .put("alg","HS256")
                .put("k","FdFYFzERwC2uCBB46pZQi4GG85LujR8obt-KWRBICVQ");

        ArrayNode arrayNode = DatabindCodec.mapper().createArrayNode()
                .add(rsaKey)
                .add(ecKey)
                .add(okpKey)
                .add(octKey);

        ObjectNode root = DatabindCodec.mapper().createObjectNode();
        root.set("keys",arrayNode);

        Optional<JWKSet> result = new JWKSetDeserializer().convert(root);

        assertTrue("Was expecting a result",result.isPresent());
    }
}
