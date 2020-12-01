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
import {Component, OnInit, ViewChild} from '@angular/core';
import {OrganizationService} from "../../../services/organization.service";
import {DialogService} from "../../../services/dialog.service";
import {SnackbarService} from "../../../services/snackbar.service";
import {ActivatedRoute} from "@angular/router";
import {PolicyService} from "../../../services/policy.service";
import {CdkDragDrop, moveItemInArray} from "@angular/cdk/drag-drop";
import {NgForm} from "@angular/forms";
import {MatDialog, MatDialogRef} from "@angular/material";
import {AuthService} from "../../../services/auth.service";

@Component({
  selector: 'app-domain-policies',
  templateUrl: './policies.component.html',
  styleUrls: ['./policies.component.scss']
})
export class DomainSettingsPoliciesComponent implements OnInit {
  @ViewChild('policyForm', { static: false }) form: NgForm;
  private domainId: string;
  isLoading: boolean = false;
  policies: any[];
  policyPlugins: any[];
  policy: any;
  policySchema: any;
  policyConfiguration: any;
  updatePolicyConfiguration: any;
  configurationIsValid = true;
  configurationPristine = true;
  selectedPolicyId: string;
  readonly = false;
  extensionPoints: any = [
    {
      value: 'ROOT',
      stage: 'ROOT',
      expanded: true
    },
    {
      value: 'PRE_CONSENT',
      stage: 'PRE_CONSENT',
      expanded: false,
    },
    {
      value: 'POST_CONSENT',
      stage: 'POST_CONSENT',
      expanded: false
    }
  ];

  constructor(private organizationService: OrganizationService,
              private policyService: PolicyService,
              private dialogService: DialogService,
              private snackbarService: SnackbarService,
              private authService: AuthService,
              private route: ActivatedRoute,
              public dialog: MatDialog) {}

  ngOnInit(): void {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    this.policies = this.route.snapshot.data['policies'] || {};
    this.organizationService.policies().subscribe(data => this.policyPlugins = data);
    this.readonly = !this.authService.hasPermissions(['domain_extension_point_update']);
  }

  addPolicy(extensionPoint, policyId) {
    this.selectedPolicyId = null;
    this.isLoading = true;
    this.policy = {};
    this.policy.extensionPoint = extensionPoint;
    this.policy.type = policyId;
    this.policy.enabled = true;
    const policies = this.policies[extensionPoint];
    this.policy.order = policies && policies.length > 0 ? policies[policies.length -1].order + 1 : 0;
    this.policyConfiguration = {};
    if (this.form) {
      this.form.reset(this.policy);
    }
    this.loadPolicySchema(this.policy, 1500);
  }

  loadPolicy(event, policy) {
    event.preventDefault();
    this.selectedPolicyId = null;
    this.isLoading = true;
    this.policy = Object.assign({}, policy)
    this.policyConfiguration = JSON.parse(this.policy.configuration);
    this.loadPolicySchema(this.policy, 1500);
  }

  enablePolicyUpdate(configurationWrapper) {
    window.setTimeout(() => {
      this.configurationPristine = this.policy.configuration ?  (this.policy.configuration === JSON.stringify(configurationWrapper.configuration)) : Object.keys(configurationWrapper.configuration).length === 1;
      this.configurationIsValid = configurationWrapper.isValid;
      this.updatePolicyConfiguration = configurationWrapper.configuration;
    });
  }

  save() {
    this.policy.configuration = JSON.stringify(this.updatePolicyConfiguration);
    if (this.policy.id) {
      this.policyService.update(this.domainId, this.policy.id, this.policy).subscribe(data => {
        this.reloadPolicies();
        this.policy = data;
        this.policyConfiguration = JSON.parse(this.policy.configuration);
        this.selectedPolicyId = this.policy.id;
        this.snackbarService.open('Policy ' + data.name + ' updated');
      })
    } else {
      this.policyService.create(this.domainId, this.policy).subscribe(data => {
        this.reloadPolicies();
        this.policy = data;
        this.policyConfiguration = JSON.parse(this.policy.configuration);
        this.selectedPolicyId = this.policy.id;
        this.snackbarService.open('Policy ' + data.name + ' created');
      });
    }
  }

  getPolicies(extensionPoint) {
    return this.policies[extensionPoint];
  }

  drop(event: CdkDragDrop<any[]>, extensionPoint) {
    if (event.previousIndex !== event.currentIndex) {
      moveItemInArray(this.policies[extensionPoint], event.previousIndex, event.currentIndex);
      this.policies[extensionPoint].forEach(function (policy, i) {
        policy.order = i;
      });
      this.policyService.updateAll(this.domainId, this.policies[extensionPoint]).subscribe(() => {
        this.snackbarService.open('Policy\'s order changed');
      });
    }
  }

  enablePolicy(event, policy) {
    policy.enabled = event.checked;
    this.policyService.update(this.domainId, policy.id, policy).subscribe(data => {
      this.snackbarService.open('Policy ' + data.name + (policy.enabled ? ' enabled' : ' disabled'));
    });
  }

  deletePolicy(event, policy) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Policy', 'Are you sure you want to delete the policy ?')
      .subscribe(res => {
        if (res) {
          this.policyService.delete(this.domainId, policy.id).subscribe(() => {
            if (this.policy && policy.id === this.policy.id) {
              this.policy = null;
            }
            this.reloadPolicies();
            this.snackbarService.open('Policy deleted');
          });
        }
      });
  }

  clearPolicy() {
    this.policy = null;
    this.selectedPolicyId = null;
  }

  isPolicyEnabled(policy) {
    return policy.enabled;
  }

  noPolicies(extensionPoint) {
    const policies = this.getPolicies(extensionPoint);
    return !policies || policies.length === 0;
  }

  openDialog() {
    this.dialog.open(PoliciesInfoDialog, {});
  }

  private reloadPolicies() {
    this.policyService.findByDomain(this.domainId).subscribe(policies => this.policies = policies);
  }

  private loadPolicySchema(policy, delay) {
    let self = this;
    setTimeout(function() {
      self.organizationService.policySchema(policy.type).subscribe(data => {
        self.policySchema = data;
        if (policy.id) {
          self.selectedPolicyId = policy.id;
        }
        self.isLoading = false;
      });
    }, delay);
  }
}

@Component({
  selector: 'policies-info-dialog',
  templateUrl: './dialog/policies-info.component.html',
})
export class PoliciesInfoDialog {
  constructor(public dialogRef: MatDialogRef<PoliciesInfoDialog>) {}
}

