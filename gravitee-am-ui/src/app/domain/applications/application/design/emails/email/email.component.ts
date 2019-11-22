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
import { Component, OnInit } from "@angular/core";
import { ActivatedRoute } from "@angular/router";
import { BreadcrumbService } from "../../../../../../../libraries/ng2-breadcrumb/components/breadcrumbService";

@Component({
  selector: 'app-application-email',
  templateUrl: './email.component.html',
  styleUrls: ['./email.component.scss']
})
export class ApplicationEmailComponent implements OnInit {
  private domainId: string;
  private appId: string;
  private rawTemplate: string;
  private template: string;

  constructor(private route: ActivatedRoute,
              private breadcrumbService: BreadcrumbService) { }

  ngOnInit() {
    this.domainId = this.route.snapshot.parent.parent.parent.params['domainId'];
    this.appId = this.route.snapshot.parent.parent.params['appId'];
    this.rawTemplate = this.route.snapshot.queryParams['template'];
    this.template = this.rawTemplate.toLowerCase().replace(/_/g, ' ');
    this.initBreadcrumb();
  }

  initBreadcrumb() {
    this.breadcrumbService.addFriendlyNameForRouteRegex('/domains/'+this.domainId+'/applications/'+this.appId+'/design/emails/email*', this.template);
  }
}
