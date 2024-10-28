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
import { Component, OnInit, ViewChild, ViewContainerRef } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { animate, style, transition, trigger } from '@angular/animations';
import { each } from 'lodash';

import { SnackbarService } from '../../../../services/snackbar.service';
import { UserService } from '../../../../services/user.service';
import { ProviderService } from '../../../../services/provider.service';
import { OrganizationService } from '../../../../services/organization.service';

import { UserClaimComponent } from './user-claim.component';
enum AccountType {
  User = 'user',
  ServiceAccount = 'service',
}
@Component({
  selector: 'user-creation',
  animations: [trigger('fadeInOut', [transition(':leave', [animate(500, style({ opacity: 0 }))])])],
  templateUrl: './user-creation.component.html',
  styleUrls: ['./user-creation.component.scss'],
})
export class UserCreationComponent implements OnInit {
  preRegistration = false;
  hidePassword = true;
  emailRequired = true;
  useEmailAsUsername = false;
  forceResetPassword = false;
  user: any = {};
  service: any = {};
  userClaims: any = {};
  userProviders: any[];
  selectedAccountType: AccountType = AccountType.User;
  protected readonly AccountType = AccountType;
  @ViewChild('dynamic', { read: ViewContainerRef }) viewContainerRef: ViewContainerRef;
  private domainId: string;

  constructor(
    private userService: UserService,
    private router: Router,
    private route: ActivatedRoute,
    private snackbarService: SnackbarService,
    private providerService: ProviderService,
    private organizationService: OrganizationService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;

    if (!this.isOrganizationUserAction()) {
      this.providerService.findUserProvidersByDomain(this.domainId).subscribe((response) => {
        this.userProviders = response;
      });
      this.userService.isEmailRequired().subscribe((response: boolean) => {
        this.emailRequired = response;
      });
    }
  }

  isOrganizationUserAction() {
    return !this.domainId;
  }

  create() {
    if (this.isServiceAccount()) {
      this.createOrganizationUser(this.service, true);
    } else {
      // set additional information
      if (this.userClaims && Object.keys(this.userClaims).length > 0) {
        const additionalInformation = {};
        each(this.userClaims, function (item) {
          additionalInformation[item.claimName] = item.claimValue;
        });
        this.user.additionalInformation = additionalInformation;
      }
      // set force reset
      this.user.forceResetPassword = this.forceResetPassword;
      // set pre-registration
      this.user.preRegistration = this.preRegistration;

      if (this.isOrganizationUserAction()) {
        this.createOrganizationUser(this.user, false);
      } else {
        this.userService.create(this.domainId, this.user).subscribe((data) => {
          this.snackbarService.open('User ' + data.username + ' created');
          this.router.navigate(['..', data.id], { relativeTo: this.route });
        });
      }
    }
  }

  createOrganizationUser(user: any, serviceAccount: boolean) {
    user.serviceAccount = serviceAccount;
    this.organizationService.createUser(user).subscribe((data) => {
      if (serviceAccount) {
        this.snackbarService.open('Service account ' + data.username + ' created');
      } else {
        this.snackbarService.open('User ' + data.username + ' created');
      }
      this.router.navigate(['..', data.id], { relativeTo: this.route });
    });
  }

  onEmailChange(email: string): void {
    if (this.useEmailAsUsername) {
      if (!email || email === '') {
        this.useEmailAsUsername = false;
        this.user.username = '';
      } else {
        this.user.username = email;
      }
    }
  }

  toggleUseEmailAsUsername(event: any): void {
    this.useEmailAsUsername = event.checked;
    this.user.username = this.useEmailAsUsername ? this.user.email : '';
  }

  addDynamicComponent(): void {
    const component = this.viewContainerRef.createComponent(UserClaimComponent);

    component.instance.addClaimChange.subscribe((claim) => {
      if (claim.name && claim.value) {
        this.userClaims[claim.id] = { claimName: claim.name, claimValue: claim.value };
      }
    });

    component.instance.removeClaimChange.subscribe((claim) => {
      delete this.userClaims[claim.id];
      this.viewContainerRef.remove(this.viewContainerRef.indexOf(component.hostView));
      if (claim.name && claim.value) {
        this.snackbarService.open('Claim ' + claim.name + ' deleted');
      }
    });
  }

  onAppSelectionChanged(event: any): void {
    this.user.client = event.id;
  }

  onAppDeleted(): void {
    this.user.client = null;
  }

  setForceResetPassword(e: any): void {
    this.forceResetPassword = e.checked;
  }

  selectAccountType(e: AccountType): void {
    this.selectedAccountType = e;
  }

  isServiceAccount(): boolean {
    return this.selectedAccountType === AccountType.ServiceAccount;
  }
}
