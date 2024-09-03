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
package io.gravitee.am.identityprovider.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class DefaultIdentityProviderGroupMapperTest {

    private DefaultIdentityProviderGroupMapper groupMapper = new DefaultIdentityProviderGroupMapper();

    @Test
    public void should_return_group_by_group_mapper(){
        groupMapper.setProperties(Map.of("GR1", new String[]{"username=lukasz"}));
        List<String> list = groupMapper.apply(new DummyAuthenticationContext(Map.of(), new DummyRequest()), Map.of("username", "lukasz"));

        Assertions.assertTrue(list.contains("GR1"));
    }

    @Test
    public void should_not_return_group_by_group_mapper(){
        groupMapper.setProperties(Map.of("GR1", new String[]{"username=lukasz"}));
        List<String> list = groupMapper.apply(new DummyAuthenticationContext(Map.of(), new DummyRequest()), Map.of("username", "xxx"));

        Assertions.assertFalse(list.contains("GR1"));
    }


}