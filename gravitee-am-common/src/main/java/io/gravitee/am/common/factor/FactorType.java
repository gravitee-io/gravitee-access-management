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
package io.gravitee.am.common.factor;

import java.util.NoSuchElementException;

/**
 * @author Donald Courtney (donald.courtney at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum FactorType {
    OTP("TOTP"),
    SMS("SMS"),
    EMAIL("EMAIL"),
    CALL("CALL");

    FactorType(String type) {
        this.type = type;
    }
    private final String type;

    public static FactorType getFactorTypeFromString(String type){
        if (OTP.getType().equalsIgnoreCase(type) || "OTP".equalsIgnoreCase(type)) return OTP;
        if (SMS.getType().equalsIgnoreCase(type)) return SMS;
        if (EMAIL.getType().equalsIgnoreCase(type)) return EMAIL;
        if (CALL.getType().equalsIgnoreCase(type)) return CALL;
        throw new NoSuchElementException(String.format("No factor type for provided string of %s", type));
    }

    public String getType() {
        return type;
    }
}
