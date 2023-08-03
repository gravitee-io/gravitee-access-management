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
package io.gravitee.am.gateway.handler.common.utils;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Tuple<T1, T2> {
    private final T1 reporters;
    private final T2 context;

    Tuple(T1 reporters, T2 context) {
        this.reporters = reporters;
        this.context = context;
    }

    public T1 getT1() {
        return reporters;
    }

    public T2 getT2() {
        return context;
    }

    public static <T1, T2> Tuple<T1, T2> of(T1 reporters, T2 context) {
        return new Tuple(reporters, context);
    }
}