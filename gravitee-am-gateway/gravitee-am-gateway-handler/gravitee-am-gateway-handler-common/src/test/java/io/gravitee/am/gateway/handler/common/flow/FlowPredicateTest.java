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
package io.gravitee.am.gateway.handler.common.flow;

import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.ExecutionContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class FlowPredicateTest {
    @Mock
    private ExecutionContext ctx = mock(ExecutionContext.class);

    @Before
    public void before() {
        when(ctx.getTemplateEngine()).thenReturn(TemplateEngine.templateEngine());
    }

    @Test
    public void shouldAlwaysReturnTrue_condition_null() {
        ExecutionPredicate predicate = ExecutionPredicate.alwaysTrue();
        assertTrue(predicate.evaluate(null));
    }

    @Test
    public void shouldAlwaysReturnTrue_condition_empty() {
        ExecutionPredicate predicate = ExecutionPredicate.alwaysTrue();
        assertTrue(predicate.evaluate(""));
    }

    @Test
    public void shouldAlwaysReturnTrue_condition_true() {
        ExecutionPredicate predicate = ExecutionPredicate.alwaysTrue();
        assertTrue(predicate.evaluate("true"));
    }

    @Test
    public void shouldAlwaysReturnTrue_condition_false() {
        ExecutionPredicate predicate = ExecutionPredicate.alwaysTrue();
        assertTrue(predicate.evaluate("false"));
    }

    @Test
    public void shouldReturnTrue_condition_null() {
        ExecutionPredicate predicate = ExecutionPredicate.from(ctx);
        assertTrue(predicate.evaluate(null));
    }

    @Test
    public void shouldReturnTrue_condition_empty() {
        ExecutionPredicate predicate = ExecutionPredicate.from(ctx);
        assertTrue(predicate.evaluate(""));
    }

    @Test
    public void shouldReturnTrue_condition_true() {
        ExecutionPredicate predicate = ExecutionPredicate.from(ctx);
        assertTrue(predicate.evaluate("true"));
    }

    @Test
    public void shouldReturnFalse_condition_false() {
        ExecutionPredicate predicate = ExecutionPredicate.from(ctx);
        assertFalse(predicate.evaluate("false"));
    }

    @Test
    public void shouldReturnFalse_ExpressionException() {
        ExecutionPredicate predicate = ExecutionPredicate.from(ctx);
        assertFalse(predicate.evaluate("{#invalid}"));
    }
}
