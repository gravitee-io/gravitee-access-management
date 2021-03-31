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
package io.gravitee.am.factor.api;

import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Enrollment {

    private String key;
    private String barCode;
    private List<String> countries;

    public Enrollment() { }

    public Enrollment(List<String> countries) {
        this.countries = countries;
    }

    public Enrollment(String key, String barCode) {
        this.key = key;
        this.barCode = barCode;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getBarCode() {
        return barCode;
    }

    public void setBarCode(String barCode) {
        this.barCode = barCode;
    }

    public List<String> getCountries() {
        return countries == null ? emptyList() : countries;
    }

    public void setCountries(List<String> countries) {
        this.countries = countries;
    }
}
