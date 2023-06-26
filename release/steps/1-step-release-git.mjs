import { AbstractStepRelease } from './abstract-step-release.mjs';

export class ReleaseGit extends AbstractStepRelease {
  buildCommand(program) {
    super.buildCommand(program, 'release-git');
    this.command
      .description('trigger git release step on CircleCI')
      .usage('npm run am-release -- --branch="3.21.x" --gio-version="3.21.22" --dry-run release-git')
      .option('-rc, --release-candidate', 'this is a release candidate', false);
  }

  computePipelineParameters(options) {
    return {
      gio_action: 'release',
      rc_requested: options.releaseCandidate,
    };
  }
}
