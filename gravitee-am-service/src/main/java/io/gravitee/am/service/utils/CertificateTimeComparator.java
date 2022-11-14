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
package io.gravitee.am.service.utils;

import io.gravitee.am.model.Certificate;

import java.util.Comparator;

/**
 * Always take the most recent certificate but if 2 certs have the same creation date
 * we take the one with the highest expiration date
 *
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CertificateTimeComparator implements Comparator<Certificate> {

    @Override
    public int compare(Certificate cert1, Certificate cert2) {
        if (cert1.getCreatedAt().getTime() == cert2.getCreatedAt().getTime()) {
            if (cert1.getExpiresAt() != null && cert2.getExpiresAt() != null) {
                return cert1.getExpiresAt().getTime() < cert2.getExpiresAt().getTime() ? 1 : -1;
            } else {
                return 0;
            }
        } else {
            return cert1.getCreatedAt().getTime() < cert2.getCreatedAt().getTime() ? 1 : -1;
        }
    }
}
