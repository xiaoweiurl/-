package com.imagemanager.imagesearch;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * 检索执行器和对话检索都有测试用短构造器。Spring 必须走标了 {@code @Autowired} 的长构造器，
 * 否则启动会退回不存在的无参构造。
 */
class ImageSearchRecordLinksWiringTest {

    @Test
    void springUsesTheInjectionConstructors() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(ImageSearchProperties.class, ImageSearchProperties::new);
            ctx.registerBean(ImageSearchQueryService.class, () -> mock(ImageSearchQueryService.class));
            ctx.registerBean(ImageSearchConditionApplier.class, () -> mock(ImageSearchConditionApplier.class));
            ctx.registerBean(ImageSearchRecordLinks.class, () -> mock(ImageSearchRecordLinks.class));
            ctx.register(VisualSearchExecutor.class);
            ctx.register(ChatVisualSearchService.class);
            ctx.refresh();

            assertNotNull(ctx.getBean(VisualSearchExecutor.class));
            assertNotNull(ctx.getBean(ChatVisualSearchService.class));
        }
    }
}
