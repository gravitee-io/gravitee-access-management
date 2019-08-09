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

@Component({
  selector: 'app-group-roles',
  templateUrl: './roles.component.html',
  styleUrls: ['./roles.component.scss']
})
export class GroupRolesComponent implements OnInit {
  private domainId: string;
  private group: any;
  groupRoles: any[];

  constructor(private route: ActivatedRoute,
              private router: Router,
              private snackbarService: SnackbarService,
              private dialogService: DialogService,
              private groupService: GroupService,
              private dialog: MatDialog) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.parent.params['domainId'];
    this.group = this.route.snapshot.parent.data['group'];
    this.groupRoles = this.route.snapshot.data['roles'];
  }

  get isEmpty() {
    return !this.groupRoles || this.groupRoles.length == 0;
  }

  add() {
    let dialogRef = this.dialog.open(AddGroupRolesComponent, { width : '700px', data: { domain: this.domainId, assignedRoles: this.groupRoles }});
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
          this.groupService.revokeRole(this.domainId, this.group.id, role.id).subscribe(group => {
            this.group = group;
            this.route.snapshot.parent.data['group'] = group;
            this.snackbarService.open('Role ' + role.name + ' revoked');
            this.loadRoles();
          });
        }
      });
  }

  loadRoles() {
    this.groupService.roles(this.domainId, this.group.id).subscribe(roles => {
      this.groupRoles = roles;
    });
  }

  private assignRoles(roles) {
    this.groupService.assignRoles(this.domainId, this.group.id, roles).subscribe(group => {
      this.group = group;
      this.route.snapshot.parent.data['group'] = group;
      this.snackbarService.open("Role(s) assigned");
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
  roles: any[];
  initialSelectedRoles: any[];
  assignedRoles: string[] = [];
  hasChanged: boolean =  false;

  constructor(@Inject(MAT_DIALOG_DATA) public data: any,
              public dialogRef: MatDialogRef<AddGroupRolesComponent>,
              private roleService: RoleService) {
    this.domainId = data.domain;
    this.initialSelectedRoles = data.assignedRoles;
  }

  ngOnInit() {
    this.roleService.findByDomain(this.domainId).subscribe(roles => {
      this.roles = roles;
    });
  }

  onRoleSelectionChanges(selection) {
    this.assignedRoles = selection;
    this.hasChanged = true;
  }
}
