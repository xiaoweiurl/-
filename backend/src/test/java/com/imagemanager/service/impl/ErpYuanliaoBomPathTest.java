package com.imagemanager.service.impl;

import com.imagemanager.config.ErpProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErpYuanliaoBomPathTest {

    @Test
    void yuanliaoBomUsesTechnologyMaterialYLQuery() throws Exception {
        assertEquals("Technology/MaterialYLQuery", moduleEndpoint("yuanliao-bom"));
    }

    @Test
    void resolveApiUrlAppendsAspxLikeOtherModules() {
        ErpProperties properties = new ErpProperties();
        properties.setBaseUrl("http://mpro42.ywhzsoft.com/netWf2024Unitive_Ent");
        assertEquals(
                "http://mpro42.ywhzsoft.com/netWf2024Unitive_Ent/Technology/MaterialYLQuery.aspx",
                properties.resolveApiUrl("Technology/MaterialYLQuery"));
        assertEquals(
                "http://mpro42.ywhzsoft.com/netWf2024Unitive_Ent/Technology/NGyMainQuery.aspx",
                properties.resolveApiUrl("Technology/NGyMainQuery"));
    }

    @Test
    void nonOrderModulesShareTechnologyPrefix() throws Exception {
        for (Object module : modules()) {
            String key = accessor(module, "key");
            String endpoint = accessor(module, "endpoint");
            if ("orders".equals(key)) {
                continue;
            }
            assertTrue(endpoint.startsWith("Technology/"),
                    key + " should use Technology/ prefix, got " + endpoint);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> modules() throws Exception {
        Field field = ErpSyncServiceImpl.class.getDeclaredField("MODULES");
        field.setAccessible(true);
        return (List<Object>) field.get(null);
    }

    private static String moduleEndpoint(String moduleKey) throws Exception {
        return modules().stream()
                .filter(m -> moduleKey.equals(accessor(m, "key")))
                .map(m -> accessor(m, "endpoint"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing module " + moduleKey));
    }

    private static String accessor(Object record, String name) {
        try {
            Method method = record.getClass().getDeclaredMethod(name);
            return (String) method.invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
