-- V58: ERP「仅新增同步」批量匹配索引
-- 无唯一约束的业务表（order_buj_component / order_gongxu_process / order_gongxu_price / raw_material_warehouse）
-- 使用 md5(业务键) 表达式索引：
--   1. 分批 IN 查询匹配走索引扫描，避免全表扫描与 N+1
--   2. md5 定长 16 字节，规避多列 varchar(500) 组合索引超 2704 字节上限的风险
-- 同步侧 Java 计算的 md5 规则必须与下方 SQL 表达式完全一致（UTF-8 + 小写 hex + COALESCE(col,'') 以 '|' 连接）

CREATE INDEX IF NOT EXISTS idx_buj_match_md5 ON order_buj_component (
    md5(concat_ws('|',
        COALESCE(hhname, ''), COALESCE(color, ''), COALESCE(chima, ''),
        COALESCE(buj, ''), COALESCE(zbj, ''), COALESCE(jix, '')))
);

CREATE INDEX IF NOT EXISTS idx_gxp_match_md5 ON order_gongxu_process (
    md5(concat_ws('|',
        COALESCE(hhname, ''), COALESCE(wtname, ''), COALESCE(jizhong, ''),
        COALESCE(zhenju, ''), COALESCE(zhenhao, ''), COALESCE(zhenmu, '')))
);

CREATE INDEX IF NOT EXISTS idx_gxpr_match_md5 ON order_gongxu_price (
    md5(concat_ws('|', COALESCE(hhname, ''), COALESCE(wtname, '')))
);

CREATE INDEX IF NOT EXISTS idx_rmw_match_md5 ON raw_material_warehouse (
    md5(concat_ws('|',
        COALESCE(huohao, ''), COALESCE(color, ''), COALESCE(size, ''),
        COALESCE(component, ''), COALESCE(material_name, ''),
        COALESCE(specification, ''), COALESCE(batch_no, '')))
);
