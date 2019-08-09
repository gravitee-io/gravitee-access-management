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
import {Component, OnInit, ViewChild} from '@angular/core';
import { ActivatedRoute, Router } from "@angular/router";
import { BreadcrumbService } from "../../../../../../libraries/ng2-breadcrumb/components/breadcrumbService";
import { SnackbarService } from "../../../../../services/snackbar.service";
import { DialogService } from "../../../../../services/dialog.service";
import { GroupService } from "../../../../../services/group.service";
import { AppConfig } from "../../../../../../config/app.config";

@Component({
  selector: 'app-group-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.scss']
})
export class GroupSettingsComponent implements OnInit {
  @ViewChild('groupForm') form: any;
  private domainId: string;
  group: any;

  constructor(private route: ActivatedRoute,
              private router: Router,
              private breadcrumbService: BreadcrumbService,
              private snackbarService: SnackbarService,
              private dialogService: DialogService,
              private groupService: GroupService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.parent.params['domainId'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.domainId = AppConfig.settings.authentication.domainId;
    }
    this.group = this.route.snapshot.parent.data['group'];
    this.initBreadcrumb();
  }

  initBreadcrumb() {
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/settings/groups/'+this.group.id+'$', this.group.name);
  }

  update() {
    this.groupService.update(this.domainId, this.group.id, this.group).subscribe(data => {
      this.group = data;
      this.form.reset(this.group);
      this.initBreadcrumb();
      this.snackbarService.open("Group updated");
    });
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Group', 'Are you sure you want to delete this group ?')
      .subscribe(res => {
        if (res) {
          this.groupService.delete(this.domainId, this.group.id).subscribe(response => {
            this.snackbarService.open('Group '+ this.group.name + ' deleted');
            this.router.navigate(['/domains', this.domainId, 'settings', 'groups']);
          });
        }
      });
  }
}
