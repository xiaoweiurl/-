-- V58: ERP「仅新增同步」批量匹配索引
-- 无唯一约束的业务表（order_buj_component / order_gongxu_process / order_gongxu_price / raw_material_warehouse）
-- 使用 md5(业务键) 表达式索引：
--   1. 分批 IN 查询匹配走索引扫描，避免全表扫描与 N+1
--   2. md5 定长 16 字节，规避多列 varchar(500) 组合索引超 2704 字节上限的风险
-- 注意（两处易错点，同步侧 Java 计算的 md5 规则必须与下方 SQL 表达式完全一致）：
--   1. 所有键列统一 ::text 转换（order_buj_component.zbj 为 integer，直接 COALESCE(int_col,'') 会报
--      "无效的类型 integer 输入语法"）
--   2. 所有键列统一 TRIM（Java 侧取值带 trim，SQL 侧必须同步，否则历史带空格数据匹配失败被误判为新增）
--   规则：md5(concat_ws('|', COALESCE(TRIM(col::text),'') ...))，Java 侧 null 键段按 "" 参与拼接

CREATE INDEX IF NOT EXISTS idx_buj_match_md5 ON order_buj_component (
    md5(concat_ws('|',
        COALESCE(TRIM(hhname::text), ''), COALESCE(TRIM(color::text), ''), COALESCE(TRIM(chima::text), ''),
        COALESCE(TRIM(buj::text), ''), COALESCE(TRIM(zbj::text), ''), COALESCE(TRIM(jix::text), '')))
);

CREATE INDEX IF NOT EXISTS idx_gxp_match_md5 ON order_gongxu_process (
    md5(concat_ws('|',
        COALESCE(TRIM(hhname::text), ''), COALESCE(TRIM(wtname::text), ''), COALESCE(TRIM(jizhong::text), ''),
        COALESCE(TRIM(zhenju::text), ''), COALESCE(TRIM(zhenhao::text), ''), COALESCE(TRIM(zhenmu::text), '')))
);

CREATE INDEX IF NOT EXISTS idx_gxpr_match_md5 ON order_gongxu_price (
    md5(concat_ws('|', COALESCE(TRIM(hhname::text), ''), COALESCE(TRIM(wtname::text), '')))
);

CREATE INDEX IF NOT EXISTS idx_rmw_match_md5 ON raw_material_warehouse (
    md5(concat_ws('|',
        COALESCE(TRIM(huohao::text), ''), COALESCE(TRIM(color::text), ''), COALESCE(TRIM(size::text), ''),
        COALESCE(TRIM(component::text), ''), COALESCE(TRIM(material_name::text), ''),
        COALESCE(TRIM(specification::text), ''), COALESCE(TRIM(batch_no::text), '')))
);
