# CollectX 爬虫管理平台

微服务 + 爬虫任务调度平台，基于 Spring Cloud Alibaba + Vue3。

## 技术栈

| 模块 | 技术 |
|------|------|
| 网关 | Spring Cloud Gateway |
| 注册/配置中心 | Nacos |
| 后端服务 | Spring Boot 3.2 + JDK 17 |
| ORM | MyBatis-Plus |
| 消息队列 | Kafka |
| 数据库 | MySQL 8.0 |
| 搜索 | Elasticsearch 7.17 |
| 对象存储 | MinIO |
| 爬虫引擎 | Jsoup + OkHttp |
| 前端 | Vue 3 + TypeScript + Element Plus + Vite |

## 项目结构

```
collect-x/
├── crawler-common            # 公共模块
├── crawler-gateway           # API 网关 (8080)
├── crawler-user-service      # 用户/角色/权限 (8081)
├── crawler-spider-service    # 爬虫/任务/调度 (8082)
├── crawler-search-service    # 数据搜索 (8083)
├── crawler-file-service      # 文件管理 (8084)
├── crawler-worker            # 爬虫执行集群 (8090)
├── crawler-admin-web         # 前端 (5173)
├── docker/                   # 初始化脚本
└── docker-compose.yml        # 全栈部署
```

## 架构

```
Web Admin (Vue3)
      │ HTTPS
      ▼
API Gateway (8080)
      │
 ┌────┼───────────┬──────────┐
 │    │           │          │
User  Spider     Search     File
 │    │           │          │
 └────┴─────┬─────┴──────────┘
            │
    MySQL / ES / MinIO
             │
          Kafka
             │
        Worker 1..N (Jsoup 抓取)
```

## 快速开始

### 1. 启动中间件

```bash
docker-compose up -d mysql redis elasticsearch minio nacos kafka
```

等待所有容器健康后：
- MySQL: `127.0.0.1:3306` (root/root123, 库 crawler_platform 自动初始化)
- ES: `127.0.0.1:9200`
- MinIO: `127.0.0.1:9000` (控制台 9001, admin/admin123)
- Nacos: `127.0.0.1:8848`
- Kafka: `127.0.0.1:9092`

> IK 分词器：需手动将 IK 插件放入 ES 的 plugins 目录后重启，否则搜索退回标准分词。

### 2. 启动后端服务

本地运行（需 JDK 17）：

```bash
# 依次启动（每个模块独立端口）
mvn -pl crawler-gateway spring-boot:run
mvn -pl crawler-user-service spring-boot:run
mvn -pl crawler-spider-service spring-boot:run
mvn -pl crawler-search-service spring-boot:run
mvn -pl crawler-file-service spring-boot:run
mvn -pl crawler-worker spring-boot:run
```

或 Docker 一键启动全部应用：

```bash
docker-compose up -d crawler-gateway crawler-user-service crawler-spider-service \
  crawler-search-service crawler-file-service crawler-worker
```

### 3. 启动前端

```bash
cd crawler-admin-web
npm install
npm run dev        # 开发模式 http://127.0.0.1:5173
npm run build      # 生产构建到 dist/
```

### 4. 登录

默认账号（密码均为 `admin123`）：
- 管理员: `admin`
- 普通用户: `user`

## 核心流程

1. 前端创建爬虫（配置起始URL、类型、深度、调度）
2. spider-service 生成任务记录，投递 Kafka
3. worker 消费消息，Jsoup 抓取页面
4. 抓取内容写入 ES（全文检索）+ 原始 HTML 存入 MinIO
5. 任务状态/日志回写 MySQL
6. 前端 Dashboard 展示统计，搜索页查询 ES 数据

## 端口清单

| 服务 | 端口 |
|------|------|
| Gateway | 8080 |
| User Service | 8081 |
| Spider Service | 8082 |
| Search Service | 8083 |
| File Service | 8084 |
| Worker | 8090 |
| 前端 Dev | 5173 |
| Nacos | 8848 |
| MySQL | 3306 |
| ES | 9200 |
| Kibana | 5601 |
| MinIO | 9000 / 9001 |
| Kafka | 9092 |
| Redis | 6379 |

## Swagger 文档

各服务启动后访问 `http://127.0.0.1:端口/swagger-ui.html`。

杀掉全部服务进程

pkill -9 -f 'Application'


freedom.domain.email@gmail.com


sk-3N_e2cnzIc0cjlxUJ5u2sRCQLvLBUHKJKEOCirVPkDo