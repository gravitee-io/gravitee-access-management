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
package io.gravitee.am.resource.api.mfa;

import io.gravitee.am.factor.api.FactorContext;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MFALink {
    private final MFAType channel;
    private final String target;
    private final FactorContext factorContext;

    public MFALink(MFAType channel, String target, FactorContext factorContext) {
        this.channel = channel;
        this.target = target;
        this.factorContext = factorContext;
    }

    public MFAType getChannel() {
        return channel;
    }

    public String getTarget() {
        return target;
    }

    public FactorContext getFactorContext() {
        return factorContext;
    }
}
