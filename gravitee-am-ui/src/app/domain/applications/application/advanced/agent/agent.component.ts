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
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

const AGENT_TYPE_LABELS: Record<string, string> = {
  user_embedded: 'User-embedded',
  autonomous: 'Autonomous',
  delegated: 'Delegated',
};

@Component({
  selector: 'app-application-agent',
  templateUrl: './agent.component.html',
  styleUrls: ['./agent.component.scss'],
  standalone: false,
})
export class ApplicationAgentComponent implements OnInit {
  application: any;
  agentType = '';
  agentTypeLabel = '';

  constructor(private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.application = this.route.snapshot.data['application'];
    this.agentType = this.application.settings?.agent?.agentType ?? '';
    this.agentTypeLabel = AGENT_TYPE_LABELS[this.agentType] ?? this.agentType;
  }
}
