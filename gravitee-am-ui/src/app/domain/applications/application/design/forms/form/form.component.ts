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

import { AuthService } from '../../../../../../services/auth.service';

@Component({
  selector: 'app-application-form',
  templateUrl: './form.component.html',
  styleUrls: ['./form.component.scss'],
})
export class ApplicationFormComponent implements OnInit {
  private domainId: string;
  private appId: string;
  private rawTemplate: string;
  private template: string;
  createMode: boolean;
  editMode: boolean;
  deleteMode: boolean;

  constructor(private route: ActivatedRoute, private authService: AuthService) {}

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.appId = this.route.snapshot.params['appId'];
    this.rawTemplate = this.route.snapshot.queryParams['template'];
    this.template = this.rawTemplate.toLowerCase().replace(/_/g, ' ');
    this.createMode = this.authService.hasPermissions(['application_form_create']);
    this.editMode = this.authService.hasPermissions(['application_form_update']);
    this.deleteMode = this.authService.hasPermissions(['application_form_delete']);
  }
}
