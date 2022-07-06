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
package io.gravitee.am.management.service.alerts;

import io.gravitee.alert.api.trigger.Trigger;
import io.gravitee.am.model.ReferenceType;
import io.gravitee.am.model.alert.AlertNotifier;
import io.gravitee.am.model.alert.AlertTrigger;
import io.gravitee.am.model.alert.AlertTriggerType;
import java.util.List;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AlertTriggerFactoryTest {

    @Test
    public void createTooManyLoginFailuresTrigger() {

        final AlertTrigger alertTrigger = new AlertTrigger();

        alertTrigger.setId("alertTrigger#1");
        alertTrigger.setEnabled(true);
        alertTrigger.setType(AlertTriggerType.TOO_MANY_LOGIN_FAILURES);
        alertTrigger.setReferenceType(ReferenceType.DOMAIN);
        alertTrigger.setReferenceId("domain#1");

        final AlertNotifier alertNotifier = new AlertNotifier();
        alertNotifier.setId("alertNotifier#1");
        alertNotifier.setName("test");
        alertNotifier.setConfiguration("{}");
        alertNotifier.setType("webhook");

        final MockEnvironment environment = new MockEnvironment();
        final List<Trigger> triggers = AlertTriggerFactory.create(alertTrigger, Collections.singletonList(alertNotifier), environment);

        assertEquals(1, triggers.size());
        var trigger = triggers.get(0);

        assertEquals(alertTrigger.getId(), trigger.getId());
        assertEquals(1, trigger.getConditions().size());
        assertEquals(1, trigger.getFilters().size());
        assertTrue(trigger.isEnabled());
    }

    @Test
    public void createRiskAssessmentAlert() {

        final AlertTrigger alertTrigger = new AlertTrigger();

        alertTrigger.setId("alertTrigger#1");
        alertTrigger.setEnabled(true);
        alertTrigger.setType(AlertTriggerType.RISK_ASSESSMENT);
        alertTrigger.setReferenceType(ReferenceType.DOMAIN);
        alertTrigger.setReferenceId("domain#1");

        final AlertNotifier alertNotifier = new AlertNotifier();
        alertNotifier.setId("alertNotifier#1");
        alertNotifier.setName("test");
        alertNotifier.setConfiguration("{}");
        alertNotifier.setType("webhook");

        final MockEnvironment environment = new MockEnvironment();
        final List<Trigger> triggers = AlertTriggerFactory.create(alertTrigger, Collections.singletonList(alertNotifier), environment);

        assertEquals(3, triggers.size());
        triggers.forEach(trigger -> {
            assertTrue(trigger.getId().startsWith(alertTrigger.getId()));
            assertEquals(1, trigger.getConditions().size());
            assertEquals(1, trigger.getFilters().size());
            assertTrue(trigger.isEnabled());
        });
    }
}