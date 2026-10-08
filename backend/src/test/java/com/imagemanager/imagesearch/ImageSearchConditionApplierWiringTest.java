package com.imagemanager.imagesearch;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Reproduces the post-#43 startup failure: two constructors without {@code @Autowired}
 * made Spring use SimpleInstantiationStrategy (no-arg) and throw
 * {@code NoSuchMethodException: ImageSearchConditionApplier.<init>()}.
 */
class ImageSearchConditionApplierWiringTest {

    @Test
    void springWiresInjectionConstructorWithoutNoArgCtor() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(ImageSearchProperties.class, ImageSearchProperties::new);
            ctx.registerBean(ImageSearchCondition.Lexicon.class, () -> ImageSearchCondition.Lexicon.EMPTY);
            ctx.registerBean(ImageEmbedder.class, () -> mock(ImageEmbedder.class));
            ctx.registerBean(ImageVectorIndex.class, () -> mock(ImageVectorIndex.class));
            ctx.register(ImageSearchConditionApplier.class);
            ctx.refresh();

            assertTrue(ctx.containsBean("imageSearchConditionApplier"));
            ImageSearchConditionApplier applier = ctx.getBean(ImageSearchConditionApplier.class);
            assertNotNull(applier, "must be created via the 4-arg constructor");
            assertEquals(ImageSearchCondition.Parsed.empty(), applier.parse("  ", null));
        }
    }
}
