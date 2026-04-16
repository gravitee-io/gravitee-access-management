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
package io.gravitee.am.service.model.openid;

import io.gravitee.am.model.oidc.CIMDSettings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author GraviteeSource Team
 */
@RunWith(JUnit4.class)
public class PatchCIMDSettingsTest {

    @Test
    public void patchEmptyOptionalNumeric_restoresDomainDefaults() {
        CIMDSettings existing = new CIMDSettings();
        existing.setFetchTimeoutMs(9999);
        existing.setMaxResponseSizeKb(99);
        existing.setCacheTtlSeconds(111);
        existing.setCacheMaxEntries(222);

        PatchCIMDSettings patch = new PatchCIMDSettings();
        patch.setFetchTimeoutMs(Optional.empty());
        patch.setMaxResponseSizeKb(Optional.empty());
        patch.setCacheTtlSeconds(Optional.empty());
        patch.setCacheMaxEntries(Optional.empty());

        CIMDSettings result = patch.patch(existing);
        assertEquals(CIMDSettings.DEFAULT_FETCH_TIMEOUT_MS, result.getFetchTimeoutMs());
        assertEquals(CIMDSettings.DEFAULT_MAX_RESPONSE_SIZE_KB, result.getMaxResponseSizeKb());
        assertEquals(CIMDSettings.DEFAULT_CACHE_TTL_SECONDS, result.getCacheTtlSeconds());
        assertEquals(CIMDSettings.DEFAULT_CACHE_MAX_ENTRIES, result.getCacheMaxEntries());
    }

    @Test
    public void patchNullNumeric_skipsField() {
        CIMDSettings existing = new CIMDSettings();
        existing.setFetchTimeoutMs(9999);

        PatchCIMDSettings patch = new PatchCIMDSettings();

        CIMDSettings result = patch.patch(existing);
        assertEquals(9999, result.getFetchTimeoutMs());
    }

    @Test
    public void patchPresentOptionalNumeric_appliesValue() {
        CIMDSettings existing = new CIMDSettings();
        existing.setFetchTimeoutMs(5000);

        PatchCIMDSettings patch = new PatchCIMDSettings();
        patch.setFetchTimeoutMs(Optional.of(1000));

        CIMDSettings result = patch.patch(existing);
        assertEquals(1000, result.getFetchTimeoutMs());
    }
}
