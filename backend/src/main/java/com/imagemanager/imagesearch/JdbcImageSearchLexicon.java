package com.imagemanager.imagesearch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 打样员和相册只从现有表读，解析时对不上就不生成硬条件。
 */
@Slf4j
@Component
public class JdbcImageSearchLexicon implements ImageSearchCondition.Lexicon {

    private static final long TTL_MS = 60_000L;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate txTemplate;
    private final Object lock = new Object();
    private volatile List<String> samplers = List.of();
    private volatile List<String> albums = List.of();
    private volatile long loadedAt;

    public JdbcImageSearchLexicon(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public List<String> samplerNames() {
        refresh();
        return samplers;
    }

    @Override
    public List<String> albumNames() {
        refresh();
        return albums;
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        if (now - loadedAt < TTL_MS && loadedAt > 0) {
            return;
        }
        synchronized (lock) {
            if (System.currentTimeMillis() - loadedAt < TTL_MS && loadedAt > 0) {
                return;
            }
            Set<String> samplerValues = new LinkedHashSet<>();
            samplerValues.addAll(column("SELECT DISTINCT btrim(sampler) AS name FROM goods_library "
                    + "WHERE sampler IS NOT NULL AND btrim(sampler) <> ''"));
            samplerValues.addAll(column("SELECT DISTINCT btrim(nickname) AS name FROM users "
                    + "WHERE nickname IS NOT NULL AND btrim(nickname) <> ''"));
            samplerValues.addAll(column("SELECT DISTINCT btrim(username) AS name FROM users "
                    + "WHERE username IS NOT NULL AND btrim(username) <> ''"));
            Set<String> albumValues = new LinkedHashSet<>();
            albumValues.addAll(column("SELECT DISTINCT btrim(album_name) AS name FROM images "
                    + "WHERE album_name IS NOT NULL AND btrim(album_name) <> ''"));
            albumValues.addAll(column("SELECT DISTINCT btrim(name) AS name FROM albums "
                    + "WHERE name IS NOT NULL AND btrim(name) <> ''"));
            samplers = List.copyOf(samplerValues);
            albums = List.copyOf(albumValues);
            loadedAt = System.currentTimeMillis();
        }
    }

    private List<String> column(String sql) {
        try {
            List<java.util.Map<String, Object>> rows = txTemplate.execute(status -> jdbcTemplate.queryForList(sql));
            List<String> names = new ArrayList<>();
            if (rows == null) {
                return names;
            }
            for (java.util.Map<String, Object> row : rows) {
                Object value = row.get("name");
                if (value != null && !value.toString().isBlank()) {
                    names.add(value.toString().trim());
                }
            }
            return names;
        } catch (Exception e) {
            log.warn("读取以图搜图筛选词典失败: {}", e.getMessage());
            return List.of();
        }
    }
}
