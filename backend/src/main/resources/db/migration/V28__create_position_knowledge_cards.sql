-- ============================================================
-- 岗位知识卡片表（按岗位知识卡片模板V1.0设计）
-- V28__create_position_knowledge_cards.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS position_knowledge_cards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 卡片元信息
    card_no VARCHAR(50) UNIQUE,              -- 卡片编号: KC-XXXXXXXX
    submit_date DATE,                         -- 提交日期
    department VARCHAR(100),                  -- 所属部门

    -- 一、岗位基本信息
    position_name VARCHAR(100) NOT NULL,      -- 岗位名称
    position_holder VARCHAR(100),             -- 在岗人员
    report_to VARCHAR(100),                   -- 汇报上级
    team VARCHAR(100),                        -- 所属团队: 品牌运营(携创云织)/产品开发(盈云)/供应链/财务/投资委员会
    position_nature VARCHAR(20),              -- 岗位性质: 全职/兼职/顾问/Agent辅助

    -- 二、岗位职责
    core_responsibilities TEXT,               -- 核心职责(不超过5条,JSON数组格式)
    auxiliary_responsibilities TEXT,           -- 辅助职责(JSON数组格式)

    -- 三、关键产出物
    key_deliverables TEXT,                    -- 关键产出物(JSON数组: [{name, frequency, deliver_to, standard}])

    -- 四、能力要求
    hard_skills TEXT,                         -- 硬技能(JSON数组)
    soft_skills TEXT,                         -- 软技能(JSON数组)

    -- 五、协作关系
    upstream_inputs TEXT,                     -- 上游输入(JSON数组: [{position, content, frequency}])
    downstream_outputs TEXT,                  -- 下游输出(JSON数组: [{position, content, frequency}])

    -- 六、当前状态
    completed_work TEXT,                      -- 已完成的主要工作(本季度)
    ongoing_work TEXT,                        -- 当前进行中
    bottlenecks TEXT,                         -- 卡点和瓶颈
    support_needed TEXT,                      -- 需要的支持

    -- 七、改进计划
    improvement_direction TEXT,               -- 本人提升方向
    process_optimization TEXT,                -- 流程优化建议
    tool_resource_needs TEXT,                 -- 工具/资源需求

    -- 八、补充说明
    additional_notes TEXT,                    -- 补充说明

    -- 系统字段
    status VARCHAR(20) DEFAULT 'draft',       -- 状态: draft/published/archived
    company VARCHAR(50),                      -- 所属公司(数据隔离)
    user_id VARCHAR(100) NOT NULL,            -- 创建人
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_pkc_card_no ON position_knowledge_cards(card_no);
CREATE INDEX IF NOT EXISTS idx_pkc_position_name ON position_knowledge_cards(position_name);
CREATE INDEX IF NOT EXISTS idx_pkc_department ON position_knowledge_cards(department);
CREATE INDEX IF NOT EXISTS idx_pkc_team ON position_knowledge_cards(team);
CREATE INDEX IF NOT EXISTS idx_pkc_company ON position_knowledge_cards(company);
CREATE INDEX IF NOT EXISTS idx_pkc_user ON position_knowledge_cards(user_id);
CREATE INDEX IF NOT EXISTS idx_pkc_status ON position_knowledge_cards(status);
