package com.imagemanager.dingtalk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imagemanager.config.DingTalkProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces the phase-1 startup failure: two constructors without {@code @Autowired}
 * made Spring use SimpleInstantiationStrategy (no-arg) and throw
 * {@code NoSuchMethodException: DingTalkClient.<init>()}.
 */
class DingTalkClientWiringTest {

    @Test
    void springWiresInjectionConstructorWithoutNoArgCtor() {
        try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
            ctx.registerBean(DingTalkProperties.class, () -> new DingTalkProperties());
            ctx.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            ctx.register(DingTalkClient.class);
            ctx.refresh();

            assertTrue(ctx.containsBean("dingTalkClient"));
            DingTalkClient client = ctx.getBean(DingTalkClient.class);
            assertNotNull(client, "dingTalkClient must be created via the 2-arg constructor");
            assertFalse(client.isConfigured(), "empty credentials must still wire; sync fails later");
        }
    }
}
