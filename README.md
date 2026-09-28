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
| 爬虫引擎 | WebMagic + OkHttp |
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
        Worker 1..N (WebMagic 抽取)
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

Kafka 任务 topic 按环境隔离：默认环境使用 `spider_task_topic-default`，本地环境使用
`spider_task_topic-local`，避免本地和 Docker 环境互相消费爬虫任务。

程序日志会携带 `environment` 字段：本地 Profile 为 `local`，未激活 Profile 的 Docker/默认环境为
`default`。Logstash 查询时请按该字段过滤，避免混看不同环境日志。

> IK 分词器：需手动将 IK 插件放入 ES 的 plugins 目录后重启，否则搜索退回标准分词。

### 2. 启动后端服务

本地构建后端（需 JDK 21 和本地 Maven；只生成各模块的 JAR，不构建 Docker 镜像）：

```bash
./scripts/build-local.sh
```

构建脚本会检查 Maven 实际使用的 Java 版本。若提示低于 21，请将 `JAVA_HOME` 指向 JDK 21 或更高版本，并将其 `bin` 目录加入 `PATH`；可用 `mvn -version` 确认 Maven 使用的 JDK。

构建产物位于各模块的 `target/` 目录。只构建某个服务及其 Maven 依赖时，例如：

```bash
./scripts/build-local.sh -pl crawler-gateway -am
```

本地运行后端（需 JDK 21）：

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
./scripts/build-local.sh
docker-compose up -d crawler-gateway crawler-user-service crawler-spider-service \
  crawler-search-service crawler-file-service crawler-worker crawler-admin-web
```

Compose 使用本地专用的 `docker/Dockerfile-app-local`，仅提供 JRE 运行环境并复制本机 Maven 生成的 JAR；请先执行本地 Maven 构建。

### 本地 Docker 镜像构建（可选）

以下命令仅用于需要本地运行应用容器时构建镜像。脚本先调用本机 Maven 构建后端，再构建运行时镜像；Maven 不会在镜像中运行。

```bash
./scripts/build-docker-images/build-all.sh
```

脚本默认最多并行构建 2 个镜像；可通过 `-j` / `--jobs` 或 `BUILD_JOBS` 调整并行数，例如：

```bash
./scripts/build-docker-images/build-all.sh --jobs 4
BUILD_JOBS=3 ./scripts/build-docker-images/build-all.sh
```

也可以继续通过对应的 `crawler-*.sh` 脚本单独构建镜像。默认镜像标签为 `latest`，可通过 `IMAGE_TAG` 指定其他标签。

本地构建入口是 `scripts/build-local.sh`，本地镜像使用 `docker/Dockerfile-app-local`。GitHub Actions 则由 `.github/workflows/docker-publish.yml` 独立使用 Docker Buildx 和 `docker/Dockerfile-app` 在构建容器内执行 Maven，并在适用时发布到 GHCR；Actions 不调用本地构建脚本，也不依赖本地 `target/` 产物。

前端容器使用 Nginx 提供生产构建文件，并将 `/api` 请求转发到网关：

```bash
docker-compose up -d crawler-admin-web
# http://127.0.0.1:5173
```

Compose 内默认使用 `http://crawler-gateway:8080`，适用于前端与网关位于同一 Docker 网络的场景。若在 1Panel 中单独部署前端容器，请为其设置 `API_UPSTREAM` 环境变量，值为**从前端容器内部可访问**的网关地址，例如 `http://<网关容器IP或网络别名>:8080`。配置后重建并重新部署前端镜像；浏览器仍通过同域 `/api` 请求，不需要在前端源码中配置 API 地址。

已有数据库升级时，需执行 `docker/mysql/migration/008_spider_skip_tls_verify.sql`，再部署更新后的服务。爬虫配置中的“跳过 TLS 校验”默认为关闭；仅当目标站点证书异常且确认站点可信时才应开启，启用后会跳过 HTTPS 证书链及主机名校验。

### 3. 启动前端

```bash
cd crawler-admin-web
npm install
npm run dev        # 开发模式 http://127.0.0.1:5173
npm run build      # 生产构建到 dist/
```

生产构建会按路由延迟加载页面，单独缓存 Vue 运行时，并仅打包页面实际引用的 Element Plus 组件和图标。

### 4. 登录

默认账号（密码均为 `admin123`）：
- 管理员: `admin`
- 普通用户: `user`

## 核心流程

1. 前端创建爬虫（配置起始URL、类型、深度、调度）
2. spider-service 生成任务记录，投递 Kafka
3. worker 消费消息，WebMagic 解析页面
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