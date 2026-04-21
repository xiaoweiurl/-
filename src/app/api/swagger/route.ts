import { NextResponse } from 'next/server';
import { swaggerSpec } from '@/lib/swagger';

/**
 * @swagger
 * /api/swagger:
 *   get:
 *     summary: 获取 OpenAPI 规范
 *     description: 返回 OpenAPI 3.0 规范的 JSON 定义
 *     responses:
 *       200:
 *         description: OpenAPI 规范 JSON
 */
export async function GET() {
  return NextResponse.json(swaggerSpec);
}
