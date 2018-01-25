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
import { RoleService } from "../../../../services/role.service";
import { ActivatedRoute, Router } from "@angular/router";
import { SnackbarService } from "../../../../services/snackbar.service";

@Component({
  selector: 'app-creation',
  templateUrl: './role-creation.component.html',
  styleUrls: ['./role-creation.component.scss']
})
export class RoleCreationComponent implements OnInit {
  private scopes: any[];
  private domainId: string;
  role: any = {};


  constructor(private roleService: RoleService, private router: Router, private route: ActivatedRoute,
              private snackbarService : SnackbarService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    this.scopes = this.route.snapshot.data['scopes'];
  }

  create() {
    this.roleService.create(this.domainId, this.role).map(res => res.json()).subscribe(data => {
      this.snackbarService.open("Role " + data.name + " created");
      this.router.navigate(['/domains', this.domainId, 'settings', 'roles', data.id]);
    });
  }

}
