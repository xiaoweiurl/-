-- ============================================================
-- V58: ERP 数据同步「仅新增」匹配索引
-- ============================================================
-- 为 4 张无唯一约束的业务表创建 md5(业务键) 表达式索引，
-- 用于 ERP 同步时快速匹配存量数据（避免全表扫描 + 避免组合索引超 2704 字节上限）。
--
-- 索引表达式规则（与 ErpDataPersister.buildMd5Expr 完全一致）：
--   键段取值：COALESCE(TRIM(col::text), '')    —— 兼容 integer/text 列、去首尾空格、null 按 ''
--   键段连接：'|' 字符串连接符                  —— || (textcat) 是 IMMUTABLE；
--       ⚠️ 禁止使用 concat_ws：它是 STABLE 函数，索引表达式要求 IMMUTABLE，会报错
--         "索引表达式中函数必需标记为 IMMUTABLE"
--   外层哈希：md5(text) 定长 16 字节 IMMUTABLE
-- Java 侧 ErpDataPersister.joinKey 取值规则与之一一对应（str() 取值带 trim，null 段按 ""）。
-- ============================================================

-- 工艺部件表：货号+颜色+尺码+部件+主编号+机型
CREATE INDEX IF NOT EXISTS idx_buj_match_md5
    ON order_buj_component (md5(
        COALESCE(TRIM(hhname::text), '') || '|' ||
        COALESCE(TRIM(color::text), '') || '|' ||
        COALESCE(TRIM(chima::text), '') || '|' ||
        COALESCE(TRIM(buj::text), '') || '|' ||
        COALESCE(TRIM(zbj::text), '') || '|' ||
        COALESCE(TRIM(jix::text), '')
    ));

-- 工艺工序表：货号+工序+机种+针距+针号+针目
CREATE INDEX IF NOT EXISTS idx_gxp_match_md5
    ON order_gongxu_process (md5(
        COALESCE(TRIM(hhname::text), '') || '|' ||
        COALESCE(TRIM(wtname::text), '') || '|' ||
        COALESCE(TRIM(jizhong::text), '') || '|' ||
        COALESCE(TRIM(zhenju::text), '') || '|' ||
        COALESCE(TRIM(zhenhao::text), '') || '|' ||
        COALESCE(TRIM(zhenmu::text), '')
    ));

-- 工序工价表：货号+工序
CREATE INDEX IF NOT EXISTS idx_gxpr_match_md5
    ON order_gongxu_price (md5(
        COALESCE(TRIM(hhname::text), '') || '|' ||
        COALESCE(TRIM(wtname::text), '')
    ));

-- 原料仓表：货号+颜色+尺码+部件+原料名+规格+批号
CREATE INDEX IF NOT EXISTS idx_rmw_match_md5
    ON raw_material_warehouse (md5(
        COALESCE(TRIM(huohao::text), '') || '|' ||
        COALESCE(TRIM(color::text), '') || '|' ||
        COALESCE(TRIM(size::text), '') || '|' ||
        COALESCE(TRIM(component::text), '') || '|' ||
        COALESCE(TRIM(material_name::text), '') || '|' ||
        COALESCE(TRIM(specification::text), '') || '|' ||
        COALESCE(TRIM(batch_no::text), '')
    ));
