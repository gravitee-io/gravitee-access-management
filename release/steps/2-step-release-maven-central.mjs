import { AbstractStepRelease } from './abstract-step-release.mjs';

export class ReleaseMavenCentral extends AbstractStepRelease {
  buildCommand(program) {
    super.buildCommand(program, 'publish-maven-central');
    this.command
      .description('trigger publish_maven_central step on CircleCI')
      .usage('npm run am-release -- --branch="3.21.x" --gio-version="3.21.22" --dry-run publish-maven-central');
  }

  computePipelineParameters(options) {
    return {
      gio_action: 'publish_maven_central',
    };
  }
}
