import { AbstractStepRelease } from './abstract-step-release.mjs';

export class ReleaseRPM extends AbstractStepRelease {
  buildCommand(program) {
    super.buildCommand(program, 'publish-rpms');
    this.command
      .description('trigger publish_rpms step on CircleCI')
      .usage('npm run am-release -- --branch="3.21.x" --gio-version="3.21.22" --dry-run publish-rpms');
  }

  computePipelineParameters(options) {
    return {
      gio_action: 'publish_rpms',
    };
  }
}
