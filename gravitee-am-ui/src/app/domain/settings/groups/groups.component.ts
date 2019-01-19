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
import { SnackbarService } from "../../../services/snackbar.service";
import { DialogService } from "../../../services/dialog.service";
import { ActivatedRoute, Router } from "@angular/router";
import { AppConfig } from "../../../../config/app.config";
import { GroupService } from "../../../services/group.service";

@Component({
  selector: 'app-groups',
  templateUrl: './groups.component.html',
  styleUrls: ['./groups.component.scss']
})
export class GroupsComponent implements OnInit {
  pagedGroups: any;
  groups: any[];
  domainId: string;
  page: any = {};

  constructor(private groupService: GroupService, private dialogService: DialogService,
              private snackbarService: SnackbarService, private route: ActivatedRoute, private router: Router) {
    this.page.pageNumber = 0;
    this.page.size = 25;
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.params['domainId'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.domainId = AppConfig.settings.authentication.domainId;
    }
    this.pagedGroups = this.route.snapshot.data['groups'];
    this.groups = this.pagedGroups.data;
    this.page.totalElements = this.pagedGroups.totalCount;
  }

  get isEmpty() {
    return !this.groups || this.groups.length == 0;
  }

  loadGroups() {
    this.groupService.findByDomain(this.domainId, this.page.pageNumber, this.page.size).map(res => res.json()).subscribe(pagedGroups => {
      this.page.totalElements = pagedGroups.totalCount;
      this.groups = pagedGroups.data;
    });
  }

  delete(id, event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Group', 'Are you sure you want to delete this group ?')
      .subscribe(res => {
        if (res) {
          this.groupService.delete(this.domainId, id).subscribe(response => {
            this.snackbarService.open("Group deleted");
            this.page.pageNumber = 0;
            this.loadGroups();
          });
        }
      });
  }

  setPage(pageInfo){
    this.page.pageNumber = pageInfo.offset;
    this.loadGroups();
  }
}
