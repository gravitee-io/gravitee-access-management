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
import { GroupService } from "../../../../services/group.service";
import { OrganizationService } from "../../../../services/organization.service";

@Component({
  selector: 'group-creation',
  templateUrl: './group-creation.component.html',
  styleUrls: ['./group-creation.component.scss']
})
export class GroupCreationComponent implements OnInit {
  private domainId: string;
  private organizationContext: boolean;
  group: any = {};

  constructor(private groupService: GroupService,
              private organizationService: OrganizationService,
              private router: Router,
              private route: ActivatedRoute,
              private snackbarService: SnackbarService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.params['domainId'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.organizationContext = true;
    }
  }

  create() {
    if (this.organizationContext) {
      this.organizationService.createGroup(this.group).subscribe(data => {
        this.snackbarService.open('Group ' + data.name + ' created');
        this.router.navigate(['/settings', 'groups', data.id]);
      });
    } else {
      this.groupService.create(this.domainId, this.group).subscribe(data => {
        this.snackbarService.open('Group ' + data.name + ' created');
        this.router.navigate(['..', data.id], {relativeTo: this.route} );
      });
    }
  }
}
