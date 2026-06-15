import { NextRequest, NextResponse } from "next/server";
import { EmbeddingClient, HeaderUtils } from "coze-coding-dev-sdk";

/**
 * Embedding API - 文本向量化服务
 * 调用 coze-coding-dev-sdk 的 EmbeddingClient 将文本转为向量
 */

export async function POST(request: NextRequest) {
  try {
    const { text } = await request.json();
    if (!text || typeof text !== "string") {
      return NextResponse.json(
        { error: "缺少 text 参数或类型错误" },
        { status: 400 }
      );
    }

    const customHeaders = HeaderUtils.extractForwardHeaders(request.headers);
    const client = new EmbeddingClient({ customHeaders });
    const embedding = await client.embedText(text);

    return NextResponse.json({
      success: true,
      embedding,
      dimensions: embedding.length,
    });
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error);
    console.error("[Embedding] 向量化失败:", message);
    return NextResponse.json(
      { error: "向量化失败", detail: message },
      { status: 500 }
    );
  }
}
