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
import {NgForm} from '@angular/forms';
import {MatTableDataSource} from '@angular/material/table';
import {ActivatedRoute, Router} from '@angular/router';
import {SnackbarService} from '../../../../services/snackbar.service';
import {DialogService} from '../../../../services/dialog.service';
import {OrganizationService} from '../../../../services/organization.service';
import {AuthService} from '../../../../services/auth.service';

@Component({
  selector: 'app-settings-management-role',
  templateUrl: './role.component.html',
  styleUrls: ['./role.component.scss']
})
export class ManagementRoleComponent implements OnInit {
  @ViewChild('roleForm') form: NgForm;
  private deleteMode: boolean;
  allPermissions: MatTableDataSource<any>;
  role: any;
  formChanged = false;
  permissions: any[];
  readonly: boolean;

  constructor(private organizationService: OrganizationService,
              private snackbarService: SnackbarService,
              private route: ActivatedRoute,
              private router: Router,
              private authService: AuthService,
              private dialogService: DialogService) {
  }

  ngOnInit() {
    this.role = this.route.snapshot.data['role'];
    this.role.permissions = this.role.permissions || [];
    this.readonly = this.role.system || (!this.authService.hasPermissions(['organization_role_update']));
    this.deleteMode = this.authService.hasPermissions(['organization_role_delete']);
    this.initPermissionsDatasource();
  }

  private initPermissionsDatasource() {

    this.allPermissions = new MatTableDataSource(this.role.availablePermissions.map(p => ({
      permission: p,
      create: this.role.permissions.includes(p + '_create'),
      read: this.role.permissions.includes(p + '_read'),
      list: this.role.permissions.includes(p + '_list'),
      update: this.role.permissions.includes(p + '_update'),
      delete: this.role.permissions.includes(p + '_delete')
    })));
  }

  update() {
    this.role.permissions = this.allPermissions.data.map(p => {
      const perms = [];
      p.create === true ? perms.push(p.permission + '_create') : null;
      p.read === true ? perms.push(p.permission + '_read') : null;
      p.list === true ? perms.push(p.permission + '_list') : null;
      p.update === true ? perms.push(p.permission + '_update') : null;
      p.delete === true ? perms.push(p.permission + '_delete') : null;
      return perms;
    }).reduce((x, y) => x.concat(y), []);

    this.organizationService.updateRole(this.role.id, this.role).subscribe(data => {
      this.role = data;
      this.formChanged = false;
      this.initPermissionsDatasource();
      this.form.reset(this.role);
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
          this.organizationService.deleteRole(this.role.id).subscribe(() => {
            this.snackbarService.open('Role ' + this.role.name + ' deleted');
            this.router.navigate(['/settings', 'roles']);
          });
        }
      })
  }

  changePermissions(event) {
    this.allPermissions.data.forEach(p => p[event.source.value] = event.checked);
    this.formChanged = true;
  }

  hasPermissions(action) {
    return this.allPermissions.data.every(p => p[action] === true);
  }

  applyFilter(filterValue) {
    this.allPermissions.filter = filterValue.trim().toLowerCase();
  }
}
