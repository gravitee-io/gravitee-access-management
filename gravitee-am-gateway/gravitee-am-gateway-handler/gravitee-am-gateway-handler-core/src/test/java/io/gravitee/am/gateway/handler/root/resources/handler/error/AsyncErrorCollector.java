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
package io.gravitee.am.gateway.handler.root.resources.handler.error;

import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AsyncErrorCollector implements TestRule {
    @Override
    public Statement apply(Statement statement, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                final var errors = startCollectErrors();
                statement.evaluate();
                if (!errors.isEmpty()) {
                    throw new AsyncErrors(errors);
                }
            }
        };
    }

    private List<Throwable> startCollectErrors() {
        final var result = Collections.synchronizedList(new ArrayList<Throwable>());
        RxJavaPlugins.setErrorHandler(result::add);
        return result;
    }

    static class AsyncErrors extends Throwable {
        public AsyncErrors(List<Throwable> errors) {
            super(errors.stream().map(Throwable::getMessage).toList().toString(), errors.isEmpty() ? null : errors.get(0));
        }
    }
}
