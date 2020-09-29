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
import {ActivatedRoute, Router} from "@angular/router";
import {SnackbarService} from "../../../../../services/snackbar.service";
import {DialogService} from "../../../../../services/dialog.service";
import {MAT_DIALOG_DATA, MatDialog, MatDialogRef} from "@angular/material";
import {RoleService} from "../../../../../services/role.service";
import {GroupService} from "../../../../../services/group.service";
import {OrganizationService} from "../../../../../services/organization.service";
import {AuthService} from "../../../../../services/auth.service";

@Component({
  selector: 'app-group-roles',
  templateUrl: './roles.component.html',
  styleUrls: ['./roles.component.scss']
})
export class GroupRolesComponent implements OnInit {
  private domainId: string;
  private group: any;
  private organizationContext: any;
  groupRoles: any[];
  editMode: boolean;

  constructor(private route: ActivatedRoute,
              private router: Router,
              private snackbarService: SnackbarService,
              private dialogService: DialogService,
              private groupService: GroupService,
              private organizationService: OrganizationService,
              private authService: AuthService,
              private dialog: MatDialog) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.params['domainId'];
    this.group = this.route.snapshot.data['group'];
    this.groupRoles = this.route.snapshot.data['roles'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.organizationContext = true;
      this.editMode = this.authService.hasPermissions(['organization_group_update']);
    } else {
      this.editMode = this.authService.hasPermissions(['domain_group_update']);
    }
  }

  get isEmpty() {
    return !this.groupRoles || this.groupRoles.length === 0;
  }

  add() {
    let dialogRef = this.dialog.open(AddGroupRolesComponent, { width : '700px', data: { domain: this.domainId, organizationContext: this.organizationContext, assignedRoles: this.groupRoles }});
    dialogRef.afterClosed().subscribe(roles => {
      if (roles) {
          this.assignRoles(roles);
      }
    });
  }

  revoke(event, role) {
    event.preventDefault();
    this.dialogService
      .confirm('Revoke role', 'Are you sure you want to remove role from this group ?')
      .subscribe(res => {
        if (res) {
          this.groupService.revokeRole(this.domainId, this.group.id, role.id, this.organizationContext).subscribe(group => {
            this.group = group;
            this.route.snapshot.data['group'] = group;
            this.snackbarService.open('Role ' + role.name + ' revoked');
            this.loadRoles();
          });
        }
      });
  }

  loadRoles() {
    if (this.organizationContext) {
      this.organizationService.groupRoles(this.group.id).subscribe(roles => {
        this.groupRoles = roles;
      });
    } else {
      this.groupService.roles(this.domainId, this.group.id).subscribe(roles => {
        this.groupRoles = roles;
      });
    }
  }

  private assignRoles(roles) {
    this.groupService.assignRoles(this.domainId, this.group.id, roles, this.organizationContext).subscribe(group => {
      this.group = group;
      this.route.snapshot.data['group'] = group;
      this.snackbarService.open('Role(s) assigned');
      this.loadRoles();
    });
  }
}

@Component({
  selector: 'add-group-roles',
  templateUrl: './add/add-group-roles.component.html',
})
export class AddGroupRolesComponent implements OnInit {
  private domainId: string;
  private organizationContext = false;
  roles: any[];
  initialSelectedRoles: any[];
  assignedRoles: string[] = [];
  hasChanged: boolean =  false;

  constructor(@Inject(MAT_DIALOG_DATA) public data: any,
              public dialogRef: MatDialogRef<AddGroupRolesComponent>,
              private roleService: RoleService,
              private organizationService: OrganizationService) {
    this.domainId = data.domain;
    this.organizationContext = data.organizationContext;
    this.initialSelectedRoles = data.assignedRoles;
  }

  ngOnInit() {
    if (this.organizationContext) {
      this.organizationService.roles('MANAGEMENT').subscribe(roles => {
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
