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
import {Component, OnInit} from '@angular/core';
import {AppConfig} from "../../../../../config/app.config";
import {RoleService} from "../../../../services/role.service";
import {SnackbarService} from "../../../../services/snackbar.service";
import {ActivatedRoute, Router} from "@angular/router";
import {BreadcrumbService} from "../../../../../libraries/ng2-breadcrumb/components/breadcrumbService";
import {DialogService} from "../../../../services/dialog.service";
import {PlatformService} from "../../../../services/platform.service";
import {AuthService} from "../../../../services/auth.service";

@Component({
  selector: 'app-settings-management-role',
  templateUrl: './role.component.html',
  styleUrls: ['./role.component.scss']
})
export class ManagementRoleComponent implements OnInit {
  private createPermissions: any[];
  private readPermissions: any[];
  private updatePermissions: any[];
  private deletePermissions: any[];
  private deleteMode: boolean;
  role: any;
  formChanged = false;
  permissions: any[];
  readonly: boolean;

  constructor(private platformService: PlatformService,
              private snackbarService: SnackbarService,
              private route: ActivatedRoute,
              private router: Router,
              private breadcrumbService: BreadcrumbService,
              private authService: AuthService,
              private dialogService: DialogService) { }

  ngOnInit() {
    this.role = this.route.snapshot.data['role'];
    this.role.permissions = this.role.permissions || [];
    this.readonly = !this.authService.isAdmin() && !this.authService.hasPermissions(['management_role_update']);
    this.deleteMode = this.authService.isAdmin() || this.authService.hasPermissions(['management_role_delete']);
    this.initPermissions();
  }

  update() {
    this.platformService.updateRole(this.role.id, this.role).subscribe(data => {
      this.role = data;
      this.formChanged = false;
      this.snackbarService.open('Role updated');
    });
  }

  canDelete(): boolean {
    return !this.role.system && this.deleteMode;
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Role', 'Are you sure you want to delete this role ?')
      .subscribe(res => {
        if (res) {
          this.platformService.deleteRole(this.role.id).subscribe(() => {
            this.snackbarService.open('Role ' + this.role.name + ' deleted');
            this.router.navigate(['/settings', 'management', 'roles']);
          });
        }
      })
  }

  changePermission(event) {
    let permission = event.source.value;
    if (event.checked) {
      if (!this.hasPermission(permission)) {
        this.role.permissions.push(permission);
      }
    } else {
      this.role.permissions.splice(this.role.permissions.indexOf(permission), 1);
    }
    this.formChanged = true;
  }

  hasPermission(permission) {
    return this.role.permissions.indexOf(permission) != -1;
  }

  changePermissions(event) {
    let action = event.source.value;
    let array = this.getPermissions(action);
    if (event.checked) {
      array.forEach(p => {
        if (!this.hasPermission(p)) {
          this.role.permissions.push(p);
        }
      });
    } else {
      this.role.permissions = this.role.permissions.filter(p => !array.includes(p));
    }
    this.formChanged = true;
  }

  hasPermissions(action) {
    let array = this.getPermissions(action);
    return array.every(elem => this.role.permissions.indexOf(elem) > -1);
  }

  private initPermissions() {
    this.createPermissions = this.role.availablePermissions.map(p => p + '_create');
    this.readPermissions = this.role.availablePermissions.map(p => p + '_read');
    this.updatePermissions = this.role.availablePermissions.map(p => p + '_update');
    this.deletePermissions = this.role.availablePermissions.map(p => p + '_delete');
  }

  private getPermissions(action) {
    let array = [];
    switch (action) {
      case 'create':
        array = this.createPermissions;
        break;
      case 'read':
        array = this.readPermissions;
        break;
      case 'update':
        array = this.updatePermissions;
        break;
      case 'delete':
        array = this.deletePermissions;
    }
    return array;
  }
}
