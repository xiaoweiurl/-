package com.imagemanager.imagesearch;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 不启动 Spring、不连业务库。用假图片走 embedding → Milvus image_vectors → 检索 → Recall。
 * 只删除本次写入的 smoke- 向量，不碰 salesperson_* 集合。
 */
public final class ImageSearchPipelineSmoke {

    private ImageSearchPipelineSmoke() {
    }

    /** 与 ImageEmbedClient 一样固定 HTTP/1.1，避免 uvicorn httptools 拒绝 Upgrade: h2c。 */
    static HttpClient embedHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public static void main(String[] args) throws Exception {
        String embedUrl = arg(args, "--embed-url", "http://127.0.0.1:8002");
        String host = arg(args, "--milvus-host", "localhost");
        int port = Integer.parseInt(arg(args, "--milvus-port", "19530"));
        HttpClient http = embedHttpClient();
        HttpResponse<String> health = http.send(HttpRequest.newBuilder(URI.create(embedUrl + "/health"))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (health.statusCode() != 200) {
            throw new IllegalStateException("embedding 服务不可用: " + health.statusCode());
        }
        System.out.println("embedding health: " + health.body());

        ImageSearchProperties properties = new ImageSearchProperties();
        properties.setEnabled(true);
        properties.setEmbedBaseUrl(embedUrl);
        properties.setMilvusHost(host);
        properties.setMilvusPort(port);
        properties.setCollection("image_vectors");
        properties.setDimension(512);
        ImageSearchFilters.assertCollectionName(properties.getCollection());

        ImageEmbedClient embedder = new ImageEmbedClient(properties, new ObjectMapper(), http);
        MilvusImageVectorIndex index = new MilvusImageVectorIndex(properties);
        String redId = "lib:smoke-red";
        String blueId = "lib:smoke-blue";
        try {
            index.initIfEnabled();
            if (!index.isReady()) {
                throw new IllegalStateException("Milvus 图片集合未就绪");
            }
            byte[] red = solid(new Color(220, 30, 30));
            byte[] blue = solid(new Color(30, 60, 220));
            byte[] redScaled = ImageTransforms.scaleHalf(red);
            float[] redVec = embedder.embedImage(red, "red.png");
            float[] blueVec = embedder.embedImage(blue, "blue.png");
            float[] queryVec = embedder.embedImage(redScaled, "red-scaled.jpg");
            index.upsert(ImageVectorRecord.library("smoke-red", "宝娜斯集团", "smoke/red.png", "红色", "", "宝娜斯集团"), redVec);
            index.upsert(ImageVectorRecord.library("smoke-blue", "宝娜斯集团", "smoke/blue.png", "蓝色", "", "宝娜斯集团"), blueVec);

            List<ImageSearchModels.RawHit> hits = waitHits(index, queryVec);
            List<String> ids = new ArrayList<>();
            for (ImageSearchModels.RawHit hit : hits) {
                ids.add(hit.record().vectorId() + ":" + hit.score());
            }
            System.out.println("hits: " + ids);
            double recallAt1 = ImageSearchRecall.meanRecall(List.of(List.of(redId)), List.of(vectorIds(hits)), 1);
            double recallAt5 = ImageSearchRecall.meanRecall(List.of(List.of(redId)), List.of(vectorIds(hits)), 5);
            boolean blueNotFirst = hits.isEmpty() || !blueId.equals(hits.get(0).record().vectorId());
            System.out.println("Recall@1=" + recallAt1 + " Recall@5=" + recallAt5 + " blueNotFirst=" + blueNotFirst);
            if (recallAt1 < 1d || !blueNotFirst) {
                throw new IllegalStateException("假图片检索未命中红色原图");
            }
            long started = System.nanoTime();
            index.search(queryVec, "all", "宝娜斯集团", 5);
            System.out.println("searchLatencyMs=" + ((System.nanoTime() - started) / 1_000_000L));
            System.out.println("SMOKE_OK");
        } finally {
            try {
                index.deleteById(redId);
                index.deleteById(blueId);
            } catch (Exception ignored) {
                System.err.println("清理 smoke 向量失败，可稍后手工删 vector_id lib:smoke-red / lib:smoke-blue");
            }
            index.close();
        }
    }

    private static List<ImageSearchModels.RawHit> waitHits(MilvusImageVectorIndex index, float[] query) throws InterruptedException {
        List<ImageSearchModels.RawHit> hits = List.of();
        for (int i = 0; i < 10; i++) {
            hits = index.search(query, "all", "宝娜斯集团", 5);
            if (!hits.isEmpty()) {
                return hits;
            }
            Thread.sleep(500);
        }
        return hits;
    }

    private static List<String> vectorIds(List<ImageSearchModels.RawHit> hits) {
        List<String> ids = new ArrayList<>();
        for (ImageSearchModels.RawHit hit : hits) {
            ids.add(hit.record().vectorId());
        }
        return ids;
    }

    private static byte[] solid(Color color) throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, 64, 64);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static String arg(String[] args, String name, String fallback) {
        if (args == null) {
            return fallback;
        }
        for (int i = 0; i < args.length; i++) {
            if (name.equals(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        return fallback;
    }
}
