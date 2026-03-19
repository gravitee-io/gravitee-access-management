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
import { parse as parseYaml, stringify as stringifyYaml } from 'yaml';
import 'codemirror/mode/yaml/yaml';

import { ApplicationService } from '../../../../../services/application.service';
import { AuthorizationEngineService } from '../../../../../services/authorization-engine.service';
import { OpenFGAService } from '../../../../../services/openfga.service';
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

interface LanklockPolicy {
  compatibility?: string;
}

interface ParsedPolicy {
  version?: number;
  network: NetworkPolicy[];
  filesystem: FilesystemPolicy | null;
  process: ProcessPolicy | null;
  landlock: LanklockPolicy | null;
}

interface PolicyTemplate {
  id: string;
  name: string;
  description: string;
  yaml: string;
}

const POLICY_TEMPLATES: PolicyTemplate[] = [
  {
    id: 'base',
    name: 'Base (Claude Code + GitHub)',
    description: 'Claude Code agent with GitHub read-only access, PyPI, and sandboxed filesystem. The standard starting point.',
    yaml: `version: 1

filesystem_policy:
  include_workdir: true
  read_only:
    - /usr
    - /lib
    - /proc
    - /dev/urandom
    - /app
    - /etc
    - /var/log
  read_write:
    - /sandbox
    - /tmp
    - /dev/null

landlock:
  compatibility: best_effort

process:
  run_as_user: sandbox
  run_as_group: sandbox

network_policies:
  claude_code:
    name: claude-code
    endpoints:
      - { host: api.anthropic.com, port: 443, protocol: rest, enforcement: enforce, access: full, tls: terminate }
      - { host: statsig.anthropic.com, port: 443 }
      - { host: sentry.io, port: 443 }
      - { host: raw.githubusercontent.com, port: 443 }
      - { host: platform.claude.com, port: 443 }
    binaries:
      - { path: /usr/local/bin/claude }
      - { path: /usr/bin/node }

  github_ssh_over_https:
    name: github-ssh-over-https
    endpoints:
      - host: github.com
        port: 443
        protocol: rest
        tls: terminate
        enforcement: enforce
        rules:
          - allow:
              method: GET
              path: "/**/info/refs*"
          - allow:
              method: POST
              path: "/**/git-upload-pack"
    binaries:
      - { path: /usr/bin/git }

  github_rest_api:
    name: github-rest-api
    endpoints:
      - host: api.github.com
        port: 443
        protocol: rest
        tls: terminate
        enforcement: enforce
        access: read-only
    binaries:
      - { path: /usr/local/bin/claude }
      - { path: /usr/bin/gh }

  pypi:
    name: pypi
    endpoints:
      - { host: pypi.org, port: 443 }
      - { host: files.pythonhosted.org, port: 443 }
      - { host: github.com, port: 443 }
      - { host: objects.githubusercontent.com, port: 443 }
      - { host: api.github.com, port: 443 }
      - { host: downloads.python.org, port: 443 }
    binaries:
      - { path: /sandbox/.venv/bin/python }
      - { path: /sandbox/.venv/bin/python3 }
      - { path: /sandbox/.venv/bin/pip }
      - { path: /usr/local/bin/uv }
`,
  },
  {
    id: 'ollama',
    name: 'Ollama + Claude Code',
    description: 'Run local LLMs via Ollama alongside Claude Code with GitHub and npm access.',
    yaml: `version: 1

filesystem_policy:
  include_workdir: true
  read_only:
    - /usr
    - /lib
    - /proc
    - /dev/urandom
    - /app
    - /etc
    - /var/log
  read_write:
    - /sandbox
    - /tmp
    - /dev/null

landlock:
  compatibility: best_effort

process:
  run_as_user: sandbox
  run_as_group: sandbox

network_policies:
  ollama:
    name: ollama
    endpoints:
      - { host: ollama.com, port: 443 }
      - { host: www.ollama.com, port: 443 }
      - { host: registry.ollama.com, port: 443 }
      - { host: registry.ollama.ai, port: 443 }
      - { host: github.com, port: 443 }
      - { host: objects.githubusercontent.com, port: 443 }
      - { host: raw.githubusercontent.com, port: 443 }
    binaries:
      - { path: /usr/bin/curl }
      - { path: /bin/bash }
      - { path: /usr/local/bin/ollama }

  claude_code:
    name: claude_code
    endpoints:
      - { host: api.anthropic.com, port: 443, protocol: rest, enforcement: enforce, access: full, tls: terminate }
      - { host: statsig.anthropic.com, port: 443 }
      - { host: sentry.io, port: 443 }
      - { host: raw.githubusercontent.com, port: 443 }
      - { host: platform.claude.com, port: 443 }
    binaries:
      - { path: /usr/local/bin/claude }
      - { path: /usr/bin/node }

  npm:
    name: npm
    endpoints:
      - { host: registry.npmjs.org, port: 443 }
      - { host: npmjs.org, port: 443 }
    binaries:
      - { path: /usr/bin/npm }
      - { path: /usr/bin/node }
      - { path: /usr/bin/curl }

  github:
    name: github
    endpoints:
      - host: github.com
        port: 443
        protocol: rest
        tls: terminate
        enforcement: enforce
        rules:
          - allow:
              method: GET
              path: "/**/info/refs*"
          - allow:
              method: POST
              path: "/**/git-upload-pack"
    binaries:
      - { path: /usr/bin/git }

  github_rest_api:
    name: github-rest-api
    endpoints:
      - host: api.github.com
        port: 443
        protocol: rest
        tls: terminate
        enforcement: enforce
        rules:
          - allow:
              method: GET
              path: "/**"
          - allow:
              method: HEAD
              path: "/**"
    binaries:
      - { path: /usr/bin/gh }
`,
  },
  {
    id: 'openclaw',
    name: 'OpenClaw (Claude + NVIDIA NIM)',
    description: 'Claude Code with NVIDIA inference APIs, GitHub access, and VS Code remote support.',
    yaml: `version: 1

filesystem_policy:
  include_workdir: true
  read_only:
    - /usr
    - /lib
    - /proc
    - /dev/urandom
    - /app
    - /etc
    - /var/log
  read_write:
    - /sandbox
    - /tmp
    - /dev/null

landlock:
  compatibility: best_effort

process:
  run_as_user: sandbox
  run_as_group: sandbox

network_policies:
  claude_code:
    name: claude_code
    endpoints:
      - { host: api.anthropic.com, port: 443, protocol: rest, enforcement: enforce, access: full, tls: terminate }
      - { host: statsig.anthropic.com, port: 443 }
      - { host: sentry.io, port: 443 }
      - { host: raw.githubusercontent.com, port: 443 }
      - { host: platform.claude.com, port: 443 }
    binaries:
      - { path: /usr/local/bin/claude }
      - { path: /usr/bin/node }

  nvidia:
    name: nvidia
    endpoints:
      - { host: integrate.api.nvidia.com, port: 443 }
    binaries:
      - { path: /usr/bin/curl }
      - { path: /bin/bash }
      - { path: /usr/local/bin/opencode }

  nvidia_web:
    name: nvidia_web
    endpoints:
      - { host: nvidia.com, port: 443 }
      - { host: www.nvidia.com, port: 443 }
    binaries:
      - { path: /usr/bin/curl }

  github:
    name: github
    endpoints:
      - host: github.com
        port: 443
        protocol: rest
        tls: terminate
        enforcement: enforce
        rules:
          - allow:
              method: GET
              path: "/**/info/refs*"
          - allow:
              method: POST
              path: "/**/git-upload-pack"
    binaries:
      - { path: /usr/bin/git }

  github_rest_api:
    name: github-rest-api
    endpoints:
      - host: api.github.com
        port: 443
        protocol: rest
        tls: terminate
        enforcement: enforce
        access: read-only
    binaries:
      - { path: /usr/local/bin/claude }
      - { path: /usr/bin/gh }

  vscode:
    name: vscode
    endpoints:
      - { host: update.code.visualstudio.com, port: 443 }
      - { host: az764295.vo.msecnd.net, port: 443 }
      - { host: vscode.download.prss.microsoft.com, port: 443 }
      - { host: marketplace.visualstudio.com, port: 443 }
      - { host: gallerycdn.vsassets.io, port: 443 }
    binaries:
      - { path: /usr/bin/curl }
      - { path: /usr/bin/wget }
`,
  },
  {
    id: 'isolated',
    name: 'Isolated Compute',
    description: 'No network access at all. Sandboxed filesystem only. For pure computation tasks.',
    yaml: `version: 1

filesystem_policy:
  include_workdir: true
  read_only:
    - /usr
    - /lib
    - /proc
    - /dev/urandom
    - /etc
  read_write:
    - /sandbox
    - /tmp
    - /dev/null

landlock:
  compatibility: best_effort

process:
  run_as_user: sandbox
  run_as_group: sandbox

network_policies: {}
`,
  },
  {
    id: 'web-scraper',
    name: 'Web Scraper',
    description: 'Outbound HTTPS to any host for web scraping. Read-write /sandbox for storing results.',
    yaml: `version: 1

filesystem_policy:
  include_workdir: true
  read_only:
    - /usr
    - /lib
    - /proc
    - /dev/urandom
    - /etc
  read_write:
    - /sandbox
    - /tmp
    - /dev/null

landlock:
  compatibility: best_effort

process:
  run_as_user: sandbox
  run_as_group: sandbox

network_policies:
  web_access:
    name: web-access
    endpoints:
      - { host: "*", port: 443 }
      - { host: "*", port: 80 }
    binaries:
      - { path: /usr/bin/curl }
      - { path: /usr/bin/wget }
      - { path: /usr/bin/node }
      - { path: /sandbox/.venv/bin/python }
      - { path: /sandbox/.venv/bin/python3 }
`,
  },
  {
    id: 'cursor-dev',
    name: 'Cursor IDE + Claude Code',
    description: 'Full development environment with Cursor IDE, Claude Code, GitHub, npm, and PyPI access.',
    yaml: `version: 1

filesystem_policy:
  include_workdir: true
  read_only:
    - /usr
    - /lib
    - /proc
    - /dev/urandom
    - /app
    - /etc
    - /var/log
  read_write:
    - /sandbox
    - /tmp
    - /dev/null

landlock:
  compatibility: best_effort

process:
  run_as_user: sandbox
  run_as_group: sandbox

network_policies:
  claude_code:
    name: claude-code
    endpoints:
      - { host: api.anthropic.com, port: 443, protocol: rest, enforcement: enforce, access: full, tls: terminate }
      - { host: statsig.anthropic.com, port: 443 }
      - { host: sentry.io, port: 443 }
      - { host: raw.githubusercontent.com, port: 443 }
      - { host: platform.claude.com, port: 443 }
    binaries:
      - { path: /usr/local/bin/claude }
      - { path: /usr/bin/node }

  cursor:
    name: cursor
    endpoints:
      - { host: cursor.blob.core.windows.net, port: 443 }
      - { host: api2.cursor.sh, port: 443 }
      - { host: repo.cursor.sh, port: 443 }
      - { host: download.cursor.sh, port: 443 }
      - { host: cursor.download.prss.microsoft.com, port: 443 }
    binaries:
      - { path: /usr/bin/curl }
      - { path: /usr/bin/wget }

  github:
    name: github
    endpoints:
      - host: github.com
        port: 443
        protocol: rest
        tls: terminate
        enforcement: enforce
        rules:
          - allow:
              method: GET
              path: "/**/info/refs*"
          - allow:
              method: POST
              path: "/**/git-upload-pack"
    binaries:
      - { path: /usr/bin/git }

  github_rest_api:
    name: github-rest-api
    endpoints:
      - host: api.github.com
        port: 443
        protocol: rest
        tls: terminate
        enforcement: enforce
        access: read-only
    binaries:
      - { path: /usr/local/bin/claude }
      - { path: /usr/bin/gh }

  npm:
    name: npm
    endpoints:
      - { host: registry.npmjs.org, port: 443 }
      - { host: npmjs.org, port: 443 }
    binaries:
      - { path: /usr/bin/npm }
      - { path: /usr/bin/node }

  pypi:
    name: pypi
    endpoints:
      - { host: pypi.org, port: 443 }
      - { host: files.pythonhosted.org, port: 443 }
      - { host: downloads.python.org, port: 443 }
    binaries:
      - { path: /sandbox/.venv/bin/pip }
      - { path: /usr/local/bin/uv }
`,
  },
];

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
  syncing = false;
  syncStatus: 'idle' | 'success' | 'error' = 'idle';
  syncMessage: string | null = null;
  parseError: string | null = null;
  parsed: ParsedPolicy | null = null;

  authorizationEngines: any[] = [];
  selectedEngineId: string | null = null;

  templates = POLICY_TEMPLATES;
  selectedTemplateId: string | null = null;

  editorOptions = {
    lineNumbers: true,
    mode: 'yaml',
    theme: 'default',
    lineWrapping: false,
    indentWithTabs: false,
    tabSize: 2,
    indentUnit: 2,
    extraKeys: { Tab: (cm: any) => cm.replaceSelection('  ') },
  };

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
    private authorizationEngineService: AuthorizationEngineService,
    private openFGAService: OpenFGAService,
    private snackbarService: SnackbarService,
  ) {}

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = this.route.snapshot.data['application'];
    this.loadPolicy();
    this.loadAuthorizationEngines();
  }

  loadAuthorizationEngines(): void {
    this.authorizationEngineService.findByDomain(this.domainId).subscribe({
      next: (engines: any[]) => {
        this.authorizationEngines = engines ?? [];
        if (this.authorizationEngines.length === 1) {
          this.selectedEngineId = this.authorizationEngines[0].id;
        }
      },
      error: () => {
        this.authorizationEngines = [];
      },
    });
  }

  syncWithOpenFGA(): void {
    if (!this.selectedEngineId || this.syncing) return;

    const clientId = this.application?.settings?.oauth?.clientId;
    if (!clientId) {
      this.snackbarService.open('Cannot sync: application has no client_id');
      return;
    }

    const agentUser = `agent:${clientId}`;

    this.syncing = true;
    this.syncMessage = null;
    this.syncStatus = 'idle';

    this.openFGAService.readAllTuples(this.domainId, this.selectedEngineId).subscribe({
      next: (allTuples: any[]) => {
        const normalize = (t: any) => ({
          user: t.key?.user ?? t.user,
          relation: t.key?.relation ?? t.relation,
          object: t.key?.object ?? t.object,
        });

        const agentTuples = allTuples.filter((t: any) => (t.key?.user ?? t.user) === agentUser).map(normalize);

        const updatedYaml = this.tuplesToYaml(agentTuples, this.policy);

        this.policy = updatedYaml;
        this.reparsePolicySummary(updatedYaml);
        this.syncing = false;
        this.syncStatus = 'success';
        this.syncMessage = `Synced from OpenFGA: ${agentTuples.length} tuple(s) merged. Review and save when ready.`;
      },
      error: () => {
        this.syncing = false;
        this.syncStatus = 'error';
        this.syncMessage = 'Could not read tuples from OpenFGA. Check engine connectivity.';
      },
    });
  }

  private tuplesToYaml(tuples: { user: string; relation: string; object: string }[], existingYaml: string): string {
    // Parse existing policy as the base — tuples only add, never remove
    let raw: any = {};
    try {
      raw = parseYaml(existingYaml ?? '') ?? {};
    } catch {
      // start from empty if existing YAML is unparseable
    }

    const fs = raw.filesystem_policy ?? {};
    const existingReadOnly: string[] = fs.read_only ?? [];
    const existingReadWrite: string[] = fs.read_write ?? [];
    const existingNetworkPolicies: Record<string, any> = raw.network_policies ?? {};

    const readOnlySet = new Set<string>(existingReadOnly);
    const readWriteSet = new Set<string>(existingReadWrite);
    let includeWorkdir: boolean = fs.include_workdir ?? false;
    let lanklockCompatibility: string | null = raw.landlock?.compatibility ?? null;
    let runAsUser: string | null = raw.process?.run_as_user ?? null;
    let runAsGroup: string | null = raw.process?.run_as_group ?? null;
    const networkPolicyKeys = new Set<string>(Object.keys(existingNetworkPolicies));

    const endpointAccessMap: Record<string, string> = {
      accessible: 'full',
      read_only: 'read-only',
      forbidden: 'none',
    };
    // keyed by "host_port_protocol" to deduplicate
    const fgaEndpoints = new Map<string, { host: string; port: number; protocol: string; access: string }>();

    // Merge: add entries from OpenFGA that are not already present
    for (const t of tuples) {
      if (t.relation === 'reader' && t.object.startsWith('directory:')) {
        readOnlySet.add(t.object.slice('directory:'.length));
      } else if (t.relation === 'writer' && t.object.startsWith('directory:')) {
        readWriteSet.add(t.object.slice('directory:'.length));
      } else if (t.relation === 'enabled' && t.object === 'agent_flag:include_workdir') {
        includeWorkdir = true;
      } else if (t.relation === 'enabled' && t.object.startsWith('agent_flag:landlock_')) {
        lanklockCompatibility = t.object.slice('agent_flag:landlock_'.length).replace(/_/g, '-');
      } else if (t.relation === 'runner' && t.object.startsWith('system_user:')) {
        runAsUser = t.object.slice('system_user:'.length);
      } else if (t.relation === 'runner' && t.object.startsWith('system_group:')) {
        runAsGroup = t.object.slice('system_group:'.length);
      } else if (t.relation === 'accessible' && t.object.startsWith('network_policy:')) {
        networkPolicyKeys.add(t.object.slice('network_policy:'.length));
      } else if (t.object.startsWith('endpoint_') && t.relation in endpointAccessMap) {
        const colonIdx = t.object.indexOf(':');
        const objectType = t.object.slice(0, colonIdx);              // e.g. "endpoint_rest"
        const objectId = t.object.slice(colonIdx + 1);               // e.g. "api.example.com_443"
        const protocol = objectType.slice('endpoint_'.length);       // e.g. "rest"
        const lastUnderscore = objectId.lastIndexOf('_');
        if (lastUnderscore !== -1) {
          const host = objectId.slice(0, lastUnderscore);
          const port = Number(objectId.slice(lastUnderscore + 1));
          if (!isNaN(port)) {
            fgaEndpoints.set(`${host}_${port}_${protocol}`, { host, port, protocol, access: endpointAccessMap[t.relation] });
          }
        }
      }
    }

    // Merge fgaEndpoints into the 'fga_policy' block
    if (fgaEndpoints.size > 0) {
      const existingFgaPolicy = existingNetworkPolicies['fga_policy'] ?? { name: 'fga_policy', endpoints: [] };
      const existingEpMap = new Map<string, any>();
      for (const ep of (existingFgaPolicy.endpoints ?? [])) {
        existingEpMap.set(`${ep.host}_${ep.port}_${ep.protocol}`, ep);
      }
      for (const [key, entry] of fgaEndpoints) {
        const tls = entry.port === 443 ? { tls: 'terminate' } : {};
        existingEpMap.set(key, { ...(existingEpMap.get(key) ?? {}), ...entry, ...tls, enforcement: 'enforce' });
      }
      existingNetworkPolicies['fga_policy'] = { ...existingFgaPolicy, endpoints: [...existingEpMap.values()] };
      networkPolicyKeys.add('fga_policy');
    }

    const readOnly = [...readOnlySet];
    const readWrite = [...readWriteSet];

    const networkPoliciesObj: Record<string, any> = {};
    for (const key of networkPolicyKeys) {
      networkPoliciesObj[key] = existingNetworkPolicies[key] ?? { name: key };
    }

    const doc: any = { version: raw.version ?? 1 };

    if (readOnly.length > 0 || readWrite.length > 0) {
      const fsPolicy: any = { include_workdir: includeWorkdir };
      if (readOnly.length > 0) fsPolicy.read_only = readOnly;
      if (readWrite.length > 0) fsPolicy.read_write = readWrite;
      doc.filesystem_policy = fsPolicy;
    }

    if (lanklockCompatibility) {
      doc.landlock = { compatibility: lanklockCompatibility };
    }

    if (runAsUser || runAsGroup) {
      const proc: any = {};
      if (runAsUser) proc.run_as_user = runAsUser;
      if (runAsGroup) proc.run_as_group = runAsGroup;
      doc.process = proc;
    }

    doc.network_policies = networkPolicyKeys.size > 0 ? networkPoliciesObj : {};

    return stringifyYaml(doc, { lineWidth: 0 });
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

  download(): void {
    const blob = new Blob([this.policy], { type: 'text/yaml' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'policy.yaml';
    a.click();
    URL.revokeObjectURL(url);
  }

  applyTemplate(templateId: string): void {
    const tpl = POLICY_TEMPLATES.find((t) => t.id === templateId);
    if (!tpl) return;
    if (this.policy?.trim() && this.isDirty) {
      if (!confirm('Replace current editor content with template? Unsaved changes will be lost.')) {
        this.selectedTemplateId = null;
        return;
      }
    }
    this.policy = tpl.yaml;
    this.selectedTemplateId = null;
    this.reparsePolicySummary(this.policy);
    this.snackbarService.open(`Template "${tpl.name}" applied — review and save when ready`);
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
      const lk = raw?.landlock ?? null;

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
        landlock: lk ? { compatibility: lk.compatibility } : null,
      };
      this.parseError = null;
    } catch (e: any) {
      this.parsed = null;
      this.parseError = e?.message ?? 'Failed to parse YAML';
    }
  }
}
