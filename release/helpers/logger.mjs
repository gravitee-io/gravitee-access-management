import pino from 'pino';

const logger = pino({
  level: 'info',
  transport: {
    target: 'pino-pretty',
    options: {
      colorize: true,
    },
  },
  redact: {
    paths: [
      'init.headers["Circle-Token"]',
      'init.headers.Authorization',
      '*.headers.Authorization',
    ],
    censor: '*****',
  },
});
export default logger;
