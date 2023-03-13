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
import {Component, ComponentFactoryResolver, OnInit, ViewChild, ViewContainerRef} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {SnackbarService} from '../../../../../services/snackbar.service';
import {DialogService} from '../../../../../services/dialog.service';
import {UserService} from '../../../../../services/user.service';
import {UserClaimComponent} from '../../creation/user-claim.component';
import {AuthService} from '../../../../../services/auth.service';
import {NgForm} from '@angular/forms';

@Component({
  selector: 'app-user-profile',
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.scss']
})
export class UserProfileComponent implements OnInit {
  private domainId: string;
  organizationContext: boolean;
  @ViewChild('passwordForm') passwordForm: any;
  @ViewChild('usernameForm') usernameForm: NgForm;
  @ViewChild('dynamic', { read: ViewContainerRef }) viewContainerRef: ViewContainerRef;
  user: any;
  userClaims: any = {};
  password: any;
  formChanged = false;
  canEdit: boolean;
  canDelete: boolean;

  constructor(private route: ActivatedRoute,
              private router: Router,
              private snackbarService: SnackbarService,
              private dialogService: DialogService,
              private userService: UserService,
              private authService: AuthService,
              private factoryResolver: ComponentFactoryResolver) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.organizationContext = true;
      this.canEdit = this.authService.hasPermissions(['organization_user_update']);
      this.canDelete = this.authService.hasPermissions(['organization_user_delete']);
    } else {
      this.canEdit = this.authService.hasPermissions(['domain_user_update']);
      this.canDelete = this.authService.hasPermissions(['domain_user_delete']);
    }
    this.user = this.route.snapshot.data['user'];
  }

  update() {
    // TODO we should be able to update platform users
    this.user.additionalInformation = this.user.additionalInformation || {};
    Object.keys(this.userClaims).forEach(key => this.user.additionalInformation[key] = this.userClaims[key]);
    this.user.displayName = [this.user.firstName, this.user.lastName].filter(Boolean).join(" ");
    this.userService.update(this.domainId, this.user.id, this.user, this.organizationContext).subscribe(data => {
      this.user = data;
      this.userClaims = {};
      this.viewContainerRef.clear();
      this.formChanged = false;
      this.snackbarService.open('User updated');
    });
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete User', 'Are you sure you want to delete this user ?')
      .subscribe(res => {
        if (res) {
          this.userService.delete(this.domainId, this.user.id, this.organizationContext).subscribe(response => {
            this.snackbarService.open('User ' + this.user.username + ' deleted');
            this.router.navigate(['../..'], { relativeTo: this.route });
          });
        }
      });
  }

  resendConfirmationRegistration(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Send Email', 'Are you sure you want to send the confirmation registration email ?')
      .subscribe( res => {
        if (res) {
          this.userService.resendRegistrationConfirmation(this.domainId, this.user.id).subscribe(() => {
            this.snackbarService.open('Email sent');
          });
        }
      });
  }

  resetPassword() {
    this.dialogService
      .confirm('Reset Password', 'Are you sure you want to reset the password ?')
      .subscribe(res => {
        if (res) {
          this.userService.resetPassword(this.domainId, this.user.id, this.password, this.organizationContext).subscribe(() => {
            this.password = null;
            this.passwordForm.reset();
            // reset the errors of all the controls
            for (let name in this.passwordForm.controls) {
              this.passwordForm.controls[name].setErrors(null);
            }
            this.snackbarService.open('Password reset');
          });
        }
      });
  }

  unlock(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Unlock User', 'Are you sure you want to unlock the user ?')
      .subscribe(res => {
        if (res) {
          this.userService.unlock(this.domainId, this.user.id).subscribe(() => {
            this.unlockUser(this.user);
            this.snackbarService.open('User unlocked');
          });
        }
      });
  }

  isOrganizationUserAction() {
    return this.organizationContext;
  }

  editMode() {
    return this.user.internal;
  }

  isEmptyObject(obj) {
    return (obj && (Object.keys(obj).length === 0));
  }

  enableUser(event) {
    // TODO we should be able to update platform users
    this.dialogService
      .confirm( (event.checked ? 'Enable' : 'Disable') + ' User', 'Are you sure you want to ' + (event.checked ? 'enable' : 'disable') + ' the user ?')
      .subscribe(res => {
        if (res) {
          this.userService.updateStatus(this.domainId, this.user.id, event.checked).subscribe(() => {
            this.user.enabled = event.checked;
            this.snackbarService.open('User ' + (event.checked ? 'enabled' : 'disabled'));
          });
        } else {
          event.source.checked = true;
        }
      });
  }

  asObject(value) {
    return value && typeof value === 'object'
  }

  isUserEnabled() {
    return this.user.enabled;
  }

  addDynamicComponent() {
    const factory = this.factoryResolver.resolveComponentFactory(UserClaimComponent);
    const component = this.viewContainerRef.createComponent(factory);

    const that = this;
    component.instance.addClaimChange.subscribe(claim => {
      if (claim.name && claim.value) {
        that.userClaims[claim.name] = claim.value;
        this.formChanged = true;
      }
    });

    component.instance.removeClaimChange.subscribe(claim => {
      that.viewContainerRef.remove(that.viewContainerRef.indexOf(component.hostView));
      if (claim.name && claim.value) {
        this.formChanged = true;
      }
    });
  }

  removeExistingClaim(claimKey, event) {
    event.preventDefault();
    const that = this;
    this.user.additionalInformation = Object.keys(this.user.additionalInformation).reduce(function (obj, key) {
      if (key !== claimKey) {
        obj[key] = that.user.additionalInformation[key];
      }
      return obj;
    }, {});
    this.formChanged = true;
  }

  onAppSelectionChanged(event) {
    this.user.client = event.id;
    this.formChanged = true;
  }

  onAppDeleted(event) {
    this.user.client = null;
    this.formChanged = true;
  }

  displayClientName() {
    return this.user.applicationEntity != null ? this.user.applicationEntity.name : this.user.client;
  }

  accountLocked(user) {
    return !user.accountNonLocked && (user.accountLockedUntil === null || !user.accountLockedUntil || user.accountLockedUntil > new Date());
  }

  updateUsername() {
    this.dialogService
      .confirm('Update Username', 'Are you sure you want to update this username?')
      .subscribe(res => {
        if (res) {
          this.userService.updateUsername(this.domainId, this.user.id, this.organizationContext, this.user.username).subscribe(() => {
            this.usernameForm.resetForm({ username: this.user.username });
            this.unlockUser(this.user);
            this.snackbarService.open('Username updated');
          })
        }
      });
  }

  private unlockUser(user) {
    user.accountNonLocked = true;
    user.accountLockedAt = null;
    user.accountLockedUntil = null;
  }
}
