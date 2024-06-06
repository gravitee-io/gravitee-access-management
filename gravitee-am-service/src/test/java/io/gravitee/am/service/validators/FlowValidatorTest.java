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
package io.gravitee.am.service.validators;

import io.gravitee.am.model.flow.Step;
import io.gravitee.am.service.exception.InvalidParameterException;
import io.gravitee.am.service.model.Flow;
import io.gravitee.am.service.validators.flow.FlowValidatorImpl;
import io.gravitee.am.service.validators.flow.policy.SendEmailPolicyValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.mockito.AdditionalMatchers.not;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FlowValidatorTest {

    private static final InvalidParameterException INVALID_PARAMETER_EXCEPTION = new InvalidParameterException("Invalid parameter");
    @Mock
    private SendEmailPolicyValidator emailPolicyValidator;

    @InjectMocks
    private FlowValidatorImpl flowValidator;

    @Test
    void must_invalidate_none() {
        var step1 = getStep("step1", "{\"policy\":\"policy1\"}");
        var step2 = getStep("step2", "{\"policy\":\"policy2\"}");
        var flow = new Flow();
        flow.setName("testFlow");
        flow.setPre(List.of(step2));
        flow.setPost(List.of(step1));

        when(emailPolicyValidator.validate(any())).thenReturn(Optional.empty());

        var observer = flowValidator.validate(flow).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertComplete().assertNoErrors();

        verify(emailPolicyValidator, times(2)).validate(any());
    }

    @ParameterizedTest
    @MethodSource("params_that_must_invalidate_step")
    void must_invalidate_step(String invalidStepName, int expectedInvocations) {
        var step1 = getStep("step1", "{\"policy\":\"policy1\"}");
        var step2 = getStep("step2", "{\"policy\":\"policy2\"}");
        var steps = Map.of(
                "step1", step1,
                "step2", step2
        );
        var flow = new Flow();
        flow.setName("testFlow");
        flow.setPre(List.of(step1));
        flow.setPost(List.of(step2));

        final var invalidStep = steps.get(invalidStepName);
        lenient().when(emailPolicyValidator.validate(not(eq(invalidStep)))).thenReturn(Optional.empty());
        lenient().when(emailPolicyValidator.validate(eq(invalidStep))).thenReturn(Optional.of(INVALID_PARAMETER_EXCEPTION));

        var observer = flowValidator.validate(flow).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidParameterException.class);

        verify(emailPolicyValidator, times(expectedInvocations)).validate(any());
    }

    @ParameterizedTest
    @MethodSource("params_that_must_invalidate_step")
    void must_invalidate_step_with_flow_list(String invalidStepName, int expectedInvocations) {
        var step1 = getStep("step1", "{\"policy\":\"policy1\"}");
        var step2 = getStep("step2", "{\"policy\":\"policy2\"}");
        var steps = Map.of(
                "step1", step1,
                "step2", step2
        );
        var flow = new Flow();
        flow.setName("testFlow");
        flow.setPre(List.of(step1));
        flow.setPost(List.of(step2));

        final var invalidStep = steps.get(invalidStepName);
        lenient().when(emailPolicyValidator.validate(not(eq(invalidStep)))).thenReturn(Optional.empty());
        lenient().when(emailPolicyValidator.validate(eq(invalidStep))).thenReturn(Optional.of(INVALID_PARAMETER_EXCEPTION));

        var observer = flowValidator.validateAll(List.of(flow)).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidParameterException.class);

        verify(emailPolicyValidator, times(expectedInvocations)).validate(any());
    }

    public static Stream<Arguments> params_that_must_invalidate_step() {
        return Stream.of(
                Arguments.of("step1", 1),
                Arguments.of("step2", 2)
        );
    }

    @Test
    void must_invalidate_all() {
        var step1 = getStep("step1", "{\"policy\":\"policy1\"}");
        var step2 = getStep("step2", "{\"policy\":\"policy2\"}");
        var flow = new Flow();
        flow.setName("testFlow");
        flow.setPre(List.of(step2));
        flow.setPost(List.of(step1));

        when(emailPolicyValidator.validate(any())).thenReturn(Optional.of(INVALID_PARAMETER_EXCEPTION));

        var observer = flowValidator.validate(flow).test();
        observer.awaitDone(10, TimeUnit.SECONDS);
        observer.assertError(InvalidParameterException.class);

        verify(emailPolicyValidator, times(1)).validate(any());
    }

    private static Step getStep(String policy, String configuration) {
        var step = new Step();
        step.setName(policy);
        step.setPolicy(policy);
        step.setConfiguration(configuration);
        return step;
    }
}
