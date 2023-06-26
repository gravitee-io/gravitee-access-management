import { AbstractStepRelease } from './abstract-step-release.mjs';

export class ReleaseHelm extends AbstractStepRelease {
  buildCommand(program) {
    super.buildCommand(program, 'release-helm');
    this.command
      .description('trigger release_helm step on CircleCI')
      .usage('npm run am-release -- --branch="3.21.x" --gio-version="3.21.22" --dry-run release-helm');
  }

  computePipelineParameters(options) {
    return {
      gio_action: 'release_helm',
    };
  }
}
