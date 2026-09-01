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
package io.gravitee.am.repository.mongodb.management;

import io.gravitee.am.model.application.ApplicationOAuthSettings;
import io.gravitee.am.repository.mongodb.management.internal.model.ApplicationOAuthSettingsMongo;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Applications stored before the ID-JAG lifetime existed carry no such field, and must read as the
 * default lifetime rather than as zero.
 */
public class MongoApplicationIdJagValidityMappingTest {

    @Test
    public void shouldReadDocumentWithoutIdJagValidityAsTheDefault() {
        ApplicationOAuthSettingsMongo legacy = new ApplicationOAuthSettingsMongo();

        assertEquals(ApplicationOAuthSettings.DEFAULT_ID_JAG_VALIDITY_SECONDS, legacy.getIdJagValiditySeconds());
        assertEquals(ApplicationOAuthSettings.DEFAULT_ID_JAG_VALIDITY_SECONDS,
                MongoApplicationRepository.readIdJagValiditySeconds(legacy));
    }

    @Test
    public void shouldReadAStoredZeroAsTheDefault() {
        ApplicationOAuthSettingsMongo stored = new ApplicationOAuthSettingsMongo();
        stored.setIdJagValiditySeconds(0);

        assertEquals(ApplicationOAuthSettings.DEFAULT_ID_JAG_VALIDITY_SECONDS,
                MongoApplicationRepository.readIdJagValiditySeconds(stored));
    }

    @Test
    public void shouldKeepAStoredLifetime() {
        ApplicationOAuthSettingsMongo stored = new ApplicationOAuthSettingsMongo();
        stored.setIdJagValiditySeconds(120);

        assertEquals(120, MongoApplicationRepository.readIdJagValiditySeconds(stored));
    }
}
