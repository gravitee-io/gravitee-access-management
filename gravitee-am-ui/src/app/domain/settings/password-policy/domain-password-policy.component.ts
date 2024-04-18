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
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, Observable } from 'rxjs';

import { AuthService } from '../../../services/auth.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { PasswordPolicyService } from '../../../services/password-policy.service';
import {
  DialogCallback,
  PasswordPoliciesIdpSelectDialogFactory,
} from '../password-policies/password-policies-idp-select-dialog/password-policies-idp-select-dialog.factory';
import { ProviderService } from '../../../services/provider.service';
import { IdpDataModel } from '../password-policies/password-policies-idp-select-dialog/password-policies-idp-select-dialog.component';

import { DomainPasswordPolicy } from './domain-password-policy.model';

@Component({
  selector: 'password-policy',
  templateUrl: './domain-password-policy.component.html',
  styleUrls: ['./domain-password-policy.component.scss'],
})
export class DomainPasswordPolicyComponent implements OnInit {
  @ViewChild('applicationForm') form: any;
  private domainId: string;
  formChanged = false;
  idpUpdated: boolean = false;
  editMode: boolean;
  policyId: string;
  idpToUpdate = new Map<string, boolean>();

  @Input() passwordPolicy: DomainPasswordPolicy;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService,
    private snackbarService: SnackbarService,
    private passwordPolicyService: PasswordPolicyService,
    private dialogFactory: PasswordPoliciesIdpSelectDialogFactory,
    private providerService: ProviderService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain'].id;
    const policy = this.route.snapshot.data['policy'];
    if (policy) {
      this.refreshPolicyData(policy);
    } else {
      this.passwordPolicy = {};
    }
    this.editMode = this.authService.hasPermissions(['domain_settings_update']);
  }

  formChange(): void {
    this.formChanged = true;
  }

  setIncludeNumbers(e) {
    this.passwordPolicy.includeNumbers = e.checked;
  }

  setIncludeSpecialCharacters(e) {
    this.passwordPolicy.includeSpecialCharacters = e.checked;
  }

  setLettersInMixedValue(e) {
    this.passwordPolicy.lettersInMixedCase = e.checked;
  }

  setExcludePasswordsInDictionary(e) {
    this.passwordPolicy.excludePasswordsInDictionary = e.checked;
  }

  setExcludeUserProfileInfoInPassword(e) {
    this.passwordPolicy.excludeUserProfileInfoInPassword = e.checked;
  }

  setPasswordHistoryEnabled(e) {
    this.passwordPolicy.passwordHistoryEnabled = e.checked;
  }

  save() {
    if (
      this.passwordPolicy.passwordHistoryEnabled &&
      (!this.passwordPolicy.oldPasswords || this.passwordPolicy.oldPasswords < 1 || this.passwordPolicy.oldPasswords > 24)
    ) {
      this.snackbarService.open('Number of old passwords must be within the range [1, 24]');
      return;
    }
    if (this.passwordPolicy.minLength && this.passwordPolicy.minLength <= 0) {
      this.snackbarService.open('Min length must be greater than zero and smaller than Max length');
      return;
    }

    if (this.passwordPolicy.maxLength && this.passwordPolicy.maxLength <= 0) {
      this.snackbarService.open('Max length must be greater than zero');
      return;
    }

    let request: Observable<any>;
    if (this.policyId) {
      request = this.passwordPolicyService.update(this.domainId, this.policyId, this.passwordPolicy);
    } else {
      request = this.passwordPolicyService.create(this.domainId, this.passwordPolicy);
    }
    request.subscribe((response) => {
      this.refreshPolicyData({ ...response });
      this.form.reset(this.passwordPolicy);
      this.formChanged = false;
      this.snackbarService.open('Password settings configuration updated');
      if (this.idpUpdated) {
        this.updateIdps();
      }
      this.router.navigate(['..', response.id], { relativeTo: this.route });
    });
  }

  private refreshPolicyData(policy) {
    this.policyId = policy.id;
    delete policy['id'];
    delete policy['referenceId'];
    delete policy['referenceType'];
    delete policy['createdAt'];
    delete policy['updatedAt'];
    this.passwordPolicy = { ...policy };
  }

  private getIdentityProviderDetails(type: string) {
    const identities = this.route.snapshot.data['identities'];
    if (identities && identities[type]) {
      return identities[type];
    }
  }

  private updateIdps() {
    const observables: Observable<any>[] = [];
    this.idpToUpdate.forEach((checked: boolean, id: string) => {
      observables.push(this.providerService.assignPasswordPolicy(this.domainId, id, checked ? this.policyId : undefined));
    });
    forkJoin(observables).subscribe({
      complete: () => {
        this.idpUpdated = false;
      },
    });
  }

  private constructDataModel(idp: any): IdpDataModel {
    return {
      id: idp.id,
      name: idp.name,
      selected: this.determinateSelected(idp),
      type: {
        name: this.getIdentityProviderDetails(idp.type)?.displayName,
        icon: this.getIdentityProviderDetails(idp.type)?.icon,
      },
    } as IdpDataModel;
  }

  determinateSelected(idp: any): boolean {
    if (this.idpToUpdate.has(idp.id)) {
      return this.idpToUpdate.get(idp.id);
    }
    if (this.policyId) {
      return idp.passwordPolicy === this.policyId;
    }
    return false;
  }

  public openDialog(): void {
    this.providerService.findUserProvidersByDomain(this.domainId).subscribe((providers) => {
      const unlinked = providers
        .filter((idp) => idp.passwordPolicy === undefined || idp.passwordPolicy === this.policyId)
        .map((idp) => this.constructDataModel(idp));
      const callback: DialogCallback = (result: Map<string, boolean>) => {
        if (result && result.size > 0) {
          result.forEach((value: boolean, key: string) => {
            this.idpToUpdate.set(key, value);
          });
          this.idpUpdated = true;
          this.formChanged = true;
        }
      };
      this.dialogFactory.openDialog(unlinked, callback);
    });
  }
}
