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
import { ActivatedRoute } from '@angular/router';

import { ApplicationService } from '../../../../../services/application.service';
import { AuthService } from '../../../../../services/auth.service';
import { MembershipsDialogData } from '../../../../../components/memberships/dialog/memberships-dialog.component';

@Component({
  selector: 'app-application-memberships',
  templateUrl: './memberships.component.html',
  styleUrls: ['./memberships.component.scss'],
  standalone: false,
})
export class ApplicationMembershipsComponent implements OnInit {
  private domainId: string;
  private application: any;
  appId: string;
  members: any;
  dialogData: MembershipsDialogData;
  createMode = false;
  editMode = false;
  deleteMode = false;

  constructor(
    private applicationService: ApplicationService,
    private authService: AuthService,
    private route: ActivatedRoute,
  ) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.application = this.route.snapshot.data['application'];
    this.appId = this.application.id;
    this.members = this.route.snapshot.data['members'];
    this.createMode = this.authService.hasPermissions(['application_member_create']);
    this.editMode = this.authService.hasPermissions(['application_member_update']);
    this.deleteMode = this.authService.hasPermissions(['application_member_delete']);
    this.updateDialogData();
  }

  isEditable() {
    return this.createMode || this.editMode || this.deleteMode;
  }

  reloadMembers() {
    this.applicationService.members(this.domainId, this.application.id).subscribe((response) => {
      this.members = response;
      this.updateDialogData();
    });
  }
  private updateDialogData() {
    this.dialogData = {
      context: 'APPLICATION',
      domainId: this.domainId,
      appId: this.appId,
      resource: this.application,
      members: this.members,
      roleType: 'APPLICATION',
      createMode: this.createMode,
      editMode: this.editMode,
      deleteMode: this.deleteMode,
    };
  }
}
