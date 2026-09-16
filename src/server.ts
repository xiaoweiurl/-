import { createServer } from 'http';
import { existsSync } from 'fs';
import { join } from 'path';
import { parse } from 'url';
import next from 'next';
import { isNextDevMode, resolveNextHostname } from './lib/next-runtime';

const dev = isNextDevMode();
const hostname = resolveNextHostname(dev);
const port = parseInt(process.env.PORT || '5000', 10);
const modeLabel = dev ? 'development' : 'production';

if (!dev) {
  const buildIdPath = join(process.cwd(), '.next', 'BUILD_ID');
  if (!existsSync(buildIdPath)) {
    console.error(
      '[server] Production mode requires a prior `pnpm run build` (missing .next/BUILD_ID).\n' +
        'FRP 部署请先执行: pnpm run build && pnpm start\n' +
        'Behind FRP: build, then start in production (no HMR). Example:\n' +
        '  COZE_PROJECT_ENV=PROD NODE_ENV=production NEXT_HOSTNAME=0.0.0.0 pnpm start',
    );
    process.exit(1);
  }
}

const app = next({ dev, hostname, port });
const handle = app.getRequestHandler();

app.prepare().then(() => {
  const server = createServer(async (req, res) => {
    try {
      const parsedUrl = parse(req.url!, true);
      await handle(req, res, parsedUrl);
    } catch (err) {
      console.error('Error occurred handling', req.url, err);
      res.statusCode = 500;
      res.end('Internal server error');
    }
  });
  server.once('error', err => {
    console.error(err);
    process.exit(1);
  });
  server.listen(port, '0.0.0.0', () => {
    console.log(
      `> Server listening at http://0.0.0.0:${port} as ${modeLabel}` +
        ` (Next hostname=${hostname}, COZE_PROJECT_ENV=${process.env.COZE_PROJECT_ENV || '(unset)'}, NODE_ENV=${process.env.NODE_ENV || '(unset)'})`,
    );
    if (dev) {
      console.log(
        '> Development mode: Fast Refresh/HMR is ON. Do not expose this via FRP — ' +
          'failed webpack-hmr websockets will full-page reload every few seconds.\n' +
          '  Company FRP: pnpm run build && pnpm start (COZE_PROJECT_ENV=PROD, NODE_ENV=production).\n' +
          '  Dev-over-FRP (unsupported, last resort): ALLOWED_DEV_ORIGINS=<frp-host> NEXT_HOSTNAME=<frp-host> NEXT_DISABLE_HMR=1',
      );
    } else {
      console.log('> Production mode: HMR/Fast Refresh disabled (safe for FRP).');
    }
  });
});
