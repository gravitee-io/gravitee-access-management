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
package io.gravitee.am.management.service.impl.notifications.notifiers;

import org.junit.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LoggerNotifierTest {

    @Test
    public void logger_notifier_test(){
        LoggerNotifier notifier = new LoggerNotifier();
        Map<String, Object> map = Mockito.spy(Map.of("msg", "test"));
        notifier.send(Mockito.mock(), map);

        Mockito.verify(map, Mockito.times(1)).get(Mockito.eq("msg"));
    }
}