package com.imagemanager.imagesearch;

import java.util.ArrayList;
import java.util.List;

/**
 * 图文一起搜时的结构化条件。只保留库里真实存在的字段，软属性没有列，只用于重排。
 */
public final class ImageSearchCondition {

    private ImageSearchCondition() {
    }

    public interface Lexicon {
        List<String> samplerNames();

        List<String> albumNames();

        Lexicon EMPTY = new Lexicon() {
            @Override
            public List<String> samplerNames() {
                return List.of();
            }

            @Override
            public List<String> albumNames() {
                return List.of();
            }
        };
    }

    /**
     * 可选的补充抽取。只接受词典里已有的打样员、相册，以及已知的颜色/材质/款式词。
     * 本地没有便宜的抽取接口时用 {@link #none()}。
     */
    public record Hint(String scope, String sampler, String album, List<String> attributes) {
        public Hint {
            attributes = attributes == null ? List.of() : List.copyOf(attributes);
        }

        public static Hint none() {
            return new Hint(null, null, null, List.of());
        }
    }

    public record Window(long fromEpochMs, long toEpochMs, String label) {
    }

    public record Chip(String id, String kind, String label, String value, boolean applied, boolean relaxed) {
        public Chip relaxedCopy() {
            return new Chip(id, kind, label, value, false, true);
        }
    }

    public record Parsed(
            String scope,
            Window time,
            String sampler,
            String album,
            List<String> attributes,
            List<Chip> chips
    ) {
        public Parsed {
            attributes = attributes == null ? List.of() : List.copyOf(attributes);
            chips = chips == null ? List.of() : List.copyOf(chips);
        }

        public static Parsed empty() {
            return new Parsed(null, null, null, null, List.of(), List.of());
        }

        public boolean needsPostFilter() {
            return time != null || (sampler != null && !sampler.isBlank()) || (album != null && !album.isBlank());
        }

        public boolean hasAttributes() {
            return attributes != null && !attributes.isEmpty();
        }

        public boolean active() {
            return (scope != null && !scope.isBlank()) || needsPostFilter() || hasAttributes();
        }

        public Parsed withoutScope() {
            if (scope == null) {
                return this;
            }
            List<Chip> kept = new ArrayList<>();
            for (Chip chip : chips) {
                if (!"scope".equals(chip.id())) {
                    kept.add(chip);
                }
            }
            return new Parsed(null, time, sampler, album, attributes, kept);
        }

        public String attributeText() {
            return String.join(" ", attributes);
        }
    }
}
