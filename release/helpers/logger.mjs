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
    paths: ['init.headers["Circle-Token"]'],
    censor: '*****',
  },
});
export default logger;
