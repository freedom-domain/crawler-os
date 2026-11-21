UPDATE sys_permission config
JOIN sys_permission knowledge ON knowledge.code = 'ai:knowledge' AND knowledge.deleted = 0
SET config.name = '管理 RAG 配置',
    config.parent_id = knowledge.id,
    config.type = 2,
    config.path = NULL,
    config.icon = NULL,
    config.sort = 1,
    config.deleted = 0
WHERE config.code = 'ai:config';
