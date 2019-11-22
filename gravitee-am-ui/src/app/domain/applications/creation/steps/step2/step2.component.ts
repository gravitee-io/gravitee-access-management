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
import {Component, Input, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute} from "@angular/router";

@Component({
  selector: 'application-creation-step2',
  templateUrl: './step2.component.html',
  styleUrls: ['./step2.component.scss']
})
export class ApplicationCreationStep2Component implements OnInit {
  @Input('application') application: any;
  @ViewChild('appForm') form: any;
  domains: any[];
  domainId: string;

  constructor(private route: ActivatedRoute) { }

  ngOnInit(): void {
    this.domainId = this.route.snapshot.parent.params['domainId'];
    this.domains = this.route.snapshot.data['domains'];
  }

  logoUrl(app) {
    return 'assets/application-type-icons/' + app.type.toLowerCase() + '.png';
  }

  displayRedirectUri(): boolean {
    return this.application.type !== 'SERVICE';
  }
}
