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
import { ScopeService } from "../../../../services/scope.service";
import { ActivatedRoute, Router } from "@angular/router";
import { SnackbarService } from "../../../../services/snackbar.service";

@Component({
  selector: 'app-creation',
  templateUrl: './scope-creation.component.html',
  styleUrls: ['./scope-creation.component.scss']
})
export class ScopeCreationComponent implements OnInit {
  private domainId: string;
  scope: any = {};

  constructor(private scopeService: ScopeService, private router: Router, private route: ActivatedRoute,
              private snackbarService : SnackbarService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
  }

  create() {
    // Force lowercase for scope key
    this.scope.key = this.scope.key.toLowerCase();
    this.scopeService.create(this.domainId, this.scope).map(res => res.json()).subscribe(data => {
      this.snackbarService.open("Scope " + data.name + " created");
      this.router.navigate(['/domains', this.domainId, 'settings', 'scopes', data.id]);
    });
  }

}
