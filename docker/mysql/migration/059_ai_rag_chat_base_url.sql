UPDATE ai_rag_config
SET chat_base_url = 'http://192.168.31.2:8900/v1'
WHERE chat_base_url = 'http://192.168.31.2:8099/v1';
