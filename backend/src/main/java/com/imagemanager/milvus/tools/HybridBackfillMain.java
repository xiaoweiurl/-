package com.imagemanager.milvus.tools;

import com.imagemanager.milvus.HybridCollectionGuard;
import com.imagemanager.milvus.MilvusHybridSchema;
import io.milvus.v2.client.MilvusClientV2;

import java.util.Map;

/**
 * 把稠密集合里已有的正文和向量分批抄到影子集合，并在写入前生成 BM25 分词。
 * 不启动 Web，不连接 Postgres，不调用 Ollama。可以重复执行：每次只删影子集合再重抄。
 *
 * <p>Windows 上不要用 {@code mvnw.cmd exec:java -Dexec.args}，PowerShell 和 cmd 都会把参数拆碎。
 * 用仓库里的 {@code scripts/hybrid-backfill.ps1}（先 {@code dependency:build-classpath}，再 {@code java -cp}）。
 */
public final class HybridBackfillMain {

    private HybridBackfillMain() {
    }

    public static void main(String[] args) {
        Map<String, String> options = ToolArgs.parse(args);
        if (ToolArgs.flag(options, "help")) {
            System.out.println("""
                    用法: HybridBackfillMain --host=localhost --port=19530 --source=salesperson_docs --target=salesperson_docs_hybrid --dim=1024 --batch=200
                    --dry-run  只打印行数，不创建、不删除任何集合
                    只读 source。会删除并重建 target。拒绝 target 为 salesperson_docs 或 salesperson_chunks。
                    """);
            return;
        }
        String host = ToolArgs.text(options, "host", "localhost");
        int port = ToolArgs.integer(options, "port", 19530);
        String source = ToolArgs.text(options, "source", "salesperson_docs");
        String target = ToolArgs.text(options, "target", source + "_hybrid");
        int dim = ToolArgs.integer(options, "dim", 1024);
        int batch = ToolArgs.integer(options, "batch", 200);
        boolean dryRun = ToolArgs.flag(options, "dry-run");
        HybridCollectionGuard.assertShadowTarget(source, target);

        System.out.println("原集合（只读）: " + source);
        System.out.println("影子集合: " + target);
        System.out.println("不会调用 Ollama，不会连接 Postgres，不会修改 " + source + "。");

        MilvusClientV2 client = MilvusToolClient.connect(host, port);
        try {
            if (!MilvusHybridSchema.exists(client, source)) {
                throw new IllegalStateException("原集合不存在: " + source);
            }
            client.loadCollection(io.milvus.v2.service.collection.request.LoadCollectionReq.builder()
                    .collectionName(source)
                    .build());
            long sourceBefore = MilvusHybridSchema.rowCount(client, source);
            boolean targetExists = MilvusHybridSchema.exists(client, target);
            long targetBefore = targetExists ? MilvusHybridSchema.rowCount(client, target) : 0L;
            System.out.println("原集合行数: " + sourceBefore);
            System.out.println("影子集合目前" + (targetExists ? "存在，行数 " + targetBefore : "不存在"));
            if (dryRun) {
                System.out.println("dry-run 结束。正式回填会删除并重建 " + target + "，原集合保持 " + sourceBefore + " 行。");
                return;
            }
            System.out.println("开始回填。向量直接复用，维度参数 " + dim + "，每批 " + batch + " 行。");
            MilvusHybridSchema.dropShadow(client, source, target);
            MilvusHybridSchema.createCollection(client, target, dim);
            MilvusHybridSchema.RebuildStats stats = MilvusHybridSchema.backfill(
                    client, source, target, batch, null);
            MilvusHybridSchema.flush(client, target);
            long sourceAfter = MilvusHybridSchema.rowCount(client, source);
            long targetAfter = MilvusHybridSchema.rowCount(client, target);
            System.out.println("扫描 " + stats.scanned + "，写入 " + stats.inserted
                    + "，跳过 " + stats.skipped + "，失败 " + stats.failed);
            System.out.println("回填后原集合行数: " + sourceAfter + "，影子集合行数: " + targetAfter);
            if (sourceAfter != sourceBefore) {
                throw new IllegalStateException("原集合行数发生变化: " + sourceBefore + " -> " + sourceAfter);
            }
            if (stats.failed > 0) {
                System.err.println("部分行写入失败: " + stats.lastError);
                System.exit(2);
            }
            if (targetAfter != stats.inserted) {
                System.out.println("影子集合统计行数和写入计数不一致，可再执行一次同一条命令。");
            }
            System.out.println("完成。再执行同一条命令会重新覆盖影子集合，仍然不改 " + source + "。");
        } finally {
            client.close();
        }
    }
}
