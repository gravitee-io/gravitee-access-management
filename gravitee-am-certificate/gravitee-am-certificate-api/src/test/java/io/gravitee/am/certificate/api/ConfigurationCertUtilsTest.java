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
package io.gravitee.am.certificate.api;

import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.List;

/**
 * @author Lukasz GAWEL (lukasz.gawel at graviteesource.com)
 * @author GraviteeSource Team
 */

public class ConfigurationCertUtilsTest {

    @Test
    public void shouldExtractListOfUsages() {
        String cfg = "{\"jks\":\"keystore.jks\",\"storepass\":\"password\",\"alias\":\"abc\",\"keypass\":\"password\",\"use\":[\"sig\", \"enc\"]}";
        List<String> usages = ConfigurationCertUtils.extractUsageFromCertConfiguration(cfg);
        Assertions.assertTrue(usages.containsAll(List.of("sig", "enc")));
    }

    @Test
    public void shouldReturnEmptyListIfConfigurationIsEmpty() {
        Assertions.assertTrue(ConfigurationCertUtils.extractUsageFromCertConfiguration("").isEmpty());
    }

    @Test
    public void shouldReturnEmptyListIfConfigurationIsNull() {
        Assertions.assertTrue(ConfigurationCertUtils.extractUsageFromCertConfiguration(null).isEmpty());
    }

    @Test
    public void shouldReturnEmptyListIfConfigurationIsNotJson() {
        Assertions.assertTrue(ConfigurationCertUtils.extractUsageFromCertConfiguration("xzcxz").isEmpty());
    }

    @Test
    public void shouldExtractListWithSingleUsa() {
        String cfg = "{\"jks\":\"keystore.jks\",\"storepass\":\"password\",\"alias\":\"abc\",\"keypass\":\"password\",\"use\":[\"sig\"]}";
        List<String> usages = ConfigurationCertUtils.extractUsageFromCertConfiguration(cfg);
        Assertions.assertTrue(usages.contains("sig"));
    }

    @Test
    public void whenUseNotPresentReturnEmptyList() {
        String cfg = "{\"jks\":\"keystore.jks\",\"storepass\":\"password\",\"alias\":\"abc\",\"keypass\":\"password\"}";
        List<String> usages = ConfigurationCertUtils.extractUsageFromCertConfiguration(cfg);
        Assertions.assertTrue(usages.isEmpty());
    }

    @Test
    public void shouldReturnTrueIfUsageContainsSpecificUse() {
        String cfg = "{\"jks\":\"keystore.jks\",\"storepass\":\"password\",\"alias\":\"abc\",\"keypass\":\"password\",\"use\":[\"sig\", \"enc\"]}";
        Assertions.assertTrue(ConfigurationCertUtils.usageContains(cfg, "sig"));
        Assertions.assertTrue(ConfigurationCertUtils.usageContains(cfg, "enc"));
    }

    @Test
    public void shouldReturnFalseIfUsageDoesntContainSpecificUse() {
        String cfg = "{\"jks\":\"keystore.jks\",\"storepass\":\"password\",\"alias\":\"abc\",\"keypass\":\"password\",\"use\":[\"sig\", \"enc\"]}";
        Assertions.assertFalse(ConfigurationCertUtils.usageContains(cfg, "xxxxx"));
    }
}