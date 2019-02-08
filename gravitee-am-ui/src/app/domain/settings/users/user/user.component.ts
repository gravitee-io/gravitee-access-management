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
import { Component, ComponentFactoryResolver, OnInit, ViewChild, ViewContainerRef } from '@angular/core';
import { BreadcrumbService } from "../../../../../libraries/ng2-breadcrumb/components/breadcrumbService";
import { ActivatedRoute, Router } from "@angular/router";
import { AppConfig } from "../../../../../config/app.config";
import { SnackbarService} from "../../../../services/snackbar.service";
import { UserService } from "../../../../services/user.service";
import { DialogService } from "../../../../services/dialog.service";
import { UserClaimComponent } from "../creation/user-claim.component";
import * as _ from 'lodash';

@Component({
  selector: 'app-user',
  templateUrl: './user.component.html',
  styleUrls: ['./user.component.scss']
})
export class UserComponent implements OnInit {
  private domainId: string;
  private adminContext: boolean;
  @ViewChild('userForm') form: any;
  @ViewChild('passwordForm') passwordForm: any;
  @ViewChild('dynamic', { read: ViewContainerRef }) viewContainerRef: ViewContainerRef;
  user: any;
  userClaims: any = {};
  userAdditionalInformation: any = {};
  password: any;
  formChanged: boolean = false;

  constructor(private route: ActivatedRoute,
              private router: Router,
              private breadcrumbService: BreadcrumbService,
              private snackbarService: SnackbarService,
              private dialogService: DialogService,
              private userService: UserService,
              private factoryResolver: ComponentFactoryResolver) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.domainId = AppConfig.settings.authentication.domainId;
      this.adminContext = true;
    }
    this.user = this.route.snapshot.data['user'];
    this.userAdditionalInformation = Object.assign({}, this.user.additionalInformation);
    this.initBreadcrumb();
  }

  initBreadcrumb() {
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/settings/users/'+this.user.id+'$', this.user.username);
  }

  update() {
    // set additional information
    if (this.userClaims && Object.keys(this.userClaims).length > 0) {
      let additionalInformation = this.userAdditionalInformation;
      _.each(this.userClaims, function(item) {
        additionalInformation[item.claimName] = item.claimValue;
      });
      this.user.additionalInformation = additionalInformation;
    }

    this.userService.update(this.domainId, this.user.id, this.user).map(res => res.json()).subscribe(data => {
      this.user = data;
      this.userAdditionalInformation = Object.assign({}, this.user.additionalInformation);
      this.userClaims = {};
      this.viewContainerRef.clear();
      this.initBreadcrumb();
      this.form.reset(this.user);
      this.formChanged = false;
      this.snackbarService.open("User updated");
    });
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete User', 'Are you sure you want to delete this user ?')
      .subscribe(res => {
        if (res) {
          this.userService.delete(this.domainId, this.user.id).subscribe(response => {
            this.snackbarService.open('User '+ this.user.username + ' deleted');
            this.router.navigate(['/domains', this.domainId, 'settings', 'users']);
          });
        }
      });
  }

  resendConfirmationRegistration() {
    this.userService.resendRegistrationConfirmation(this.domainId, this.user.id).map(res => res.json()).subscribe(() => {
      this.snackbarService.open("Email sent");
    });
  }

  resetPassword() {
    this.userService.resetPassword(this.domainId, this.user.id, this.password).map(res => res.json()).subscribe(() => {
      this.password = null;
      this.passwordForm.reset();
      // reset the errors of all the controls
      for (let name in this.passwordForm.controls) {
        this.passwordForm.controls[name].setErrors(null);
      }
      this.snackbarService.open("Password reset");
    });
  }

  editMode() {
    return this.user.internal && !this.adminContext;
  }

  isEmptyObject(obj) {
    return (obj && (Object.keys(obj).length === 0));
  }

  enableUser(event) {
    this.user.enabled = event.checked;
    this.formChanged = true;
  }

  isUserEnabled() {
    return this.user.enabled;
  }

  addDynamicComponent() {
    const factory = this.factoryResolver.resolveComponentFactory(UserClaimComponent);
    const component = this.viewContainerRef.createComponent(factory);

    let that = this;
    component.instance.addClaimChange.subscribe(claim => {
      if (claim.name && claim.value) {
        that.userClaims[claim.id] = {'claimName': claim.name, 'claimValue': claim.value};
        this.formChanged = true;
      }
    });

    component.instance.removeClaimChange.subscribe(claim => {
      delete that.userClaims[claim.id];
      that.viewContainerRef.remove(that.viewContainerRef.indexOf(component.hostView));
      if (claim.name && claim.value) {
        that.snackbarService.open('Claim ' + claim.name + ' deleted');
        this.formChanged = true;
      }
    });
  }

  removeExistingClaim(claim, event) {
    event.preventDefault();
    delete this.user.additionalInformation[claim];
    this.userAdditionalInformation = Object.assign({}, this.user.additionalInformation);
    this.formChanged = true;
  }

  onClientSelectionChanged(event) {
    this.user.client = event.id;
  }

  onClientDeleted(event) {
    this.user.client = null;
  }

  displayClientName() {
    if (this.user.clientEntity != null) {
      return (this.user.clientEntity.clientName) ? this.user.clientEntity.clientName : this.user.clientEntity.clientId;
    }
    return this.user.client;
  }
}
