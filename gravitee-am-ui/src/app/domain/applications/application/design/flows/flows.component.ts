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
import _ from 'lodash';

import { OrganizationService } from '../../../../../services/organization.service';
import { SnackbarService } from '../../../../../services/snackbar.service';
import { ApplicationService } from '../../../../../services/application.service';
import { DialogService } from '../../../../../services/dialog.service';

@Component({
  selector: 'app-application-flows',
  templateUrl: './flows.component.html',
  styleUrls: ['./flows.component.scss'],
})
export class ApplicationFlowsComponent implements OnInit {
  private domainId: string;
  private application: any;
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
    private snackbarService: SnackbarService,
    private dialogService: DialogService,
  ) {}

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = this.route.snapshot.data['application'];
    this.flowSchema = this.route.snapshot.data['flowSettingsForm'];
    this.definition.flows = this.route.snapshot.data['flows'] || [];
    this.initPolicies();
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
    this.application = this.route.snapshot.data['application'];
    this.flowSchema = this.route.snapshot.data['flowSettingsForm'];
    this.definition = {
      flows: _.cloneDeep(this.route.snapshot.data['flows'] || []),
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

    this.applicationService.updateFlows(this.domainId, this.application.id, flows).subscribe((updatedFlows) => {
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
        switchMap(() =>
          this.applicationService.patch(this.domainId, this.application.id, {
            settings: {
              advanced: {
                flowsInherited: event.checked,
              },
            },
          }),
        ),
        tap((data) => {
          this.application = data;
          this.route.snapshot.parent.parent.data['application'] = this.application;
          this.snackbarService.open('Application updated');
        }),
      )
      .subscribe();
  }

  isInherited() {
    return this.application?.settings?.advanced?.flowsInherited;
  }

  private initPolicies() {
    const factors = this.route.snapshot.data['factors'] || [];
    const appFactorIds = this.application.factors || [];
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
