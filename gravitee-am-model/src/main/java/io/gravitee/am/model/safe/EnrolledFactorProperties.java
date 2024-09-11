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

package io.gravitee.am.model.safe;


import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.FactorStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class EnrolledFactorProperties {
    private String factorId;

    private String appId;

    private FactorStatus status = FactorStatus.NULL;

    private EnrolledFactorChannelProperties channel;

    private Boolean primary;

    public EnrolledFactorProperties(EnrolledFactor factor) {
        this.factorId = factor.getFactorId();
        this.appId = factor.getAppId();
        this.status = factor.getStatus();
        this.primary = factor.isPrimary();
        this.channel = Optional.ofNullable(factor.getChannel()).map(EnrolledFactorChannelProperties::new).orElse(null);
    }

}
