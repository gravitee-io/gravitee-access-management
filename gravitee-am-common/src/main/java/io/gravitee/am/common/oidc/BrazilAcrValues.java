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
package io.gravitee.am.common.oidc;

import java.util.Arrays;
import java.util.List;

/**
 *  Authentication Context Class References for OpenBanking Brazil
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface BrazilAcrValues {

    /**
     * https://openbanking-brasil.github.io/specs-seguranca/open-banking-brasil-financial-api-1_ID2.html
     * LoA2: Authentication performed using single factor;
     */
    String LoA2 = "urn:brasil:openbanking:loa2";

    /**
     * https://openbanking-brasil.github.io/specs-seguranca/open-banking-brasil-financial-api-1_ID2.html
     * LoA3: Authentication performed using multi factor (MFA)
     */
    String LoA3 = "urn:brasil:openbanking:loa3";

    static List<String> values() {
        return Arrays.asList(LoA2, LoA3);
    }
}
