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
import { Component, OnInit, ViewChild } from "@angular/core";
import { ActivatedRoute, Router } from "@angular/router";
import { RoleService } from "../../../../services/role.service";
import { SnackbarService } from "../../../../services/snackbar.service";
import { BreadcrumbService } from "../../../../../libraries/ng2-breadcrumb/components/breadcrumbService";
import { MatInput } from "@angular/material/input";
import * as _ from "lodash";
import { AppConfig } from "../../../../../config/app.config";
import { DialogService } from "../../../../services/dialog.service";

export interface Scope {
  id: string;
  key: string;
  name: string;
}

@Component({
  selector: 'app-role',
  templateUrl: './role.component.html',
  styleUrls: ['./role.component.scss']
})
export class RoleComponent implements OnInit {

  @ViewChild('chipInput') chipInput: MatInput;

  private domainId: string;
  scopes: Scope[];
  selectedPermissions: Scope[];
  role: any;
  formChanged: boolean = false;

  constructor(private roleService: RoleService,
              private snackbarService: SnackbarService,
              private route: ActivatedRoute,
              private router: Router,
              private breadcrumbService: BreadcrumbService,
              private dialogService: DialogService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.domainId = AppConfig.settings.authentication.domainId;
    }
    this.role = this.route.snapshot.data['role'];
    this.scopes = this.route.snapshot.data['scopes'];

    if (!this.role.permissions) {
      this.role.permissions = [];
    }

    this.initBreadcrumb();
    this.initScopes();
  }

  initScopes() {
    let that = this;
    // Merge with existing scope
    this.selectedPermissions = _.map(this.role.permissions, function(permission) {
      let scope = _.find(that.scopes, { 'key': permission });
      if (scope !== undefined) {
        return scope;
      }

      return undefined;
    });

    this.scopes = _.difference(this.scopes, this.selectedPermissions);
  }

  update() {
    this.role.permissions = _.map(this.selectedPermissions, permission => permission.key);
    this.roleService.update(this.domainId, this.role.id, this.role).subscribe(data => {
      this.role = data;
      this.initBreadcrumb();
      this.snackbarService.open("Role updated");
    });
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Role', 'Are you sure you want to delete this role ?')
      .subscribe(res => {
        if (res) {
          this.roleService.delete(this.domainId, this.role.id).subscribe(() => {
            this.snackbarService.open('Role '+ this.role.name + ' deleted');
            this.router.navigate(['/domains', this.domainId, 'settings', 'roles']);
          });
        }
      });
  }

  addPermission(event) {
    this.selectedPermissions = this.selectedPermissions.concat(_.remove(this.scopes, { 'key': event.option.value }));
    this.chipInput['nativeElement'].blur();
    this.formChanged = true;
  }

  removePermission(permission) {
    this.scopes = this.scopes.concat(_.remove(this.selectedPermissions, function(selectPermission) {
      return selectPermission.key === permission.key;
    }));

    this.chipInput['nativeElement'].blur();
    this.formChanged = true;
  }

  initBreadcrumb() {
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/settings/roles/'+this.role.id+'$', this.role.name);
  }
}
