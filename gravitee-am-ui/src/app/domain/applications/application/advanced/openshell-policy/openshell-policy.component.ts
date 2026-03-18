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
import { parse as parseYaml } from 'yaml';

import { ApplicationService } from '../../../../../services/application.service';
import { SnackbarService } from '../../../../../services/snackbar.service';

interface NetworkEndpoint {
  host: string;
  port?: number;
  ports?: number[];
  protocol?: string;
  enforcement?: string;
  access?: string;
}

interface NetworkPolicy {
  key: string;
  name?: string;
  endpoints: NetworkEndpoint[];
  binaryCount: number;
}

interface FilesystemPolicy {
  includeWorkdir: boolean;
  readOnly: string[];
  readWrite: string[];
}

interface ProcessPolicy {
  runAsUser?: string;
  runAsGroup?: string;
}

interface ParsedPolicy {
  version?: number;
  network: NetworkPolicy[];
  filesystem: FilesystemPolicy | null;
  process: ProcessPolicy | null;
}

@Component({
  selector: 'app-application-openshell-policy',
  templateUrl: './openshell-policy.component.html',
  styleUrls: ['./openshell-policy.component.scss'],
  standalone: false,
})
export class OpenShellPolicyComponent implements OnInit {
  domainId: string;
  application: any;
  policy: string = '';
  savedPolicy: string = '';
  loading = false;
  saving = false;
  parseError: string | null = null;
  parsed: ParsedPolicy | null = null;

  get isDirty(): boolean {
    return this.policy !== this.savedPolicy;
  }

  get hasPolicy(): boolean {
    return !!this.savedPolicy;
  }

  get gatewayUrl(): string {
    return window.location.origin.replace(':4200', ':8092');
  }

  get managementUrl(): string {
    return window.location.origin.replace(':4200', ':8093');
  }

  constructor(
    private route: ActivatedRoute,
    private applicationService: ApplicationService,
    private snackbarService: SnackbarService,
  ) {}

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = this.route.snapshot.data['application'];
    this.loadPolicy();
  }

  loadPolicy(): void {
    this.loading = true;
    this.applicationService.getOpenShellPolicy(this.domainId, this.application.id).subscribe({
      next: (yaml: string) => {
        this.policy = yaml;
        this.savedPolicy = yaml;
        this.loading = false;
        this.reparsePolicySummary(yaml);
      },
      error: () => {
        this.policy = '';
        this.savedPolicy = '';
        this.loading = false;
      },
    });
  }

  save(): void {
    if (!this.policy || !this.policy.trim()) {
      this.snackbarService.open('Policy must not be empty');
      return;
    }
    this.saving = true;
    this.applicationService.setOpenShellPolicy(this.domainId, this.application.id, this.policy).subscribe({
      next: () => {
        this.savedPolicy = this.policy;
        this.saving = false;
        this.snackbarService.open('OpenShell policy saved');
        this.reparsePolicySummary(this.policy);
      },
      error: () => {
        this.saving = false;
        this.snackbarService.open('Failed to save policy');
      },
    });
  }

  clear(): void {
    this.applicationService.deleteOpenShellPolicy(this.domainId, this.application.id).subscribe({
      next: () => {
        this.policy = '';
        this.savedPolicy = '';
        this.parsed = null;
        this.parseError = null;
        this.snackbarService.open('OpenShell policy cleared');
      },
      error: () => {
        this.snackbarService.open('Failed to clear policy');
      },
    });
  }

  displayPorts(endpoint: NetworkEndpoint): string {
    if (endpoint.ports?.length) return endpoint.ports.join(', ');
    if (endpoint.port) return String(endpoint.port);
    return '—';
  }

  private reparsePolicySummary(yaml: string): void {
    if (!yaml?.trim()) {
      this.parsed = null;
      this.parseError = null;
      return;
    }
    try {
      const raw: any = parseYaml(yaml);
      const networkPolicies: NetworkPolicy[] = [];
      if (raw?.network_policies && typeof raw.network_policies === 'object') {
        for (const [key, rule] of Object.entries<any>(raw.network_policies)) {
          const endpoints: NetworkEndpoint[] = (rule?.endpoints ?? []).map((e: any) => ({
            host: e.host ?? '',
            port: e.port,
            ports: e.ports,
            protocol: e.protocol,
            enforcement: e.enforcement,
            access: e.access,
          }));
          networkPolicies.push({
            key,
            name: rule?.name,
            endpoints,
            binaryCount: rule?.binaries?.length ?? 0,
          });
        }
      }

      const fs = raw?.filesystem_policy ?? null;
      const proc = raw?.process ?? null;

      this.parsed = {
        version: raw?.version,
        network: networkPolicies,
        filesystem: fs
          ? {
              includeWorkdir: fs.include_workdir ?? false,
              readOnly: fs.read_only ?? [],
              readWrite: fs.read_write ?? [],
            }
          : null,
        process: proc
          ? {
              runAsUser: proc.run_as_user,
              runAsGroup: proc.run_as_group,
            }
          : null,
      };
      this.parseError = null;
    } catch (e: any) {
      this.parsed = null;
      this.parseError = e?.message ?? 'Failed to parse YAML';
    }
  }
}
