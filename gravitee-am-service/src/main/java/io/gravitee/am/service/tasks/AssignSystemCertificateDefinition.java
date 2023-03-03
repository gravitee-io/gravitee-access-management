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
package io.gravitee.am.service.tasks;

import java.util.concurrent.TimeUnit;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AssignSystemCertificateDefinition implements TaskDefinition {
    private String domainId;

    private String renewedCertificate;

    private String deprecatedCertificate;

    private long delay;

    private TimeUnit unit;

    public AssignSystemCertificateDefinition() {
    }

    public AssignSystemCertificateDefinition(String domainId, String renewedCertificate, String deprecatedCertificate) {
        this.domainId = domainId;
        this.renewedCertificate = renewedCertificate;
        this.deprecatedCertificate = deprecatedCertificate;
    }

    public String getDomainId() {
        return domainId;
    }

    public void setDomainId(String domainId) {
        this.domainId = domainId;
    }

    public String getRenewedCertificate() {
        return renewedCertificate;
    }

    public void setRenewedCertificate(String renewedCertificate) {
        this.renewedCertificate = renewedCertificate;
    }

    public String getDeprecatedCertificate() {
        return deprecatedCertificate;
    }

    public void setDeprecatedCertificate(String deprecatedCertificate) {
        this.deprecatedCertificate = deprecatedCertificate;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public TimeUnit getUnit() {
        return unit;
    }

    public void setUnit(TimeUnit unit) {
        this.unit = unit;
    }

    @Override
    public String toString() {
        return "AssignSystemCertificateDefinition{" +
                "domainId='" + domainId + '\'' +
                ", renewedCertificate='" + renewedCertificate + '\'' +
                ", deprecatedCertificate='" + deprecatedCertificate + '\'' +
                ", delay=" + delay +
                ", unit=" + unit +
                '}';
    }
}
