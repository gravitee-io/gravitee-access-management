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
import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { find } from 'lodash';

@Component({
  selector: 'application-creation-step2',
  templateUrl: './step2.component.html',
  styleUrls: ['./step2.component.scss'],
  standalone: false,
})
export class ApplicationCreationStep2Component implements OnInit {
  @Input() application: any;
  @ViewChild('appForm') form: any;
  domain: any;
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
  ];

  constructor(private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.domain = this.route.snapshot.data['domain'];
    if (this.application.creationMode == null) {
      this.application.creationMode = 'manual';
    }
  }

  icon(app) {
    return find(this.applicationTypes, function (a) {
      return a.type === app.type;
    }).icon;
  }

  displayRedirectUri(): boolean {
    return this.application.type !== 'SERVICE';
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

  onCimdUrlChange(): void {
    // Force re-validation when the URL changes so we never create against a stale preview.
    this.application.cimdPreview = null;
    this.application.cimdClientName = null;
    this.application.name = null;
  }
}
