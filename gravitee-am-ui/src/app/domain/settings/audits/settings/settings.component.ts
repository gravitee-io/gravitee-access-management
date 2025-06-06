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

import { AuthService } from '../../../../services/auth.service';

@Component({
  selector: 'app-audits-settings',
  templateUrl: './settings.component.html',
  styleUrls: ['./settings.component.scss'],
  standalone: false,
})
export class AuditsSettingsComponent implements OnInit {
  reporters: any[];
  domainId: string;

  constructor(
    private route: ActivatedRoute,
    private authService: AuthService,
  ) {}

  ngOnInit() {
    this.reporters = this.route.snapshot.data['reporters'];
  }

  get isEmpty() {
    return !this.reporters || this.reporters.length === 0;
  }

  rowClass = (row) => {
    return {
      'row-disabled': !row.enabled,
    };
  };

  hasPermissions(permissions) {
    return this.authService.hasPermissions(permissions);
  }
}
