/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.service.reporter.builder;

import io.gravitee.am.common.audit.EntityType;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.model.Factor;
import io.gravitee.am.model.Reference;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.reporter.builder.gateway.GatewayAuditBuilder;

import static io.gravitee.am.common.audit.EntityType.MFA_FACTOR;

public class MFAAuditBuilder extends GatewayAuditBuilder<MFAAuditBuilder> {

    private record ChanelData(String type, String target) {
    }

    public MFAAuditBuilder factor(Factor factor) {
        setTarget(factor.getId(), MFA_FACTOR, null, factor.getName(), null, null);
        return this;
    }

    public MFAAuditBuilder channel(EnrolledFactorChannel channel) {
        if (channel != null) {
            setNewValue(new ChanelData(channel.getType().toString(), channel.getTarget()));
        }
        return this;
    }

    public MFAAuditBuilder application(Client client) {
        if (client != null && client.getId() != null) {
            reference(Reference.domain(client.getDomain()));
            setTarget(client.getId(), EntityType.APPLICATION, null, client.getClientName(), ReferenceType.DOMAIN, client.getDomain());
        }
        return this;
    }

    public MFAAuditBuilder throwable(Throwable cause, EnrolledFactorChannel channel) {
        if (cause == null) {
            return this;
        }

        if (channel == null) {
            return super.throwable(cause);
        }

        final String target = channel.getTarget();
        final String factoryType = channel.getType().toString();
        final Throwable throwable = new Throwable(cause.getMessage() +
                ".\nFactor: " + factoryType.substring(0, 1).toUpperCase() + factoryType.substring(1)
                + "\nTarget: " + target);

        super.throwable(throwable);
        return this;
    }

    public MFAAuditBuilder user(User user) {
        if (user != null) {
            setActor(user.getId(), EntityType.USER, user.getUsername(), user.getDisplayName(), user.getReferenceType(), user.getReferenceId(), user.getExternalId(), user.getSource());
            if (user.getAdditionalInformation() != null) {
                if (user.getAdditionalInformation().containsKey(Claims.IP_ADDRESS)) {
                    ipAddress((String) user.getAdditionalInformation().get(Claims.IP_ADDRESS));
                }
                if (user.getAdditionalInformation().containsKey(Claims.USER_AGENT)) {
                    userAgent((String) user.getAdditionalInformation().get(Claims.USER_AGENT));
                }
            }
        }
        return this;
    }

    public MFAAuditBuilder domainFrom(Client client) {
        if (client != null) {
            reference(Reference.domain(client.getDomain()));
        }
        return this;
    }
}
