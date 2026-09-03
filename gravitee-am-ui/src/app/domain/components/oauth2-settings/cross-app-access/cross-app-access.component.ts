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
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';

import { CrossAppAccessService } from '../../../../services/cross-app-access.service';

import {
  DEFAULT_ID_JAG_VALIDITY_SECONDS,
  CrossAppAccessResourceServerMapping,
  CrossAppAccessResourceServerOption,
  CrossAppAccessRow,
  resourceServerLabel,
} from './cross-app-access.types';

@Component({
  selector: 'app-cross-app-access-settings',
  templateUrl: './cross-app-access.component.html',
  styleUrls: ['./cross-app-access.component.scss'],
  standalone: false,
})
export class CrossAppAccessComponent implements OnInit {
  @Input() oauthSettings: any;
  @Input() domainId: string;
  @Input() readonly = false;
  @Input() disabled = false;

  @Output() settingsChange = new EventEmitter<void>();

  readonly displayedColumns = ['resourceServer', 'clientId', 'actions'];

  resourceServers: CrossAppAccessResourceServerOption[] = [];
  resourceServersUnavailable = false;

  rows: CrossAppAccessRow[] = [];
  availableResourceServers: CrossAppAccessResourceServerOption[] = [];

  newMapping: CrossAppAccessResourceServerMapping = { resourceServerId: '', clientId: '' };

  constructor(private crossAppAccessService: CrossAppAccessService) {}

  ngOnInit() {
    this.oauthSettings = this.oauthSettings || {};
    this.refresh();
    if (this.domainId) {
      this.crossAppAccessService.listResourceServers(this.domainId).subscribe({
        next: (resourceServers) => {
          this.resourceServers = resourceServers || [];
          this.refresh();
        },
        error: () => {
          this.resourceServers = [];
          this.resourceServersUnavailable = true;
          this.refresh();
        },
      });
    }
  }

  isEnabled(): boolean {
    return this.oauthSettings.crossAppAccessSettings?.enabled === true;
  }

  enableCrossAppAccess(event: any) {
    this.oauthSettings.crossAppAccessSettings = {
      ...this.oauthSettings.crossAppAccessSettings,
      enabled: event.checked,
      resourceServers: this.mappings(),
    };
    if (event.checked && this.oauthSettings.idJagValiditySeconds == null) {
      this.oauthSettings.idJagValiditySeconds = DEFAULT_ID_JAG_VALIDITY_SECONDS;
    }
    this.settingsChange.emit();
  }

  label(option: CrossAppAccessResourceServerOption): string {
    return resourceServerLabel(option);
  }

  isNewMappingValid(): boolean {
    return !!this.newMapping.resourceServerId && !!this.newMapping.clientId?.trim();
  }

  addMapping() {
    if (!this.isNewMappingValid()) {
      return;
    }
    const option = this.resourceServers.find((candidate) => candidate.resourceServerId === this.newMapping.resourceServerId);
    const mapping: CrossAppAccessResourceServerMapping = {
      trustDomainId: option?.trustDomainId,
      resourceServerId: this.newMapping.resourceServerId,
      clientId: this.newMapping.clientId.trim(),
    };
    this.setMappings([...this.mappings(), mapping]);
    this.newMapping = { resourceServerId: '', clientId: '' };
    this.settingsChange.emit();
  }

  removeMapping(index: number) {
    const mappings = [...this.mappings()];
    mappings.splice(index, 1);
    this.setMappings(mappings);
    this.settingsChange.emit();
  }

  private mappings(): CrossAppAccessResourceServerMapping[] {
    return this.oauthSettings.crossAppAccessSettings?.resourceServers ?? [];
  }

  private setMappings(mappings: CrossAppAccessResourceServerMapping[]) {
    this.oauthSettings.crossAppAccessSettings = {
      ...this.oauthSettings.crossAppAccessSettings,
      resourceServers: mappings,
    };
    this.refresh();
  }

  private refresh() {
    this.rows = this.mappings().map((mapping) => ({
      mapping,
      option: this.resourceServers.find((option) => option.resourceServerId === mapping.resourceServerId),
    }));
    const taken = new Set(this.mappings().map((mapping) => mapping.resourceServerId));
    this.availableResourceServers = this.resourceServers.filter((option) => !taken.has(option.resourceServerId));
  }
}
