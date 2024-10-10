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
package io.gravitee.am.gateway.handler.scim.model;

import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.Map;

public class GraviteeUserTest {

    @Test
    public void password_additional_information_should_be_banned(){
        GraviteeUser graviteeUser = new GraviteeUser();
        graviteeUser.setAdditionalInformation(Map.of("password","value"));
        Assertions.assertFalse(graviteeUser.getAdditionalInformation().containsKey("password"));

        graviteeUser.setAdditionalInformation(Map.of("anyother","value"));
        Assertions.assertTrue(graviteeUser.getAdditionalInformation().containsKey("anyother"));
    }

}