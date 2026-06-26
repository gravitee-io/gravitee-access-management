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
import { Component, HostListener, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import '@gravitee/ui-components/wc/gv-design';
import { filter, switchMap, tap } from 'rxjs/operators';

import { OrganizationService } from '../../../../../services/organization.service';
import { SnackbarService } from '../../../../../services/snackbar.service';
import { ApplicationService } from '../../../../../services/application.service';
import { DialogService } from '../../../../../services/dialog.service';
import { ProtectedResourceService } from '../../../../../services/protected-resource.service';

@Component({
  selector: 'app-application-flows',
  templateUrl: './flows.component.html',
  styleUrls: ['./flows.component.scss'],
  standalone: false,
})
export class ApplicationFlowsComponent implements OnInit {
  private domainId: string;
  private entity: any;
  private flowsContext: string;
  policies: any[];
  definition: any = {};
  flowSchema: string;
  documentation: string;
  isDirty = false;

  @ViewChild('gvDesignComponent', { static: true }) gvDesignComponent;
  isInvalid: boolean;

  constructor(
    private route: ActivatedRoute,
    private organizationService: OrganizationService,
    private applicationService: ApplicationService,
    private protectedResourceService: ProtectedResourceService,
    private snackbarService: SnackbarService,
    private dialogService: DialogService,
  ) {}

  private static readonly TOKEN_FLOW_TYPES = ['token'];

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.flowsContext = this.route.snapshot.data['flowsContext'];
    this.entity = this.resolveEntity();
    this.flowSchema = this.filterFlowSchema(this.route.snapshot.data['flowSettingsForm']);
    const flows = this.route.snapshot.data['flows'] || [];
    this.definition.flows = this.filterAllowedFlows(flows);
    this.initPolicies();
  }

  isMcpContext(): boolean {
    return this.flowsContext === 'mcp';
  }

  private resolveEntity(): any {
    return this.isMcpContext() ? this.route.snapshot.data['mcpServer'] : this.route.snapshot.data['application'];
  }

  private isServiceApp(): boolean {
    return this.entity?.type?.toUpperCase() === 'SERVICE';
  }

  private restrictToTokenFlow(): boolean {
    return this.isMcpContext() || this.isServiceApp();
  }

  private filterAllowedFlows(flows: any[]): any[] {
    return this.restrictToTokenFlow() ? flows.filter((f) => ApplicationFlowsComponent.TOKEN_FLOW_TYPES.includes(f.type)) : flows;
  }

  private filterFlowSchema(schema: any): any {
    if (!this.restrictToTokenFlow() || !schema) {
      return schema;
    }
    const isStringSchema = typeof schema === 'string';
    const parsed = isStringSchema ? JSON.parse(schema) : structuredClone(schema);
    const allowedTypes = ApplicationFlowsComponent.TOKEN_FLOW_TYPES;
    if (parsed.properties?.type) {
      if (Array.isArray(parsed.properties.type.enum)) {
        parsed.properties.type.enum = parsed.properties.type.enum.filter((t: string) => allowedTypes.includes(t));
      }
      if (parsed.properties.type.default && !allowedTypes.includes(parsed.properties.type.default)) {
        parsed.properties.type.default = allowedTypes[0];
      }
      if (parsed.properties.type['x-schema-form']?.titleMap) {
        const filteredTitleMap = {};
        for (const key of allowedTypes) {
          if (parsed.properties.type['x-schema-form'].titleMap[key]) {
            filteredTitleMap[key] = parsed.properties.type['x-schema-form'].titleMap[key];
          }
        }
        parsed.properties.type['x-schema-form'].titleMap = filteredTitleMap;
      }
    }
    return isStringSchema ? JSON.stringify(parsed) : parsed;
  }

  @HostListener(':gv-design:fetch-documentation', ['$event.detail'])
  onFetchDocumentation(detail) {
    const policy = detail.policy;
    this.organizationService.policyDocumentation(policy.id).subscribe(
      (response) => {
        this.gvDesignComponent.nativeElement.documentation = {
          content: response,
          image: policy.icon,
          id: policy.id,
        };
      },
      () => {
        this.gvDesignComponent.nativeElement.documentation = null;
      },
    );
  }

  _stringifyConfiguration(step) {
    if (step.configuration != null) {
      const configuration = typeof step.configuration === 'string' ? step.configuration : JSON.stringify(step.configuration);
      return { ...step, configuration };
    }
    return step;
  }

  @HostListener(':gv-design:change', ['$event.detail'])
  onChange({ definition, errors, isDirty }) {
    this.isInvalid = errors > 0 || definition == null;
    this.isDirty = isDirty;
    if (isDirty && !this.isInvalid) {
      this.definition = definition;
    }
  }

  onReset() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.entity = this.resolveEntity();
    this.flowSchema = this.filterFlowSchema(this.route.snapshot.data['flowSettingsForm']);
    const flows = structuredClone(this.route.snapshot.data['flows'] || []);
    this.definition = {
      flows: this.filterAllowedFlows(flows),
    };
    this.initPolicies();
    this.isDirty = false;
  }

  async onSubmit() {
    this.isInvalid = true;
    await this.gvDesignComponent.nativeElement.validate();
    const flows = this.definition.flows.map((flow) => {
      delete flow.icon;
      delete flow.createdAt;
      delete flow.updatedAt;
      flow.pre = flow.pre.map(this._stringifyConfiguration);
      flow.post = flow.post.map(this._stringifyConfiguration);
      return flow;
    });

    const updateFlows$ = this.isMcpContext()
      ? this.protectedResourceService.updateFlows(this.domainId, this.entity.id, flows)
      : this.applicationService.updateFlows(this.domainId, this.entity.id, flows);

    updateFlows$.subscribe((updatedFlows) => {
      this.gvDesignComponent.nativeElement.saved();
      this.definition = { ...this.definition, flows: updatedFlows };
      this.snackbarService.open('Flows updated');
      this.isDirty = false;
      this.isInvalid = false;
    });
  }

  @HostListener(':gv-expression-language:ready', ['$event.detail'])
  fetchSpelGrammar({ currentTarget }) {
    this.organizationService
      .spelGrammar()
      .toPromise()
      .then((response) => {
        currentTarget.grammar = response;
      });
  }

  enableInheritMode(event) {
    this.dialogService
      .confirm('Inherit Flows', 'Are you sure you want to change the execution flows behavior ?')
      .pipe(
        filter((res) => {
          if (res === false) {
            event.source.checked = !event.checked;
          }
          return res;
        }),
        switchMap(() => this.persistInheritMode(event.checked)),
        tap((data) => {
          this.entity = data;
          if (this.isMcpContext()) {
            this.route.snapshot.parent.data['mcpServer'] = this.entity;
            this.snackbarService.open('MCP server updated');
          } else {
            this.route.snapshot.parent.parent.data['application'] = this.entity;
            this.snackbarService.open('Application updated');
          }
        }),
      )
      .subscribe();
  }

  private persistInheritMode(flowsInherited: boolean) {
    if (this.isMcpContext()) {
      // The protected resource PATCH replaces settings wholesale (no deep-merge like applications).
      const settings = { ...(this.entity.settings || {}) };
      settings.advanced = { ...(settings.advanced || {}), flowsInherited };
      return this.protectedResourceService.patch(this.domainId, this.entity.id, { settings });
    }
    return this.applicationService.patch(this.domainId, this.entity.id, {
      settings: { advanced: { flowsInherited } },
    });
  }

  isInherited() {
    const flowsInherited = this.entity?.settings?.advanced?.flowsInherited;
    // When unset, reflect the gateway/model default (inherit enabled) for MCP servers.
    return flowsInherited ?? this.isMcpContext();
  }

  private initPolicies() {
    const factors = this.route.snapshot.data['factors'] || [];
    const appFactorIds = this.entity.factors || [];
    const filteredFactors = factors
      .filter((f) => appFactorIds.includes(f.id))
      .filter((f) => f.factorType && f.factorType.toUpperCase() !== 'RECOVERY_CODE');
    this.policies = (this.route.snapshot.data['policies'] || []).map((policy) => {
      if (policy.schema == null) {
        return policy;
      }
      const policySchema = JSON.parse(policy.schema);
      if (policySchema.properties) {
        for (const key in policySchema.properties) {
          if ('graviteeFactor' === policySchema.properties[key].widget) {
            policySchema.properties[key]['x-schema-form'] = { type: 'select' };
            policySchema.properties[key].enum = [''];
            policySchema.properties[key]['x-schema-form'].titleMap = { '': 'None' };
            if (filteredFactors.length > 0) {
              policySchema.properties[key].enum = policySchema.properties[key].enum.concat(filteredFactors.map((f) => f.id));
              filteredFactors.forEach((obj) => {
                policySchema.properties[key]['x-schema-form'].titleMap[obj.id] = obj.name;
              });
            }
          }
        }
        policy.schema = JSON.stringify(policySchema);
      }
      return policy;
    });
  }
}
