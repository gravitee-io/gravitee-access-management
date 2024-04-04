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

import { PasswordPoliciesService } from '../../../services/password-policies.service';

import { PasswordPolicy } from './domain-password-policies.model';
import {
  DialogCallback,
  PasswordPoliciesIdpSelectDialogFactory,
} from './password-policies-idp-select-dialog/password-policies-idp-select-dialog.factory';
import { IdpDataModel } from './password-policies-idp-select-dialog/password-policies-idp-select-table/password-policies-idp-select-table.component';


@Component({
  selector: 'domain-password-policies',
  templateUrl: './domain-password-policies.component.html',
  styleUrls: ['./domain-password-policies.component.scss'],
})
export class PasswordPoliciesComponent implements OnInit {
  domain: any = {};

  constructor(
    private route: ActivatedRoute,
    private passwordPoliciesService: PasswordPoliciesService,
    public dialogFactory: PasswordPoliciesIdpSelectDialogFactory,
  ) {}
  rows: PasswordPolicy[] = [];

  ngOnInit() {
    this.domain = this.route.snapshot.data['domain'];
    this.loadPasswordPolicies();
  }

  isEmpty(): boolean {
    return this.rows.length === 0;
  }

  private loadPasswordPolicies() {
    this.passwordPoliciesService.getAll(this.domain.id).subscribe((policies) => {
      this.rows = policies;
    });
  }

  private getDomainIdentityProviders(): any[] {
    const providers = this.route.snapshot.data['providers'] as any[];
    return providers.map((provider) => {
      return {
        id: provider.id,
        name: provider.name,
        typeName: this.getIdentityProviderDetails(provider.type)?.displayName,
        typeIcon: this.getIdentityProviderDetails(provider.type)?.icon,
      };
    });
  }

  private getIdentityProviderDetails(type: string) {
    const identities = this.route.snapshot.data['identities'];
    if (identities && identities[type]) {
      return identities[type];
    }
    return null;
  }

  public openDialog() {
    const idps = this.getDomainIdentityProviders();
    const unlinked = idps.map((idp) => {
      return {
        id: idp.id,
        name: idp.name,
        association: null,
        type: {
          name: idp.typeName,
          icon: idp.typeIcon,
        },
      } as IdpDataModel;
    });
    const linked = idps.map((idp) => {
      return {
        id: idp.id,
        name: idp.name,
        association: 'Password Policy',
        type: {
          name: idp.typeName,
          icon: idp.typeIcon,
        },
      } as IdpDataModel;
    });
    const callback: DialogCallback = (result) => {
      console.log(result);
    };
    this.dialogFactory.openDialog(
      {
        unlinkedIdps: unlinked,
        linkedIdps: linked,
      },
      callback,
    );
  }
}
