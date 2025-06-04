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
import { Component, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatInput } from '@angular/material/input';
import { filter, switchMap, tap } from 'rxjs/operators';
import { difference, find, map, remove } from 'lodash';

import { RoleService } from '../../../../services/role.service';
import { SnackbarService } from '../../../../services/snackbar.service';
import { DialogService } from '../../../../services/dialog.service';
import { AuthService } from '../../../../services/auth.service';

export interface Scope {
  id: string;
  key: string;
  name: string;
}

@Component({
  selector: 'app-role',
  templateUrl: './role.component.html',
  styleUrls: ['./role.component.scss'],
  standalone: false,
})
export class RoleComponent implements OnInit {
  @ViewChild('chipInput', { static: true }) chipInput: MatInput;
  private domainId: string;
  scopes: Scope[];
  selectedPermissions: Scope[];
  role: any;
  formChanged = false;
  editMode: boolean;

  constructor(
    private roleService: RoleService,
    private snackbarService: SnackbarService,
    private route: ActivatedRoute,
    private router: Router,
    private dialogService: DialogService,
    private authService: AuthService,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.role = this.route.snapshot.data['role'];
    this.scopes = this.route.snapshot.data['scopes'];
    this.editMode = this.authService.hasPermissions(['domain_role_update']);

    if (!this.role.permissions) {
      this.role.permissions = [];
    }

    this.initScopes();
  }

  initScopes() {
    // Merge with existing scope
    this.selectedPermissions = map(this.role.permissions, (permission) => find(this.scopes, { key: permission }));
    this.scopes = difference(this.scopes, this.selectedPermissions);
  }

  update() {
    this.role.permissions = map(this.selectedPermissions, (permission) => permission.key);
    this.roleService.update(this.domainId, this.role.id, this.role).subscribe((data) => {
      this.role = data;
      this.snackbarService.open('Role updated');
    });
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Role', 'Are you sure you want to delete this role ?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.roleService.delete(this.domainId, this.role.id)),
        tap(() => {
          this.snackbarService.open('Role ' + this.role.name + ' deleted');
          this.router.navigate(['..'], { relativeTo: this.route });
        }),
      )
      .subscribe();
  }

  addPermission(event) {
    this.selectedPermissions = this.selectedPermissions.concat(remove(this.scopes, { key: event.option.value }));
    this.chipInput['nativeElement'].blur();
    this.formChanged = true;
  }

  removePermission(permission) {
    this.scopes = this.scopes.concat(
      remove(this.selectedPermissions, function (selectPermission) {
        return selectPermission.key === permission.key;
      }),
    );

    this.chipInput['nativeElement'].blur();
    this.formChanged = true;
  }
}
