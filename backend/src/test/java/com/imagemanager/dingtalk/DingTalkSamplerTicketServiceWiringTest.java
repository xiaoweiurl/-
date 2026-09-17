package com.imagemanager.dingtalk;

import com.imagemanager.config.DingTalkProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Reproduces the #23 startup failure: two constructors without {@code @Autowired}
 * made Spring use SimpleInstantiationStrategy (no-arg) and throw
 * {@code NoSuchMethodException: DingTalkSamplerTicketService.<init>()}.
 */
class DingTalkSamplerTicketServiceWiringTest {

    @Test
    void springWiresInjectionConstructorWithoutNoArgCtor() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(DingTalkProperties.class, DingTalkProperties::new);
            ctx.registerBean(DingTalkFreeLoginService.class, () -> mock(DingTalkFreeLoginService.class));
            ctx.register(DingTalkSamplerTicketService.class);
            ctx.refresh();

            assertTrue(ctx.containsBean("dingTalkSamplerTicketService"));
            DingTalkSamplerTicketService service = ctx.getBean(DingTalkSamplerTicketService.class);
            assertNotNull(service, "must be created via the 3-arg constructor");
        }
    }
}
