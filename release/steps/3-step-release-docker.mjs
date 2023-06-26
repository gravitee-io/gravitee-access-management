import { AbstractStepRelease } from './abstract-step-release.mjs';

export class ReleaseDocker extends AbstractStepRelease {
  buildCommand(program) {
    super.buildCommand(program, 'publish-docker-images');
    this.command
      .description('trigger publish_docker_images step on CircleCI')
      .usage('npm run am-release -- --branch="3.21.x" --gio-version="3.21.22" --dry-run publish-docker-images')
      .option('--tag-latest', 'this is the latest version of the product - false by default', false)
      .option(
        '--tag-latest-support, --no-tag-latest-support',
        'this is the latest support version of the product (eg: 3.20 == 3.20.6) - by default it is (true)',
        true,
      );
  }

  computePipelineParameters(options) {
    return {
      gio_action: 'publish_docker_images',
      tag_latest: options.tagLatest,
      tag_latest_support: options.tagLatestSupport,
    };
  }
}
