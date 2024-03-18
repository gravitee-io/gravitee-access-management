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
package io.gravitee.am.service.model;

import io.gravitee.am.model.FactorSettings;
import io.gravitee.am.service.exception.InvalidParameterException;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.List;
import java.util.Optional;

public class PatchFactorSettingsTest {

    @Test(expected = InvalidParameterException.class)
    public void whenFactorsAreSelectedButDefaultFactorNotShouldThrowAnException(){
        // given
        PatchApplicationFactorSettings applicationFactorSettings = new PatchApplicationFactorSettings();
        applicationFactorSettings.setId(Optional.of("1"));

        PatchFactorSettings patch = new PatchFactorSettings();
        patch.setApplicationFactors(Optional.of(List.of(applicationFactorSettings)));

        // expect exception
        patch.patch(new FactorSettings());
    }

    @Test
    public void shouldCopyValues(){
        // given
        PatchApplicationFactorSettings applicationFactorSettings = new PatchApplicationFactorSettings();
        applicationFactorSettings.setId(Optional.of("1"));

        PatchFactorSettings settings = new PatchFactorSettings();
        settings.setApplicationFactors(Optional.of(List.of(applicationFactorSettings)));
        settings.setDefaultFactorId(Optional.of("1"));

        FactorSettings toPatch = new FactorSettings();
        toPatch.setApplicationFactors(List.of());
        toPatch.setDefaultFactorId(null);

        // when
        FactorSettings result = settings.patch(toPatch);

        // then
        Assertions.assertEquals("1", result.getDefaultFactorId());
        Assertions.assertEquals(1, result.getApplicationFactors().size());
    }

}