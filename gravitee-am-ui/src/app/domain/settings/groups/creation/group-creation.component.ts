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
import { ActivatedRoute, Router } from "@angular/router";
import { SnackbarService } from "../../../../services/snackbar.service";
import { AppConfig } from "../../../../../config/app.config";
import { GroupService } from "../../../../services/group.service";

@Component({
  selector: 'group-creation',
  templateUrl: './group-creation.component.html',
  styleUrls: ['./group-creation.component.scss']
})
export class GroupCreationComponent implements OnInit {
  private domainId: string;
  private adminContext: boolean;
  group: any = {};

  constructor(private groupService: GroupService, private router: Router, private route: ActivatedRoute,
              private snackbarService: SnackbarService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.domainId = AppConfig.settings.authentication.domainId;
      this.adminContext = true;
    }
  }

  create() {
    this.groupService.create(this.domainId, this.group).map(res => res.json()).subscribe(data => {
      this.snackbarService.open("Group " + data.name + " created");
      if (this.adminContext) {
        this.router.navigate(['/settings', 'management', 'groups', data.id]);
      } else {
        this.router.navigate(['/domains', this.domainId, 'settings', 'groups', data.id]);
      }
    });
  }
}
