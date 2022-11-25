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
package io.gravitee.am.gateway.handler.scim.mapper;

import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.oidc.idtoken.Claims;
import io.gravitee.am.common.scim.Schema;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.scim.model.GraviteeUser;
import io.gravitee.am.gateway.handler.scim.model.User;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertTrue;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserMapperTest {

    @Test
    public void shouldConvert_customGraviteeUser() {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        user.setAdditionalInformation(Collections.singletonMap("customClaim", "customValue"));
        User scimUser = UserMapper.convert(user, "/", false);
        assertTrue(scimUser.getSchemas().contains(Schema.SCHEMA_URI_CUSTOM_USER));
        assertTrue(((GraviteeUser) scimUser).getAdditionalInformation().containsKey("customClaim"));
    }

    @Test
    public void shouldConvert_customGraviteeUser_shouldNotContainStandardClaims() {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        user.setAdditionalInformation(standardClaims());
        User scimUser = UserMapper.convert(user, "/", false);
        assertTrue(scimUser.getSchemas().contains(Schema.SCHEMA_URI_USER));
        assertTrue(((GraviteeUser) scimUser).getAdditionalInformation() == null);
    }

    @Test
    public void shouldConvert_customGraviteeUser_shouldNotContainRestrictedClaims() {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        user.setAdditionalInformation(restrictedClaims());
        User scimUser = UserMapper.convert(user, "/", false);
        assertTrue(scimUser.getSchemas().contains(Schema.SCHEMA_URI_USER));
        assertTrue(((GraviteeUser) scimUser).getAdditionalInformation() == null);
    }

    @Test
    public void shouldConvert_customGraviteeUser_shouldNotContainAllClaims() {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        Map<String, Object> additionalInformation = new HashMap<>(standardClaims());
        additionalInformation.putAll(restrictedClaims());
        user.setAdditionalInformation(additionalInformation);
        User scimUser = UserMapper.convert(user, "/", false);
        assertTrue(scimUser.getSchemas().contains(Schema.SCHEMA_URI_USER));
        assertTrue(((GraviteeUser) scimUser).getAdditionalInformation() == null);
    }

    private Map<String, Object> standardClaims() {
        return StandardClaims.claims()
                .stream()
                .collect(Collectors.toMap(s -> s, s -> s));
    }

    private Map<String, Object> restrictedClaims() {
        return List.of(Claims.auth_time, ConstantKeys.OIDC_PROVIDER_ID_ACCESS_TOKEN_KEY, ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY)
                .stream()
                .collect(Collectors.toMap(s -> s, s -> s));
    }
}
