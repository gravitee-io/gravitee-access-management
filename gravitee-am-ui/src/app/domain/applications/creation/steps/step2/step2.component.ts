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
import { Component, Input, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { find } from 'lodash';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { DomainStoreService } from '../../../../../stores/domain.store';

@Component({
  selector: 'application-creation-step2',
  templateUrl: './step2.component.html',
  styleUrls: ['./step2.component.scss'],
  standalone: false,
})
export class ApplicationCreationStep2Component implements OnInit, OnDestroy {
  @Input() application: any;
  @ViewChild('appForm') form: any;
  domain: any;
  private destroy$ = new Subject<void>();
  creationModes = [
    {
      value: 'manual',
      label: 'Manual',
      description: 'Add manual information',
    },
    {
      value: 'cimd',
      label: 'CIMD',
      description: 'Bootstrap from a Client Identity Metadata Document URL',
    },
  ];
  applicationTypes: any[] = [
    {
      icon: 'language',
      type: 'WEB',
    },
    {
      icon: 'web',
      type: 'BROWSER',
    },
    {
      icon: 'devices_other',
      type: 'NATIVE',
    },
    {
      icon: 'storage',
      type: 'SERVICE',
    },
    {
      icon: 'folder_shared',
      type: 'RESOURCE_SERVER',
    },
    {
      icon: 'smart_toy',
      type: 'AGENT',
    },
  ];

  constructor(
    private route: ActivatedRoute,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit(): void {
    // Track DomainStore so settings changes (e.g. toggling CIMD) take effect without a hard refresh.
    // Fall back to the route snapshot until the store emits.
    this.domain = this.domainStore.current ?? this.route.snapshot.data['domain'];
    this.domainStore.domain$.pipe(takeUntil(this.destroy$)).subscribe((domain) => {
      if (domain) {
        this.domain = domain;
      }
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  icon(app) {
    return find(this.applicationTypes, function (a) {
      return a.type === app.type;
    }).icon;
  }

  displayRedirectUri(): boolean {
    if (this.application.type === 'SERVICE') {
      return false;
    }
    // Autonomous agents don't need redirect URIs (client_credentials only)
    if (this.application.type === 'AGENT' && this.application.agentType === 'AUTONOMOUS') {
      return false;
    }
    return true;
  }

  elRedirectUriEnabled(): boolean {
    return this.domain?.oidc?.clientRegistrationSettings?.allowRedirectUriParamsExpressionLanguage;
  }

  cimdEnabled(): boolean {
    return !!this.domain?.oidc?.cimdSettings?.enabled;
  }

  isCimd(): boolean {
    return this.application.creationMode === 'cimd';
  }

  onModeChange(): void {
    // Reset transient fields when toggling so we don't carry stale state across modes.
    if (this.isCimd()) {
      this.application.redirectUri = null;
      this.application.clientId = null;
      this.application.clientSecret = null;
      this.application.name = null;
    } else {
      this.application.cimdUrl = null;
      this.application.cimdPreview = null;
      this.application.cimdClientName = null;
    }
  }

  selectCreationMode(value: string): void {
    if (this.application.creationMode === value) {
      return;
    }
    this.application.creationMode = value;
    this.onModeChange();
  }

  onCimdUrlChange(): void {
    // Force re-validation when the URL changes so we never create against a stale preview.
    this.application.cimdPreview = null;
    this.application.cimdClientName = null;
    this.application.name = null;
  }
}
