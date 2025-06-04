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
import { ActivatedRoute } from '@angular/router';
import { filter, switchMap, tap } from 'rxjs/operators';

import { RoleService } from '../../../services/role.service';
import { DialogService } from '../../../services/dialog.service';
import { SnackbarService } from '../../../services/snackbar.service';
import { OrganizationService } from '../../../services/organization.service';

@Component({
  selector: 'app-settings-management-roles',
  templateUrl: './roles.component.html',
  styleUrls: ['./roles.component.scss'],
  standalone: false,
})
export class ManagementRolesComponent implements OnInit {
  @ViewChild('rolesTable') table: any;
  roles: any[];

  constructor(
    private roleService: RoleService,
    private organizationService: OrganizationService,
    private dialogService: DialogService,
    private snackbarService: SnackbarService,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.roles = this.route.snapshot.data['roles'];
  }

  get isEmpty() {
    return !this.roles || this.roles.length === 0;
  }

  loadRoles() {
    this.organizationService.roles().subscribe((response) => (this.roles = response));
  }

  delete(id, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Role', 'Are you sure you want to delete this role ?')
      .pipe(
        filter((res) => res),
        switchMap(() => this.organizationService.deleteRole(id)),
        tap(() => {
          this.snackbarService.open('Role deleted');
          this.loadRoles();
        }),
      )
      .subscribe();
  }

  toggleExpandGroup(group) {
    this.table.groupHeader.toggleExpandGroup(group);
  }
}
