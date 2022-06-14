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
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserFactorUpdater {

    public static void updateFactors(List<EnrolledFactor> factors, User existingUser, User updatedUser) {
        if (factors != null) {
            factors.stream()
                    .filter(factor -> factor.getChannel() != null)
                    .forEach(factor -> {
                        // Do not manage the SMS channel type because we can't know if it is a mobile phone number...
                        // in addition, we have to know the country related to the phone number that is an information provided by the user
                        // during the factor enrollment
                        EnrolledFactorChannel.Type channelType = factor.getChannel().getType();
                        if (EnrolledFactorChannel.Type.EMAIL.equals(channelType)) {
                            String emailFactor = factor.getChannel().getTarget();

                            // if new email is null or empty, do not update the factor
                            if (emailFactor.equals(existingUser.getEmail()) &&
                                    !existingUser.getEmail().equals(updatedUser.getEmail()) &&
                                    !StringUtils.isEmpty(updatedUser.getEmail())) {

                                factor.getChannel().setTarget(updatedUser.getEmail());

                            } else if (existingUser.getEmails() != null && updatedUser.getEmails() != null) {
                                List<String> existingEmails = existingUser.getEmails().stream().map(Attribute::getValue).collect(Collectors.toList());
                                List<String> updatedEmails = updatedUser.getEmails().stream().map(Attribute::getValue).collect(Collectors.toList());
                                List<String> existingRemaining = new ArrayList<>(existingEmails);
                                List<String> updatedRemaining = new ArrayList<>(updatedEmails);
                                updatedEmails.forEach(existingRemaining::remove);
                                existingEmails.forEach(updatedRemaining::remove);

                                if (existingRemaining.size() == 1 &&
                                        updatedRemaining.size() == 1 &&
                                        emailFactor.equals(existingRemaining.get(0)) &&
                                        !StringUtils.isEmpty(updatedRemaining.get(0))) {
                                    factor.getChannel().setTarget(updatedRemaining.get(0));
                                } // If there are more than one result, do not update the factor
                            }
                        }
                    });
        }
    }
}
