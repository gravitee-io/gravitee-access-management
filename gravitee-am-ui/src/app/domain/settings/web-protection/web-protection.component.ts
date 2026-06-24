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
import { deepClone } from '@gravitee/ui-components/src/lib/utils';

import { DomainService } from '../../../services/domain.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { AuthService } from '../../../services/auth.service';
import { DomainStoreService } from '../../../stores/domain.store';

@Component({
  selector: 'app-domain-web-protection',
  templateUrl: './web-protection.component.html',
  styleUrls: ['./web-protection.component.scss'],
  standalone: false,
})
export class DomainSettingsWebProtectionComponent implements OnInit {
  formChanged = false;
  domain: any = {};
  readonly = false;
  httpMethods: string[] = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS', 'TRACE', 'CONNECT'];
  xframeActions: string[] = ['DENY', 'SAMEORIGIN'];
  headerValue: string;
  originValue: string;
  directiveValue: string;

  private readonly defaultCorsConfiguration = {
    inherited: true,
    allowedOrigins: [],
    allowedMethods: [],
    allowedHeaders: [],
    maxAge: null,
    allowCredentials: false,
    enabled: false,
  };

  private readonly defaultWebProtectionSettings = {
    csp: {
      inherited: true,
      enabled: false,
      reportOnly: false,
      scriptInlineNonce: true,
      directives: [],
    },
    xframe: {
      inherited: true,
      enabled: false,
      action: 'DENY',
    },
    xss: {
      inherited: true,
      enabled: false,
      action: '1; mode=block',
    },
  };

  constructor(
    private domainService: DomainService,
    private snackbarService: SnackbarService,
    private authService: AuthService,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit(): void {
    this.domainStore.domain$.subscribe((domain) => {
      this.domain = deepClone(domain);
      this.initializeSettings();
    });
    this.readonly = !this.authService.hasPermissions(['domain_settings_update']);
  }

  private initializeSettings(): void {
    this.domain.corsSettings = this.domain.corsSettings || deepClone(this.defaultCorsConfiguration);
    this.domain.webProtectionSettings = this.domain.webProtectionSettings || deepClone(this.defaultWebProtectionSettings);

    if (!this.domain.webProtectionSettings.csp) {
      this.domain.webProtectionSettings.csp = deepClone(this.defaultWebProtectionSettings.csp);
    }
    if (!this.domain.webProtectionSettings.xframe) {
      this.domain.webProtectionSettings.xframe = deepClone(this.defaultWebProtectionSettings.xframe);
    }
    if (!this.domain.webProtectionSettings.xss) {
      this.domain.webProtectionSettings.xss = deepClone(this.defaultWebProtectionSettings.xss);
    }

    if (!this.domain.webProtectionSettings.csp.directives) {
      this.domain.webProtectionSettings.csp.directives = [];
    }
    if (!this.domain.corsSettings.allowedOrigins) {
      this.domain.corsSettings.allowedOrigins = [];
    }
    if (!this.domain.corsSettings.allowedHeaders) {
      this.domain.corsSettings.allowedHeaders = [];
    }

    this.normalizeInherited(this.domain.corsSettings);
    this.normalizeInherited(this.domain.webProtectionSettings.csp);
    this.normalizeInherited(this.domain.webProtectionSettings.xframe);
    this.normalizeInherited(this.domain.webProtectionSettings.xss);

    if (this.domain.corsSettings.maxAge === 0) {
      this.domain.corsSettings.maxAge = null;
    }
  }

  private normalizeInherited(settings: { inherited?: boolean; enabled?: boolean }): void {
    if (settings.inherited === undefined || settings.inherited === null) {
      settings.inherited = !settings.enabled;
    }
  }

  update(): void {
    this.flushPendingChipValues();
    this.domainService.patchWebProtectionSettings(this.domain.id, this.domain).subscribe((response) => {
      this.domain = response;
      this.domainStore.set(response);
      this.initializeSettings();
      this.domainService.notify(this.domain);
      this.formChanged = false;
      this.snackbarService.open('Web protection settings for ' + this.domain.name + ' updated');
    });
  }

  isInherited(settings: { inherited?: boolean }): boolean {
    return settings?.inherited !== false;
  }

  enableInherit(settings: { inherited?: boolean; enabled?: boolean }, event: any): void {
    settings.inherited = event.checked;
    if (event.checked) {
      settings.enabled = false;
    }
    this.formChanged = true;
  }

  enableCorsSettings(event: any): void {
    this.domain.corsSettings.enabled = event.checked;
    this.formChanged = true;
  }

  isCorsSettingsEnabled(): boolean {
    return this.domain.corsSettings?.enabled;
  }

  enableAllowCredentials(event: any): void {
    this.domain.corsSettings.allowCredentials = event.checked;
    this.formChanged = true;
  }

  isAllowCredentialsEnabled(): boolean {
    return this.domain.corsSettings?.allowCredentials;
  }

  updateAllowedMethod(event: any): void {
    this.domain.corsSettings.allowedMethods = event.value;
    this.formChanged = true;
  }

  addHeader(event?: Event): void {
    event?.preventDefault();
    this.addHeaderValue();
  }

  private addHeaderValue(): void {
    const value = this.headerValue?.trim();
    if (!value) {
      return;
    }
    if (!this.domain.corsSettings.allowedHeaders.some((el) => el === value)) {
      this.domain.corsSettings.allowedHeaders.push(value);
      this.domain.corsSettings.allowedHeaders = [...this.domain.corsSettings.allowedHeaders];
      this.formChanged = true;
      this.headerValue = '';
    } else {
      this.snackbarService.open(`Error : Header "${value}" already exists`);
    }
  }

  removeHeader(dwPattern: string): void {
    const index = this.domain.corsSettings.allowedHeaders.indexOf(dwPattern);
    if (index > -1) {
      this.domain.corsSettings.allowedHeaders.splice(index, 1);
      this.formChanged = true;
    }
  }

  addOrigin(event?: Event): void {
    event?.preventDefault();
    this.addOriginValue();
  }

  private addOriginValue(): void {
    const value = this.originValue?.trim();
    if (!value) {
      return;
    }
    if (!this.domain.corsSettings.allowedOrigins.some((el) => el === value)) {
      this.domain.corsSettings.allowedOrigins.push(value);
      this.domain.corsSettings.allowedOrigins = [...this.domain.corsSettings.allowedOrigins];
      this.formChanged = true;
      this.originValue = '';
    } else {
      this.snackbarService.open(`Error : Origin "${value}" already exists`);
    }
  }

  removeOrigin(dwPattern: string): void {
    const index = this.domain.corsSettings.allowedOrigins.indexOf(dwPattern);
    if (index > -1) {
      this.domain.corsSettings.allowedOrigins.splice(index, 1);
      this.formChanged = true;
    }
  }

  enableCspSettings(event: any): void {
    this.domain.webProtectionSettings.csp.enabled = event.checked;
    this.formChanged = true;
  }

  isCspSettingsEnabled(): boolean {
    return this.domain.webProtectionSettings?.csp?.enabled;
  }

  addDirective(event?: Event): void {
    event?.preventDefault();
    this.addDirectiveValue();
  }

  private addDirectiveValue(): void {
    const value = this.directiveValue?.trim();
    if (!value) {
      return;
    }
    const directives = this.domain.webProtectionSettings.csp.directives;
    if (!directives.some((el) => el === value)) {
      directives.push(value);
      this.domain.webProtectionSettings.csp.directives = [...directives];
      this.formChanged = true;
      this.directiveValue = '';
    } else {
      this.snackbarService.open(`Error : Directive "${value}" already exists`);
    }
  }

  removeDirective(directive: string): void {
    const directives = this.domain.webProtectionSettings.csp.directives;
    const index = directives.indexOf(directive);
    if (index > -1) {
      directives.splice(index, 1);
      this.formChanged = true;
    }
  }

  enableXFrameSettings(event: any): void {
    this.domain.webProtectionSettings.xframe.enabled = event.checked;
    this.formChanged = true;
  }

  isXFrameSettingsEnabled(): boolean {
    return this.domain.webProtectionSettings?.xframe?.enabled;
  }

  enableXssSettings(event: any): void {
    this.domain.webProtectionSettings.xss.enabled = event.checked;
    this.formChanged = true;
  }

  isXssSettingsEnabled(): boolean {
    return this.domain.webProtectionSettings?.xss?.enabled;
  }

  updateFormState(): void {
    this.formChanged = true;
  }

  private flushPendingChipValues(): void {
    if (this.originValue?.trim()) {
      this.addOriginValue();
    }
    if (this.headerValue?.trim()) {
      this.addHeaderValue();
    }
    if (this.directiveValue?.trim()) {
      this.addDirectiveValue();
    }
  }
}
