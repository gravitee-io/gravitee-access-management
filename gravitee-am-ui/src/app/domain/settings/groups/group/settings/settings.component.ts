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
import {ActivatedRoute, Router} from '@angular/router';
import {SnackbarService} from '../../../../../services/snackbar.service';
import {DialogService} from '../../../../../services/dialog.service';
import {GroupService} from '../../../../../services/group.service';
import {AuthService} from '../../../../../services/auth.service';

@Component({
  selector: 'app-group-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.scss']
})
export class GroupSettingsComponent implements OnInit {
  @ViewChild('groupForm') form: any;
  private domainId: string;
  private organizationContext = false;
  group: any;
  editMode: boolean;
  deleteMode: boolean;

  constructor(private route: ActivatedRoute,
              private router: Router,
              private snackbarService: SnackbarService,
              private dialogService: DialogService,
              private groupService: GroupService,
              private authService: AuthService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.params['domainId'];
    if (this.router.routerState.snapshot.url.startsWith('/settings')) {
      this.organizationContext = true;
      this.editMode = this.authService.hasPermissions(['organization_group_update']);
      this.deleteMode = this.authService.hasPermissions(['organization_group_delete']);
    } else {
      this.editMode = this.authService.hasPermissions(['domain_group_update']);
      this.deleteMode = this.authService.hasPermissions(['domain_group_delete']);
    }
    this.group = this.route.snapshot.data['group'];
  }

  update() {
    this.groupService.update(this.domainId, this.group.id, this.group, this.organizationContext).subscribe(data => {
      this.group = data;
      this.form.reset(this.group);
      this.snackbarService.open('Group updated');
    });
  }

  delete(event) {
    event.preventDefault();
    this.dialogService
      .confirm('Delete Group', 'Are you sure you want to delete this group ?')
      .subscribe(res => {
        if (res) {
          this.groupService.delete(this.domainId, this.group.id, this.organizationContext).subscribe(response => {
            this.snackbarService.open('Group ' + this.group.name + ' deleted');
            this.router.navigate(['../..'], { relativeTo: this.route});
          });
        }
      });
  }
}
