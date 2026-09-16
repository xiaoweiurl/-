package com.imagemanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ErpDataPersisterMaterialMappingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void buildMaterialArgsMapsBwjAndSampleFields() throws Exception {
        JsonNode row = MAPPER.readTree("""
                {"hhname":"NLTTA03","color":"","chima":"","bwj":"天丝",
                 "gys":"江苏万源纤维科技有限公司","wlname":"JY-0030/24","guige":"40S/2 24s",
                 "wlcolor":"","pihao":"34530","nianx":"5","dw":"KG","djyl":12.00000,
                 "sh":3.00000,"bl":12.00000,"price":"","je":"","remark":""}
                """);
        Object[] args = invokeBuildMaterialArgs(row);
        assertEquals("NLTTA03", args[0]);
        assertNull(args[1]);
        assertNull(args[2]);
        assertEquals("天丝", args[3]);
        assertEquals("江苏万源纤维科技有限公司", args[4]);
        assertEquals("JY-0030/24", args[5]);
        assertEquals("40S/2 24s", args[6]);
        assertNull(args[7]);
        assertEquals("34530", args[8]);
        assertEquals("5", args[9]);
        assertEquals("KG", args[10]);
        assertEquals(0, new BigDecimal("12").compareTo((BigDecimal) args[11]));
        assertEquals(0, new BigDecimal("3").compareTo((BigDecimal) args[12]));
        assertNull(args[13]);
    }

    @Test
    void buildMaterialArgsFallsBackToBujWhenBwjMissing() throws Exception {
        JsonNode row = MAPPER.readTree("{\"hhname\":\"X\",\"buj\":\"裤身\",\"wlname\":\"M\"}");
        Object[] args = invokeBuildMaterialArgs(row);
        assertEquals("裤身", args[3]);
    }

    private static Object[] invokeBuildMaterialArgs(JsonNode row) throws Exception {
        Method method = ErpDataPersister.class.getDeclaredMethod("buildMaterialArgs", JsonNode.class);
        method.setAccessible(true);
        return (Object[]) method.invoke(null, row);
    }
}
