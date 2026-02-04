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

import { AuthService } from '../../../../../services/auth.service';
import { ProtectedResource, ProtectedResourceService } from '../../../../../services/protected-resource.service';
import { MembershipsDialogData } from '../../../../../components/memberships/dialog/memberships-dialog.component';

@Component({
  selector: 'app-domain-mcp-server-memberships',
  templateUrl: './memberships.component.html',
  styleUrls: ['./memberships.component.scss'],
  standalone: false,
})
export class DomainMcpServerMembershipsComponent implements OnInit {
  private domainId: string;
  protectedResource: ProtectedResource;
  members: any;
  dialogData: MembershipsDialogData;
  createMode = false;
  editMode = false;
  deleteMode = false;

  constructor(
    private protectedResourceService: ProtectedResourceService,
    private authService: AuthService,
    private route: ActivatedRoute,
  ) {}

  ngOnInit(): void {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.protectedResource = this.route.snapshot.data['mcpServer'];
    this.members = this.route.snapshot.data['members'];
    this.createMode = this.authService.hasPermissions(['protected_resource_member_create']);
    this.editMode = this.authService.hasPermissions(['protected_resource_member_update']);
    this.deleteMode = this.authService.hasPermissions(['protected_resource_member_delete']);
    this.updateDialogData();
  }

  isEditable() {
    return this.createMode || this.editMode || this.deleteMode;
  }

  reloadMembers() {
    this.protectedResourceService.members(this.domainId, this.protectedResource.id).subscribe((response) => {
      this.members = response;
      this.updateDialogData();
    });
  }

  private updateDialogData() {
    this.dialogData = {
      context: 'PROTECTED_RESOURCE',
      domainId: this.domainId,
      protectedResourceId: this.protectedResource?.id,
      resource: this.protectedResource,
      members: this.members,
      roleType: 'PROTECTED_RESOURCE',
      createMode: this.createMode,
      editMode: this.editMode,
      deleteMode: this.deleteMode,
    };
  }
}
