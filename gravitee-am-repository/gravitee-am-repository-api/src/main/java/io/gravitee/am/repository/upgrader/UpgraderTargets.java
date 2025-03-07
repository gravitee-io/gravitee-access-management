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
package io.gravitee.am.repository.upgrader;

import lombok.experimental.UtilityClass;

@UtilityClass
public class UpgraderTargets {
    public final static String GATEWAY_UPGRADER_TARGET = "dp_upgraders";
    public final static String MANAGEMENT_UPGRADER_TARGET = "upgraders";
    public final static String DATAPLANE_UPGRADER_TARGET = GATEWAY_UPGRADER_TARGET;

}
