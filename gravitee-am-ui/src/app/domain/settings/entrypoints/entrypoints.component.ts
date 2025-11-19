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
import { Component, OnInit, ViewChild } from '@angular/core';
import { MatInput } from '@angular/material/input';
import { ActivatedRoute } from '@angular/router';
import { deepClone } from '@gravitee/ui-components/src/lib/utils';
import regexEscape from 'regex-escape';

import { DomainService } from '../../../services/domain.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { AuthService } from '../../../services/auth.service';
import { DomainStoreService } from '../../../stores/domain.store';

@Component({
  selector: 'app-general',
  templateUrl: './entrypoints.component.html',
  styleUrls: ['./entrypoints.component.scss'],
})
export class DomainSettingsEntrypointsComponent implements OnInit {
  @ViewChild('chipInput') chipInput: MatInput;
  formChanged = false;
  domain: any = {};
  entrypoint: any;
  readonly = false;
  switchModeLabel: string;
  domainRestrictions: string[];
  domainRegexList: RegExp[] = [];
  hostPattern: string;
  httpMethods: string[] = [
    'GET',
    'POST',
    'PUT',
    'PATCH',
    'DELETE',
    'HEAD',
    'OPTIONS',
    'TRACE',
    'CONNECT',
    'PROPFIND',
    'PROPPATCH',
    'MKCOL',
    'COPY',
    'MOVE',
    'LOCK',
    'UNLOCK',
    'MKCALENDAR',
    'VERSION_CONTROL',
    'REPORT',
    'CHECKOUT',
    'CHECKIN',
    'UNCHECKOUT',
    'MKWORKSPACE',
    'UPDATE',
    'LABEL',
    'MERGE',
    'BASELINE_CONTROL',
    'MKACTIVITY',
    'ORDERPATCH',
    'ACL',
    'SEARCH',
  ];
  headerValue: string;
  originValue: string;
  defaultCorsConfiguration = {
    allowedOrigins: [],
    allowedMethods: [],
    allowedHeaders: [],
    maxAge: Number,
    allowCredentials: false,
  };

  constructor(
    private domainService: DomainService,
    private snackbarService: SnackbarService,
    private route: ActivatedRoute,
    private authService: AuthService,
    private domainStore: DomainStoreService,
  ) {}

  ngOnInit() {
    this.domainStore.domain$.subscribe((domain) => (this.domain = deepClone(domain)));
    this.entrypoint = this.route.snapshot.data['entrypoint'];
    this.domain.corsSettings = this.domain.corsSettings || this.defaultCorsConfiguration;
    if (this.domain.corsSettings.maxAge === 0) {
      this.domain.corsSettings.maxAge = null;
    }
    if (this.domain.vhosts === undefined) {
      this.domain.vhosts = [];
    }

    this.readonly = !this.authService.hasPermissions(['domain_settings_update']);
    this.changeSwitchModeLabel();

    this.domainRestrictions = this.route.snapshot.data['environment'].domainRestrictions;

    if (this.domainRestrictions === undefined) {
      this.domainRestrictions = [];
    }

    this.hostPattern = this.buildHostPattern();

    // Prepare host regex (used to assist user when specifying an host).
    this.domainRestrictions.forEach((hostOption) => this.domainRegexList.push(new RegExp('\\.?' + hostOption + '$', 'i')));
  }

  private buildHostPattern(): string {
    const domainLabelPattern = '(?!-)[A-Za-z0-9\\-_]{1,63}(?<![\\-_])' as const;
    const portPattern = '(?::[0-9]{1,5})' as const;

    if (this.domainRestrictions.length === 0) {
      return `^(?:${domainLabelPattern})(?:\\.${domainLabelPattern})*${portPattern}?$`;
    }

    return `^(?:${domainLabelPattern}\\.)*(?:${this.domainRestrictions.map((value) => regexEscape(value)).join('|')})${portPattern}?$`;
  }

  update(): void {
    this.domainService.patchEntrypoints(this.domain.id, this.domain).subscribe((response) => {
      this.domain = response;
      this.domainStore.set(response);
      if (this.domain.corsSettings.maxAge === 0) {
        this.domain.corsSettings.maxAge = null;
      }
      this.domainService.notify(this.domain);
      this.formChanged = false;
      this.snackbarService.open('Domain ' + this.domain.name + ' updated');
    });
  }

  switchMode(): void {
    this.domain.vhostMode = !this.domain.vhostMode;
    this.changeSwitchModeLabel();
    this.formChanged = true;
  }

  addVhost(): void {
    if (this.domain.vhosts.length === 0) {
      this.domain.vhosts.push({ host: '', path: this.domain.path, overrideEntrypoint: true });
    } else {
      this.domain.vhosts.push({ host: '', path: '/' });
    }
  }

  remove(vhost: any): void {
    this.domain.vhosts = this.domain.vhosts.filter((v) => v !== vhost);
    this.formChanged = true;
  }

  changeSwitchModeLabel(): void {
    if (this.domain.vhostMode === true) {
      this.switchModeLabel = 'context-path';
    } else {
      this.switchModeLabel = 'virtual hosts';
    }
  }

  overrideEntrypointChange(vhost: any): void {
    this.domain.vhosts.filter((v) => v !== vhost).forEach((v) => (v.overrideEntrypoint = false));
    vhost.overrideEntrypoint = true;
    this.formChanged = true;
  }

  getHostOptions(host: string): string[] {
    if (host !== '') {
      const hostAndPort = host.split(':');
      let finalHost = hostAndPort[0];
      let finalPort = '';

      if (hostAndPort.length > 1) {
        finalPort = ':' + hostAndPort[1];
      }

      this.domainRegexList.forEach((regex) => (finalHost = finalHost.replace(regex, '')));

      if (!this.domainRestrictions.includes(finalHost)) {
        if (finalHost === '') {
          return this.domainRestrictions.map((domain) => domain + finalPort);
        }
        return this.domainRestrictions.map((domain) => finalHost + '.' + domain + finalPort);
      }
    }

    return this.domainRestrictions;
  }

  hostSelected(input: HTMLInputElement): void {
    input.blur();
    this.formChanged = true;
  }

  addHeader(event: Event): void {
    event.preventDefault();
    if (this.headerValue) {
      if (!this.domain.corsSettings.allowedHeaders.some((el) => el === this.headerValue)) {
        this.domain.corsSettings.allowedHeaders.push(this.headerValue);
        this.domain.corsSettings.allowedHeaders = [...this.domain.corsSettings.allowedHeaders];
        this.formChanged = true;
        this.headerValue = '';
      } else {
        this.snackbarService.open(`Error : Header "${this.headerValue}" already exists`);
      }
    }
  }

  removeHeader(dwPattern: string): void {
    const index = this.domain.corsSettings.allowedHeaders.indexOf(dwPattern);
    if (index > -1) {
      this.domain.corsSettings.allowedHeaders.splice(index, 1);
      this.formChanged = true;
    }
  }

  addOrigin(event: Event): void {
    event.preventDefault();
    if (this.originValue) {
      if (!this.domain.corsSettings.allowedOrigins.some((el) => el === this.originValue)) {
        this.domain.corsSettings.allowedOrigins.push(this.originValue);
        this.domain.corsSettings.allowedOrigins = [...this.domain.corsSettings.allowedOrigins];
        this.formChanged = true;
        this.originValue = '';
      } else {
        this.snackbarService.open(`Error : Header "${this.originValue}" already exists`);
      }
    }
  }

  removeOrigin(dwPattern: string): void {
    const index = this.domain.corsSettings.allowedOrigins.indexOf(dwPattern);
    if (index > -1) {
      this.domain.corsSettings.allowedOrigins.splice(index, 1);
      this.formChanged = true;
    }
  }

  updateAllowedMethod(event: any): void {
    this.domain.corsSettings.allowedMethods = event.value;
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

  updateFormState(): void {
    this.formChanged = true;
  }
}
