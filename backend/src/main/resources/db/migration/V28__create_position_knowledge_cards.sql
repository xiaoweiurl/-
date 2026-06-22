-- V28__create_position_knowledge_cards.sql
-- 岗位知识卡片表，按照岗位知识卡片模板V1.0设计
-- 每岗位一卡，季度更新，8大模块完整结构

CREATE TABLE IF NOT EXISTS position_knowledge_cards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 卡片编号与基础信息
    card_no VARCHAR(30) NOT NULL,              -- 卡片编号: KC-________
    submit_date DATE,                           -- 提交日期
    department VARCHAR(100),                    -- 所属部门

    -- 一、岗位基本信息
    position_name VARCHAR(100) NOT NULL,        -- 岗位名称(必填)
    position_holder VARCHAR(100),               -- 在岗人员
    report_to VARCHAR(100),                     -- 汇报上级
    team VARCHAR(100),                          -- 所属团队: 品牌运营/产品开发/供应链/财务/投资委员会
    position_type VARCHAR(20),                  -- 岗位性质: 全职/兼职/顾问/Agent辅助

    -- 二、岗位职责
    core_duties TEXT NOT NULL,                  -- 核心职责(不超过5条，JSON数组格式)
    auxiliary_duties TEXT,                      -- 辅助职责(JSON数组格式)

    -- 三、关键产出物
    key_outputs TEXT,                           -- 关键产出物(JSON数组: [{name, frequency, deliver_to, standard}])

    -- 四、能力要求
    hard_skills TEXT,                           -- 硬技能(JSON数组)
    soft_skills TEXT,                           -- 软技能(JSON数组)

    -- 五、协作关系
    upstream_inputs TEXT,                       -- 上游输入(JSON数组: [{position, content, frequency}])
    downstream_outputs TEXT,                    -- 下游输出(JSON数组: [{position, content, frequency}])

    -- 六、当前状态
    completed_work TEXT,                        -- 已完成的主要工作
    ongoing_work TEXT,                          -- 当前进行中
    bottlenecks TEXT,                           -- 卡点和瓶颈
    support_needed TEXT,                        -- 需要的支持

    -- 七、改进计划
    self_improvement TEXT,                      -- 本人提升方向
    process_improvement TEXT,                   -- 流程优化建议
    tool_resource_needs TEXT,                   -- 工具/资源需求

    -- 八、补充说明
    additional_notes TEXT,                      -- 补充说明

    -- 系统字段
    status VARCHAR(20) DEFAULT 'draft',         -- 状态: draft/published/archived
    user_id VARCHAR(100),                       -- 创建人ID
    company VARCHAR(50),                        -- 所属公司(数据隔离)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_pkc_card_no ON position_knowledge_cards(card_no);
CREATE INDEX IF NOT EXISTS idx_pkc_position_name ON position_knowledge_cards(position_name);
CREATE INDEX IF NOT EXISTS idx_pkc_team ON position_knowledge_cards(team);
CREATE INDEX IF NOT EXISTS idx_pkc_user_company ON position_knowledge_cards(user_id, company);
CREATE INDEX IF NOT EXISTS idx_pkc_status ON position_knowledge_cards(status);
CREATE INDEX IF NOT EXISTS idx_pkc_created ON position_knowledge_cards(created_at DESC);
