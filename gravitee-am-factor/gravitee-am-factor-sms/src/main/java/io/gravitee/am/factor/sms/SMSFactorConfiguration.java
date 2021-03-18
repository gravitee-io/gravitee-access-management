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
package io.gravitee.am.factor.sms;

import io.gravitee.am.factor.api.FactorConfiguration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SMSFactorConfiguration implements FactorConfiguration {

    private String countryCodes;

    private String graviteeResource;

    public String getCountryCodes() {
        return countryCodes;
    }

    public void setCountryCodes(String countryCodes) {
        this.countryCodes = countryCodes;
    }

    public String getGraviteeResource() {
        return graviteeResource;
    }

    public void setGraviteeResource(String graviteeResource) {
        this.graviteeResource = graviteeResource;
    }

    public List<String> countries() {
        if (countryCodes == null) {
            return Collections.emptyList();
        } else {
            return Stream.of(countryCodes.toLowerCase(Locale.ROOT).split(","))
                    .map(String::trim)
                    .collect(Collectors.toList());
        }
    }
}
