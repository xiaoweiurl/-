package com.imagemanager.imagesearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.enhance.Reranker;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImageEmbedHttpClientTest {

    @Test
    void embedClientsUseHttp11() throws Exception {
        ImageEmbedClient client = new ImageEmbedClient(new ImageSearchProperties(), new ObjectMapper());
        assertEquals(HttpClient.Version.HTTP_1_1, httpClient(client, "httpClient").version());
        assertEquals(HttpClient.Version.HTTP_1_1, ImageSearchPipelineSmoke.embedHttpClient().version());
    }

    @Test
    void rerankerUsesHttp11() throws Exception {
        Reranker reranker = new Reranker();
        assertEquals(HttpClient.Version.HTTP_1_1, httpClient(reranker, "httpClient").version());
    }

    private static HttpClient httpClient(Object owner, String fieldName) throws Exception {
        Field field = owner.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (HttpClient) field.get(owner);
    }
}
