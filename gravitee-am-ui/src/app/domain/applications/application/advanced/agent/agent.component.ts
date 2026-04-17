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

import { SnackbarService } from '../../../../../services/snackbar.service';
import { ApplicationService } from '../../../../../services/application.service';
import { AuthService } from '../../../../../services/auth.service';

interface AgentFormState {
  agentType: string;
  allowedGrantTypes: string[];
  tokenTtlSeconds: number | null;
  refreshTokenEnabled: boolean;
  maxPublicKeysPerWorkload: number | null;
}

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
  readonly grantTypeOptions = [
    'authorization_code',
    'refresh_token',
    'client_credentials',
    'urn:ietf:params:oauth:grant-type:token-exchange',
  ];

  application: any;
  domainId: string;
  editMode = false;
  agentTypeLabel = '';
  form: AgentFormState = {
    agentType: '',
    allowedGrantTypes: [],
    tokenTtlSeconds: null,
    refreshTokenEnabled: false,
    maxPublicKeysPerWorkload: null,
  };
  dirty = false;

  constructor(
    private route: ActivatedRoute,
    private snackbarService: SnackbarService,
    private applicationService: ApplicationService,
    private authService: AuthService,
  ) {}

  ngOnInit(): void {
    this.application = this.route.snapshot.data['application'];
    this.domainId = this.route.snapshot.data['domain']?.id ?? this.application.domain;
    this.editMode = this.authService.hasPermissions(['application_settings_update']);

    const agent = this.application.settings?.agent ?? {};
    this.form = {
      agentType: agent.agentType ?? '',
      allowedGrantTypes: agent.allowedGrantTypes ?? [],
      tokenTtlSeconds: agent.tokenTtlSeconds ?? null,
      refreshTokenEnabled: !!agent.refreshTokenEnabled,
      maxPublicKeysPerWorkload: agent.maxPublicKeysPerWorkload ?? null,
    };
    this.agentTypeLabel = AGENT_TYPE_LABELS[this.form.agentType] ?? this.form.agentType;
  }

  markDirty(): void {
    this.dirty = true;
  }

  save(): void {
    const patch = {
      settings: {
        agent: {
          agentType: this.form.agentType || null,
          allowedGrantTypes: this.form.allowedGrantTypes,
          tokenTtlSeconds: this.form.tokenTtlSeconds,
          refreshTokenEnabled: this.form.refreshTokenEnabled,
          maxPublicKeysPerWorkload: this.form.maxPublicKeysPerWorkload,
        },
      },
    };
    this.applicationService.patch(this.domainId, this.application.id, patch).subscribe({
      next: (updated) => {
        this.application = updated;
        this.route.snapshot.data['application'] = updated;
        this.dirty = false;
        this.snackbarService.open('Agent settings updated');
      },
      error: (err: unknown) => {
        const maybe = err as { error?: { message?: string } } | undefined;
        this.snackbarService.open(maybe?.error?.message ?? 'Failed to update agent settings');
      },
    });
  }
}
