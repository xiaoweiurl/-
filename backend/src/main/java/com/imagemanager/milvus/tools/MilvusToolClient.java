package com.imagemanager.milvus.tools;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;

public final class MilvusToolClient {

    private MilvusToolClient() {
    }

    public static MilvusClientV2 connect(String host, int port) {
        try {
            return new MilvusClientV2(ConnectConfig.builder()
                    .uri("http://" + host + ":" + port)
                    .connectTimeoutMs(10_000)
                    .build());
        } catch (RuntimeException e) {
            throw new IllegalStateException("连不上 Milvus " + host + ":" + port + "。" + e.getMessage(), e);
        }
    }
}
