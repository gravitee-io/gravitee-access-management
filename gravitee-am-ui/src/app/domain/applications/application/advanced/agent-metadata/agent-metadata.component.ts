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
import { AgentCard, SecurityScheme } from '@a2a-js/sdk';

import { ApplicationService } from '../../../../../services/application.service';

@Component({
  selector: 'app-application-agent-metadata',
  templateUrl: './agent-metadata.component.html',
  styleUrls: ['./agent-metadata.component.scss'],
  standalone: false,
})
export class AgentMetadataComponent implements OnInit {
  domainId: string;
  application: any;
  agentCardUrl: string | null = null;
  loading = false;
  loadError: string | null = null;
  agentCard: AgentCard | null = null;
  rawJson: string | null = null;

  get hasCapabilities(): boolean {
    const c = this.agentCard?.capabilities;
    return !!(c?.streaming || c?.pushNotifications || c?.stateTransitionHistory || c?.extensions?.length);
  }

  get hasSecurity(): boolean {
    const s = this.agentCard?.securitySchemes;
    return !!s && Object.keys(s).length > 0;
  }

  get hasSkills(): boolean {
    return !!(this.agentCard?.skills.length || this.agentCard?.defaultInputModes.length || this.agentCard?.defaultOutputModes.length);
  }

  securitySchemeEntries(): Array<{ name: string; scheme: SecurityScheme }> {
    if (!this.agentCard?.securitySchemes) return [];
    return Object.entries(this.agentCard.securitySchemes).map(([name, scheme]) => ({ name, scheme }));
  }

  oauthFlowNames(scheme: SecurityScheme): string[] {
    if (scheme.type !== 'oauth2') return [];
    return Object.keys(scheme.flows);
  }

  isUrl(value: unknown): boolean {
    return typeof value === 'string' && (value.startsWith('https://') || value.startsWith('http://'));
  }

  constructor(
    private route: ActivatedRoute,
    private applicationService: ApplicationService,
  ) {}

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = this.route.snapshot.data['application'];
    this.agentCardUrl = this.application?.settings?.advanced?.agentCardUrl ?? null;
    if (this.agentCardUrl) {
      this.loadAgentCard();
    }
  }

  loadAgentCard(): void {
    this.loading = true;
    this.loadError = null;
    this.applicationService.getAgentCard(this.domainId, this.application.id).subscribe({
      next: (card) => {
        this.agentCard = card;
        this.rawJson = JSON.stringify(card, null, 2);
        this.loading = false;
      },
      error: () => {
        this.loadError = 'Failed to load agent card. The URL may be unreachable or return invalid data.';
        this.loading = false;
      },
    });
  }
}
