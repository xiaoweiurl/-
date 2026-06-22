package com.imagemanager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 岗位知识卡片实体 - 按岗位知识卡片模板V1.0设计
 * 包含8大模块：岗位基本信息、岗位职责、关键产出物、能力要求、协作关系、当前状态、改进计划、补充说明
 */
@Entity
@Table(name = "position_knowledge_cards")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionKnowledgeCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;

    // ===== 卡片元信息 =====
    @Column(name = "card_code", length = 50, nullable = false)
    private String cardCode;        // 卡片编号: KC-XXXXXXXX

    @Column(name = "submit_date", length = 20)
    private String submitDate;      // 提交日期: 20XX年XX月XX日

    @Column(name = "department", length = 100)
    private String department;      // 所属部门

    // ===== 一、岗位基本信息 =====
    @Column(name = "position_name", length = 100, nullable = false)
    private String positionName;    // 岗位名称

    @Column(name = "on_duty_person", length = 100)
    private String onDutyPerson;    // 在岗人员

    @Column(name = "report_to", length = 100)
    private String reportTo;        // 汇报上级

    @Column(name = "team", length = 100)
    private String team;            // 所属团队: 品牌运营(携创云织)/产品开发(盈云)/供应链/财务/投资委员会

    @Column(name = "position_nature", length = 50)
    private String positionNature;  // 岗位性质: 全职/兼职/顾问/Agent辅助

    // ===== 二、岗位职责 =====
    @Column(name = "core_duties", columnDefinition = "TEXT")
    private String coreDuties;      // 核心职责(JSON数组,不超过5条)

    @Column(name = "auxiliary_duties", columnDefinition = "TEXT")
    private String auxiliaryDuties; // 辅助职责(JSON数组)

    // ===== 三、关键产出物 =====
    @Column(name = "key_outputs", columnDefinition = "TEXT")
    private String keyOutputs;      // 关键产出物(JSON数组: [{deliverable,frequency,recipient,standard}])

    // ===== 四、能力要求 =====
    @Column(name = "hard_skills", columnDefinition = "TEXT")
    private String hardSkills;      // 硬技能(JSON数组: 工具/技术/资质)

    @Column(name = "soft_skills", columnDefinition = "TEXT")
    private String softSkills;      // 软技能(JSON数组: 沟通/判断/协作)

    // ===== 五、协作关系 =====
    @Column(name = "upstream_inputs", columnDefinition = "TEXT")
    private String upstreamInputs;  // 上游输入(JSON数组: [{position,content,frequency}])

    @Column(name = "downstream_outputs", columnDefinition = "TEXT")
    private String downstreamOutputs; // 下游输出(JSON数组: [{position,content,frequency}])

    // ===== 六、当前状态 =====
    @Column(name = "completed_work", columnDefinition = "TEXT")
    private String completedWork;   // 已完成的主要工作(本季度)

    @Column(name = "in_progress", columnDefinition = "TEXT")
    private String inProgress;      // 当前进行中

    @Column(name = "bottlenecks", columnDefinition = "TEXT")
    private String bottlenecks;     // 卡点和瓶颈

    @Column(name = "support_needed", columnDefinition = "TEXT")
    private String supportNeeded;   // 需要的支持

    // ===== 七、改进计划 =====
    @Column(name = "improvement_direction", columnDefinition = "TEXT")
    private String improvementDirection; // 本人提升方向

    @Column(name = "process_optimization", columnDefinition = "TEXT")
    private String processOptimization;  // 流程优化建议

    @Column(name = "tool_resource_needs", columnDefinition = "TEXT")
    private String toolResourceNeeds;    // 工具/资源需求

    // ===== 八、补充说明 =====
    @Column(name = "additional_notes", columnDefinition = "TEXT")
    private String additionalNotes; // 补充说明

    // ===== 向量化状态 =====
    @Column(name = "embedding_status", length = 20)
    private String embeddingStatus;   // 向量化状态: PENDING/PROCESSING/COMPLETED/FAILED

    // ===== 系统字段 =====
    @Column(name = "user_id", length = 100, nullable = false)
    private String userId;

    @Column(name = "company", length = 50, nullable = false)
    private String company;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
