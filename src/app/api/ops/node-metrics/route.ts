import { NextResponse } from 'next/server';

export async function GET() {
  try {
    const mem = process.memoryUsage();
    const cpu = process.cpuUsage();
    const uptime = process.uptime();

    return NextResponse.json({
      success: true,
      data: {
        node: {
          rssMb: Math.round(mem.rss / 1024 / 1024),
          heapUsedMb: Math.round(mem.heapUsed / 1024 / 1024),
          heapTotalMb: Math.round(mem.heapTotal / 1024 / 1024),
          externalMb: Math.round(mem.external / 1024 / 1024),
          arrayBuffersMb: Math.round(mem.arrayBuffers / 1024 / 1024),
        },
        cpu: {
          userMs: cpu.user,
          systemMs: cpu.system,
        },
        uptimeSeconds: Math.round(uptime),
        pid: process.pid,
      },
    });
  } catch {
    return NextResponse.json(
      { success: false, error: 'Failed to get Node.js metrics' },
      { status: 500 }
    );
  }
}
