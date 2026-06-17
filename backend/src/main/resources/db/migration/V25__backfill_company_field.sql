-- V25: 修复历史数据中 company 为 NULL 的记录
-- 将 knowledge_embeddings 中 company 为 NULL 的记录根据关联表回填公司名

-- 1. 知识库类型的 embedding: 通过 knowledge_base_docs 表回填 company
UPDATE knowledge_embeddings e
SET company = d.company
FROM knowledge_base_docs d
WHERE e.source_doc_id = d.id::text
  AND e.source_type = 'KNOWLEDGE_BASE'
  AND e.company IS NULL
  AND d.company IS NOT NULL;

-- 2. 记忆库类型的 embedding: 通过 knowledge_cards 表回填 company
UPDATE knowledge_embeddings e
SET company = c.company
FROM knowledge_cards c
WHERE e.card_id = c.id
  AND e.source_type = 'MEMORY'
  AND e.company IS NULL
  AND c.company IS NOT NULL;

-- 3. 如果关联表也没有 company，则设置默认值 '盈云'
UPDATE knowledge_embeddings SET company = '盈云' WHERE company IS NULL;

-- 4. 回填 knowledge_base_docs 中 company 为 NULL 的记录（通过用户表获取公司）
UPDATE knowledge_base_docs d
SET company = u.company
FROM users u
WHERE d.user_id = u.id::text
  AND d.company IS NULL
  AND u.company IS NOT NULL;

-- 5. 如果用户表也没有 company，则设置默认值
UPDATE knowledge_base_docs SET company = '盈云' WHERE company IS NULL;

-- 6. 回填 knowledge_cards 中 company 为 NULL 的记录
UPDATE knowledge_cards c
SET company = u.company
FROM users u
WHERE c.user_id = u.id::text
  AND c.company IS NULL
  AND u.company IS NOT NULL;

UPDATE knowledge_cards SET company = '盈云' WHERE company IS NULL;

-- 7. 回填 knowledge_domains 中 company 为 NULL 的记录
UPDATE knowledge_domains d
SET company = u.company
FROM users u
WHERE d.created_by = u.id::text
  AND d.company IS NULL
  AND u.company IS NOT NULL;

UPDATE knowledge_domains SET company = '盈云' WHERE company IS NULL;
