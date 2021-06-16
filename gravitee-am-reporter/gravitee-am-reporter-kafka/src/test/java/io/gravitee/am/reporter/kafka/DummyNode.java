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
package io.gravitee.am.reporter.kafka;

import io.gravitee.common.component.Lifecycle.State;
import io.gravitee.node.api.Node;

public class DummyNode implements Node {

  private final String id;
  private final String hostname;

  public DummyNode(String id, String hostname) {
    this.id = id;
    this.hostname = hostname;
  }

  @Override
  public String id() {
    return this.id;
  }

  @Override
  public String hostname() {
    return this.hostname;
  }

  @Override
  public String name() {
    return null;
  }

  @Override
  public String application() {
    return null;
  }

  @Override
  public State lifecycleState() {
    return null;
  }

  @Override
  public Node start() throws Exception {
    return null;
  }

  @Override
  public Node stop() throws Exception {
    return null;
  }
}
