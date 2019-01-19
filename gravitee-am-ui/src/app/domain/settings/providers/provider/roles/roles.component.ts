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
import {Component, OnInit, Inject, ViewChild} from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { SnackbarService } from "../../../../../services/snackbar.service";
import { ActivatedRoute, Router } from "@angular/router";
import { ProviderService } from "../../../../../services/provider.service";
import { DialogService } from "../../../../../services/dialog.service";
import {AppConfig} from "../../../../../../config/app.config";
import { MatSelect } from "@angular/material";
import {GroupService} from "../../../../../services/group.service";
import {NgForm} from "@angular/forms";

@Component({
  selector: 'app-roles',
  templateUrl: './roles.component.html',
  styleUrls: ['./roles.component.scss']
})
export class ProviderRolesComponent implements OnInit {
  private domainId: string;
  private provider: any;
  roles: any;
  groups: any;
  providerRoleMapper: any = {};

  constructor(private snackbarService: SnackbarService, private providerService: ProviderService,
              private dialogService: DialogService, private dialog: MatDialog, private route: ActivatedRoute, private router: Router) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.parent.params['domainId'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.domainId = AppConfig.settings.authentication.domainId;
    }
    this.provider = this.route.snapshot.parent.data['provider'];
    this.roles = this.route.snapshot.data['roles'];
    this.groups = this.route.snapshot.data['groups'].data;
    if (this.provider.roleMapper) {
      this.providerRoleMapper = this.provider.roleMapper;
    }
  }

  add() {
    let dialogRef = this.dialog.open(CreateRoleMapperComponent, { data: { domain: this.domainId, roles: this.roles, groups: this.groups }, width : '700px'});

    dialogRef.afterClosed().subscribe(mapper => {
      if (mapper) {
        let errorMessages = [];
        let roleMapped = false;
        mapper.roles.forEach(role => {
          // no mapping for this role
          if (!this.providerRoleMapper.hasOwnProperty(role)) {
            if (mapper.user) {
              this.providerRoleMapper[role] = [mapper.user];
            }
            if (mapper.groups && mapper.groups.length > 0) {
              mapper.groups.forEach(group => {
                (this.providerRoleMapper[role] = this.providerRoleMapper[role] || []).push('group=' + group);
              });
            }
            roleMapped = true;
          } else {
            // check user/group uniqueness
            let users = this.providerRoleMapper[role];
            // user
            if (mapper.user) {
              if (users.indexOf(mapper.user) === -1) {
                users.push(mapper.user);
                this.providerRoleMapper[role] = users;
                roleMapped = true;
              } else {
                errorMessages.push(`'${mapper.user}' has already the '${this.getRole(role)}' role`);
              }
            }
            // group
            if (mapper.groups && mapper.groups.length > 0) {
              mapper.groups.forEach(group => {
                let groupValue = 'group=' + group;
                if (users.indexOf(groupValue) === -1) {
                  users.push(groupValue);
                  this.providerRoleMapper[role] = users;
                  roleMapped = true;
                } else {
                  errorMessages.push(`'${this.getGroup(group)}' has already the '${this.getRole(role)}' role`);
                }
              });
            }
          }
        });

        if (roleMapped) {
          this.update();
        }

        if (errorMessages.length > 0) {
          this.snackbarService.openFromComponent('Errors', errorMessages);
        }
      }
    });
  }

  update() {
    this.provider.roleMapper = this.providerRoleMapper;
    this.providerService.update(this.domainId, this.provider.id, this.provider).map(res => res.json()).subscribe(data => {
      this.snackbarService.open("Role mapping updated");
    })
  }

  deleteUserFromRole(role, user, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete entry from role', `Are you sure you want to remove this entry from '${this.getRole(role)}' role ?`)
      .subscribe(res => {
        if (res) {
          let users = this.providerRoleMapper[role];
          this.providerRoleMapper[role] = users.filter(_user => _user !== user);
          if (this.providerRoleMapper[role].length == 0) {
            delete this.providerRoleMapper[role];
          }
          this.update();
        }
      });
  }

  getRole(id): string {
    return this.roles.filter((role) => role.id === id).map(role => role.name);
  }

  get providerRoles(): string[] {
    if (this.providerRoleMapper) {
      return Object.keys(this.providerRoleMapper);
    }

    return [];
  }

  get isEmpty() {
    return !this.providerRoleMapper || this.providerRoles.length == 0;
  }

  getGroup(id): string {
    return this.groups.filter((group) => group.id === id).map(group => group.name);
  }

  displayMapping(mapping: string): string {
    let mapperKey = mapping.split('=')[0];
    let mapperValue = mapping.split('=')[1];
    if (mapperKey === 'group') {
      mapperValue = this.getGroup(mapperValue);
    }
    return mapperKey + '=' + mapperValue;
  }

}

@Component({
  selector: 'create-role-mapper',
  templateUrl: './create/create.component.html',
})
export class CreateRoleMapperComponent implements OnInit {
  @ViewChild('groupSelect') selectElem: MatSelect;
  @ViewChild('userRoleForm') form: NgForm;
  private page = 0;
  private size = 25;
  private RELOAD_TOP_SCROLL_POSITION = 25;
  groups: any[];

  constructor(@Inject(MAT_DIALOG_DATA) public data: any,
              public dialogRef: MatDialogRef<CreateRoleMapperComponent>,
              private groupService: GroupService) {
    this.groups = data.groups;
  }

  ngOnInit() {
    this.selectElem.onOpen.subscribe(() => this.registerPanelScrollEvent());
  }

  get formInvalid() {
    let formValue = this.form.value;
    return !formValue.user && (formValue.groups === undefined || formValue.groups.length == 0);
  }

  registerPanelScrollEvent() {
    const panel = this.selectElem.panel.nativeElement;
    panel.addEventListener('scroll', event => this.loadGroupsOnScroll(event));
  }

  loadGroupsOnScroll(event) {
    if (event.target.scrollTop > this.RELOAD_TOP_SCROLL_POSITION) {
      this.groupService.findByDomain(this.data.domain, ++this.page, this.size).map(res => res.json()).subscribe(response => {
        this.groups = response.data;
        this.RELOAD_TOP_SCROLL_POSITION += this.groups.length * this.page;
      });
    }
  }
}
