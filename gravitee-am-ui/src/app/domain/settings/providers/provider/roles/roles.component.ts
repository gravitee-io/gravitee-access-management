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
import { Component, OnInit, Inject, ViewChild, HostListener } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { ActivatedRoute, Router } from '@angular/router';
import { NgForm } from '@angular/forms';

import { SnackbarService } from '../../../../../services/snackbar.service';
import { ProviderService } from '../../../../../services/provider.service';
import { DialogService } from '../../../../../services/dialog.service';
import { SpelGrammarService } from '../../../../../services/spel-grammar.service';

@Component({
  selector: 'app-roles',
  templateUrl: './roles.component.html',
  styleUrls: ['./roles.component.scss'],
  standalone: false,
})
export class ProviderRolesComponent implements OnInit {
  private domainId: string;
  private organizationContext = false;
  private provider: any;
  roles: any;
  providerRoleMapper: any = {};

  constructor(
    private snackbarService: SnackbarService,
    private providerService: ProviderService,
    private dialogService: DialogService,
    private dialog: MatDialog,
    private route: ActivatedRoute,
    private router: Router,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.organizationContext = true;
    }
    this.provider = this.route.snapshot.data['provider'];
    this.roles = this.route.snapshot.data['roles'];
    if (this.provider.roleMapper) {
      this.providerRoleMapper = this.provider.roleMapper;
    }
  }

  add() {
    const dialogRef = this.dialog.open(CreateRoleMapperComponent, {
      data: { domain: this.domainId, roles: this.roles, organizationContext: this.organizationContext },
      width: '700px',
    });

    dialogRef.afterClosed().subscribe((mapper) => {
      if (mapper) {
        const errorMessages = [];
        let roleMapped = false;
        let mapperRoles;

        if (Array.isArray(mapper.roles)) {
          mapperRoles = mapper.roles;
        } else {
          mapperRoles = [mapper.roles];
        }

        mapperRoles.forEach((role) => {
          // no mapping for this role
          // eslint-disable-next-line no-prototype-builtins
          if (!this.providerRoleMapper.hasOwnProperty(role)) {
            if (mapper.user) {
              this.providerRoleMapper[role] = [mapper.user];
            }
            roleMapped = true;
          } else {
            // check uniqueness
            const users = this.providerRoleMapper[role];
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
    this.provider.configuration = this.provider.configuration ? JSON.parse(this.provider.configuration) : {};
    this.provider.roleMapper = this.providerRoleMapper;
    this.providerService.update(this.domainId, this.provider.id, this.provider, this.organizationContext).subscribe(() => {
      this.snackbarService.open('Role mapping updated');
    });
  }

  deleteUserFromRole(role, user, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete entry from role', `Are you sure you want to remove this entry from '${this.getRole(role)}' role ?`)
      .subscribe((res) => {
        if (res) {
          const users = this.providerRoleMapper[role];
          this.providerRoleMapper[role] = users.filter((_user) => _user !== user);
          if (this.providerRoleMapper[role].length === 0) {
            delete this.providerRoleMapper[role];
          }
          this.update();
        }
      });
  }

  getRole(id): string {
    return this.roles.filter((role) => role.id === id).map((role) => role.name);
  }

  get providerRoles(): string[] {
    if (this.providerRoleMapper) {
      return Object.keys(this.providerRoleMapper);
    }

    return [];
  }

  get isEmpty() {
    return !this.providerRoleMapper || this.providerRoles.length === 0;
  }
}

@Component({
  selector: 'create-role-mapper',
  templateUrl: './create/create.component.html',
  standalone: false,
})
export class CreateRoleMapperComponent implements OnInit {
  @ViewChild('userRoleForm', { static: true }) form: NgForm;
  rule: string;

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: any,
    public dialogRef: MatDialogRef<CreateRoleMapperComponent>,
    private spelGrammarChecker: SpelGrammarService,
  ) {
    this.rule = '';
  }

  ngOnInit() {
    this.spelGrammarChecker.init();
  }

  getGrammar() {
    return this.spelGrammarChecker.getGrammar();
  }

  @HostListener(':gv-expression-language:ready', ['$event.detail'])
  setGrammar({ currentTarget }) {
    this.getGrammar().then((grammar) => {
      currentTarget.grammar = grammar;
      currentTarget.requestUpdate();
    });
  }

  get formInvalid() {
    const formValue = this.form.value;
    return !formValue.user;
  }

  change($event) {
    this.rule = $event.target.value;
  }
}
