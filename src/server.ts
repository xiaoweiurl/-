import { createServer } from 'http';
import { existsSync } from 'fs';
import { join } from 'path';
import { parse } from 'url';
import next from 'next';
import { isNextDevMode, resolveNextHostname } from './lib/next-runtime';

// Always production Next: no Fast Refresh, no webpack-hmr, no overlay reconnect reloads.
const dev = isNextDevMode();
const hostname = resolveNextHostname(dev);
const port = parseInt(process.env.PORT || '5000', 10);

if (dev) {
  console.error(
    '[server] Next development/HMR is disabled in this project. Refusing to start with dev=true.',
  );
  process.exit(1);
}

const buildIdPath = join(process.cwd(), '.next', 'BUILD_ID');
if (!existsSync(buildIdPath)) {
  console.error(
    '[server] Missing production build (.next/BUILD_ID).\n' +
      '先构建再启动（无 HMR，改代码后需重新 build 并手动重启进程）:\n' +
      '  pnpm run build && pnpm start\n' +
      'Local / FRP: NEXT_HOSTNAME=0.0.0.0 PORT=5000 pnpm start',
  );
  process.exit(1);
}

const app = next({ dev: false, hostname, port });
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
      `> Server listening at http://0.0.0.0:${port} as production` +
        ` (Next hostname=${hostname}, COZE_PROJECT_ENV=${process.env.COZE_PROJECT_ENV || '(unset)'}, NODE_ENV=${process.env.NODE_ENV || '(unset)'})`,
    );
    console.log(
      '> HMR / Fast Refresh / tsx watch: OFF. After code changes: pnpm run build, then manually restart this process.',
    );
  });
});
