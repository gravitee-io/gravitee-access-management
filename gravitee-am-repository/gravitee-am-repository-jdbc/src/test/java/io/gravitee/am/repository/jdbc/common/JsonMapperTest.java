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
package io.gravitee.am.repository.jdbc.common;

import io.gravitee.am.model.jose.ECKey;
import io.gravitee.am.model.jose.JWK;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JsonMapperTest {

    @Test
    public void testJWKDeserialization() {
        ECKey eck = new ECKey();
        eck.setCrv("CrvValue");
        eck.setD("Dva");
        eck.setX("Xva");
        eck.setY("Yva");
        eck.setAlg("Algo");
        eck.setKeyOps(new HashSet<>(Arrays.asList("4", "ops")));
        eck.setKid("kidval");
        eck.setUse("usesomething");
        eck.setX5t("x5tval");
        eck.setX5u("x5uval");

        String json = JSONMapper.toJson(eck);

        JWK jwk = JSONMapper.toBean(json, ECKey.class);
        assertEquals(eck.getCrv(), ((ECKey) jwk).getCrv());
        assertEquals(eck.getD(), ((ECKey) jwk).getD());
        assertEquals(eck.getX(), ((ECKey) jwk).getX());
        assertEquals(eck.getY(), ((ECKey) jwk).getY());

        assertEquals(eck.getAlg(), jwk.getAlg());
        assertEquals(eck.getKid(), jwk.getKid());

    }
}
