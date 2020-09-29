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
import {Component, OnInit} from '@angular/core';
import {AppConfig} from "../../../config/app.config";
import {ActivatedRoute} from "@angular/router";
import {EnvironmentService} from "../../services/environment.service";

@Component({
  selector: 'app-domains',
  templateUrl: './domains.component.html',
  styleUrls: ['./domains.component.scss']
})
export class DomainsComponent implements OnInit {
  title = AppConfig.settings.portalTitle;
  version = AppConfig.settings.version;
  domains = [];
  hasEnv = false;
  currentEnvironment: any;

  constructor(private route : ActivatedRoute) { }

  ngOnInit() {
    this.domains = this.route.snapshot.data['domains'];
    this.hasEnv = this.route.snapshot.data['environment'] !== null;
  }

  get isEmpty() {
    return !this.domains || this.domains.length === 0;
  }
}
