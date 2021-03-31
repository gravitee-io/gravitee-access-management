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

import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorChannel;
import io.gravitee.am.model.scim.Attribute;
import org.junit.Assume;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserFactorUpdaterTest {

    @Test
    public void shouldNotUpdateFactors_NoChannel () {
        EnrolledFactor factor = new EnrolledFactor();
        Assume.assumeTrue(factor.getChannel() == null);

        User existingUser = new User();
        existingUser.setEmail("email@domain.org");

        User updatedUser = new User();
        updatedUser.setEmail("email2@domain.org");

        UserFactorUpdater.updateFactors(singletonList(factor), existingUser, updatedUser);

        assertNull(factor.getChannel());
    }

    @Test
    public void shouldUpdateEmail() {
        User existingUser = new User();
        existingUser.setEmail("email@domain.org");

        EnrolledFactor factor = new EnrolledFactor();
        factor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, existingUser.getEmail()));

        User updatedUser = new User();
        updatedUser.setEmail("email2@domain.org");

        UserFactorUpdater.updateFactors(singletonList(factor), existingUser, updatedUser);

        assertEquals("Email should be updated", updatedUser.getEmail(), factor.getChannel().getTarget());
    }

    @Test
    public void shouldNotUpdateEmail_EmailRemoved() {
        User existingUser = new User();
        existingUser.setEmail("email@domain.org");

        EnrolledFactor factor = new EnrolledFactor();
        factor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, existingUser.getEmail()));

        User updatedUser = new User();
        updatedUser.setEmail(null);

        UserFactorUpdater.updateFactors(singletonList(factor), existingUser, updatedUser);

        assertEquals("Email should not be updated", existingUser.getEmail(), factor.getChannel().getTarget());
    }

    @Test
    public void shouldUpdateEmail_WithEmailAttributes() {
        User existingUser = new User();
        existingUser.setEmail("email@domain.org");
        Attribute email1 = new Attribute();
        email1.setValue(existingUser.getEmail());
        Attribute email2 = new Attribute();
        email2.setValue("email2@domain.org");
        existingUser.setEmails(Arrays.asList(email1, email2));

        EnrolledFactor factor = new EnrolledFactor();
        factor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, email2.getValue()));

        User updatedUser = new User();
        updatedUser.setEmail(null);
        Attribute uemail1 = new Attribute();
        uemail1.setValue(email1.getValue()); // value is the same
        Attribute uemail2 = new Attribute();
        uemail2.setValue("uemail2@domain.org");
        updatedUser.setEmails(Arrays.asList(uemail1, uemail2));

        UserFactorUpdater.updateFactors(singletonList(factor), existingUser, updatedUser);

        assertEquals("Email should be updated", uemail2.getValue(), factor.getChannel().getTarget());
    }

    @Test
    public void shouldNotUpdateEmail_NoChange() {
        User existingUser = new User();
        existingUser.setEmail("email@domain.org");
        Attribute email1 = new Attribute();
        email1.setValue(existingUser.getEmail());
        Attribute email2 = new Attribute();
        email2.setValue("email2@domain.org");
        Attribute email3 = new Attribute();
        email3.setValue("email3@domain.org");
        existingUser.setEmails(Arrays.asList(email1, email2, email3));

        EnrolledFactor factor = new EnrolledFactor();
        factor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, email3.getValue()));

        User updatedUser = new User();
        updatedUser.setEmail(null);
        Attribute uemail1 = new Attribute();
        uemail1.setValue(email1.getValue()); // value is the same
        Attribute uemail2 = new Attribute();
        uemail2.setValue("uemail2@domain.org");
        updatedUser.setEmails(Arrays.asList(uemail1, uemail2));

        UserFactorUpdater.updateFactors(singletonList(factor), existingUser, updatedUser);

        assertEquals("Email should not be updated", email3.getValue(), factor.getChannel().getTarget());
    }


    @Test
    public void shouldNotUpdateEmail_TooMuchDiff() {
        User existingUser = new User();
        existingUser.setEmail("email@domain.org");
        Attribute email1 = new Attribute();
        email1.setValue(existingUser.getEmail());
        Attribute email2 = new Attribute();
        email2.setValue("email2@domain.org");
        Attribute email3 = new Attribute();
        email3.setValue("email3@domain.org");
        existingUser.setEmails(Arrays.asList(email1, email2, email3));

        EnrolledFactor factor = new EnrolledFactor();
        factor.setChannel(new EnrolledFactorChannel(EnrolledFactorChannel.Type.EMAIL, email3.getValue()));

        User updatedUser = new User();
        updatedUser.setEmail(null);
        Attribute uemail1 = new Attribute();
        uemail1.setValue(email1.getValue()); // value is the same
        Attribute uemail2 = new Attribute();
        uemail2.setValue("uemail2@domain.org");
        Attribute uemail3 = new Attribute();
        uemail3.setValue("uemail3@domain.org");
        updatedUser.setEmails(Arrays.asList(uemail1, uemail2, uemail3));

        UserFactorUpdater.updateFactors(singletonList(factor), existingUser, updatedUser);

        assertEquals("Email should not be updated", email3.getValue(), factor.getChannel().getTarget());
    }

}
