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
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from "@angular/router";
import { RoleService } from "../../../../services/role.service";
import { SnackbarService } from "../../../../services/snackbar.service";
import { BreadcrumbService } from "../../../../../libraries/ng2-breadcrumb/components/breadcrumbService";

@Component({
  selector: 'app-role',
  templateUrl: './role.component.html',
  styleUrls: ['./role.component.scss']
})
export class RoleComponent implements OnInit {
  private domainId: string;
  role: any;
  formChanged: boolean = false;

  constructor(private roleService: RoleService, private snackbarService: SnackbarService, private route: ActivatedRoute,
              private breadcrumbService: BreadcrumbService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.params['domainId'];
    this.role = this.route.snapshot.data['role'];
    if (!this.role.permissions) {
      this.role.permissions = [];
    }
    this.initBreadcrumb();
  }

  update() {
    this.roleService.update(this.domainId, this.role.id, this.role).map(res => res.json()).subscribe(data => {
      this.role = data;
      this.initBreadcrumb();
      this.snackbarService.open("Role updated");
    });
  }

  addPermission(input: HTMLInputElement, event) {
    this.addElement(input, this.role.permissions, event);
  }

  removePermission(role, event) {
    this.removeElement(role, this.role.permissions, event);
  }

  addElement(input: HTMLInputElement, list: any[], event: any) {
    event.preventDefault();
    if (input.value && input.value.trim() != '' && list.indexOf(input.value.trim()) == -1) {
      list.push(input.value.trim());
      input.value = '';
      this.formChanged = true;
    }
  }

  removeElement(element: any, list: any[], event: any) {
    event.preventDefault();
    let index = list.indexOf(element);
    if (index !== -1) {
      list.splice(index, 1);
      this.formChanged = true;
    }
  }

  initBreadcrumb() {
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/roles/'+this.role.id+'$', this.role.name);
  }
}
