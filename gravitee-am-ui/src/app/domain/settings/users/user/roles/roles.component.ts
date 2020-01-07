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
import {Component, Inject, OnInit} from '@angular/core';
import {MAT_DIALOG_DATA, MatDialog, MatDialogRef} from "@angular/material";
import {ActivatedRoute, Router} from "@angular/router";
import {SnackbarService} from "../../../../../services/snackbar.service";
import {DialogService} from "../../../../../services/dialog.service";
import {UserService} from "../../../../../services/user.service";
import {RoleService} from "../../../../../services/role.service";
import {PlatformService} from "../../../../../services/platform.service";
import {AuthService} from "../../../../../services/auth.service";

@Component({
  selector: 'app-user-roles',
  templateUrl: './roles.component.html',
  styleUrls: ['./roles.component.scss']
})
export class UserRolesComponent implements OnInit {
  private domainId: string;
  private user: any;
  private adminContext = false;
  userRoles: any[];
  editMode: boolean;

  constructor(private route: ActivatedRoute,
              private router: Router,
              private snackbarService: SnackbarService,
              private dialogService: DialogService,
              private userService: UserService,
              private platformService: PlatformService,
              private authService: AuthService,
              private dialog: MatDialog) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.parent.params['domainId'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.adminContext = true;
      this.editMode = this.authService.isAdmin() || this.authService.hasPermissions(['management_user_update']);
    } else {
      this.editMode = this.authService.isAdmin() || this.authService.hasPermissions(['domain_user_update']);
    }
    this.user = this.route.snapshot.parent.data['user'];
    this.userRoles = this.route.snapshot.data['roles'];
  }

  get isEmpty() {
    return !this.userRoles || this.userRoles.length === 0;
  }

  add() {
    let dialogRef = this.dialog.open(AddUserRolesComponent, { width : '700px', data: { domain: this.domainId, adminContext: this.adminContext, assignedRoles: this.userRoles }});
    dialogRef.afterClosed().subscribe(roles => {
      if (roles) {
          this.assignRoles(roles);
      }
    });
  }

  revoke(event, role) {
    event.preventDefault();
    this.dialogService
      .confirm('Revoke role', 'Are you sure you want to remove role from the user ?')
      .subscribe(res => {
        if (res) {
          this.userService.revokeRole(this.domainId, this.user.id, role.id, this.adminContext).subscribe(user => {
            this.user = user;
            this.route.snapshot.parent.data['user'] = user;
            this.snackbarService.open('Role ' + role.name + ' revoked');
            this.loadRoles();
          });
        }
      });
  }

  loadRoles() {
    const userRolesCall = this.adminContext ? this.platformService.userRoles(this.user.id) : this.userService.roles(this.domainId, this.user.id);
    userRolesCall.subscribe(roles => {
      this.userRoles = roles;
    });
  }

  private assignRoles(roles) {
    this.userService.assignRoles(this.domainId, this.user.id, roles, this.adminContext).subscribe(user => {
      this.user = user;
      this.route.snapshot.parent.data['user'] = user;
      this.snackbarService.open('Role(s) assigned');
      this.loadRoles();
    });
  }
}

@Component({
  selector: 'add-user-roles',
  templateUrl: './add/add-user-roles.component.html',
})
export class AddUserRolesComponent implements OnInit {
  private domainId: string;
  private adminContext: boolean;
  roles: any[];
  initialSelectedRoles: any[];
  assignedRoles: string[] = [];
  hasChanged: boolean =  false;

  constructor(@Inject(MAT_DIALOG_DATA) public data: any,
              public dialogRef: MatDialogRef<AddUserRolesComponent>,
              private roleService: RoleService,
              private platformService: PlatformService) {
    this.domainId = data.domain;
    this.adminContext = data.adminContext;
    this.initialSelectedRoles = data.assignedRoles;
  }

  ngOnInit() {
    if (this.adminContext) {
      this.platformService.roles('MANAGEMENT').subscribe(roles => {
        this.roles = roles;
      })
    } else {
      this.roleService.findByDomain(this.domainId).subscribe(roles => {
        this.roles = roles;
      });
    }
  }

  onRoleSelectionChanges(selection) {
    this.assignedRoles = selection;
    this.hasChanged = true;
  }
}
