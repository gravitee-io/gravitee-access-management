/*
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
import { Component, Input } from '@angular/core';

@Component({
  selector: 'agent-creation-step1',
  templateUrl: './step1.component.html',
  styleUrls: ['./step1.component.scss'],
  standalone: false,
})
export class AgentCreationStep1Component {
  @Input() application;

  agentTypes: any[] = [
    {
      name: 'User-Embedded',
      icon: 'person',
      type: 'USER_EMBEDDED',
      description: "Runs in the user's context",
      subDescription: 'Browser extension, desktop assistant — acts on behalf of a logged-in user via PKCE',
    },
    {
      name: 'Hosted Delegated',
      icon: 'cloud',
      type: 'HOSTED_DELEGATED',
      description: 'Server-side with user delegation',
      subDescription: 'Backend agent that receives delegated authority via token exchange (RFC 8693)',
    },
    {
      name: 'Autonomous',
      icon: 'memory',
      type: 'AUTONOMOUS',
      description: 'Fully independent, no user',
      subDescription: 'Machine-to-machine agent using client credentials + token exchange',
    },
  ];

  selectAgentType(selectedAgentType: string): void {
    this.application.agentType = selectedAgentType;
  }
}
