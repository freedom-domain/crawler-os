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
├── crawler-finance-service   # 模拟投资/基金行情 (8085)
├── crawler-spider-service    # 爬虫/任务/调度 (8082)
├── crawler-ip-service       # Cloudflare IP 管理与测速 (8086)
├── crawler-search-service   # 数据搜索 (8083)
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
 │    │      │    │      │   │
User Finance Spider IP   Search File
 │    │      │    │          │
 └────┴──────┴────┴──────────┘
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
mvn -pl crawler-finance-service spring-boot:run
mvn -pl crawler-spider-service spring-boot:run
mvn -pl crawler-ip-service spring-boot:run
mvn -pl crawler-search-service spring-boot:run
mvn -pl crawler-file-service spring-boot:run
mvn -pl crawler-worker spring-boot:run
```

或通过本地预编译 JAR 构建轻量运行镜像并启动全部应用：

```bash
./scripts/build-docker-images/build-all.sh
docker-compose up -d crawler-gateway crawler-user-service crawler-finance-service crawler-spider-service \
  crawler-ip-service crawler-search-service crawler-file-service crawler-worker crawler-admin-web
```

Compose 默认使用本地 `latest` 镜像，不在 `docker compose up` 时编译源码。`build-all.sh` 会先用本机 Maven 构建全部后端模块，再为每个镜像只发送对应 JAR 和运行 Dockerfile；前端镜像仍在 Node 构建阶段运行 `npm ci` 和生产构建。修改源码后请先重新构建镜像，再启动或重建对应容器。使用自定义 `IMAGE_TAG` 时，构建与 Compose 启动都需设置相同标签。

### 本地 Docker 镜像构建（可选）

以下命令用于本地构建应用镜像。脚本先调用本机 Maven 构建后端，再构建仅包含运行时 JRE 和对应 JAR 的镜像。

```bash
./scripts/build-docker-images/build-all.sh
```

脚本默认最多并行构建 2 个镜像；可通过 `-j` / `--jobs` 或 `BUILD_JOBS` 调整并行数，例如：

```bash
./scripts/build-docker-images/build-all.sh --jobs 4
BUILD_JOBS=3 ./scripts/build-docker-images/build-all.sh
```

也可以继续通过对应的 `crawler-*.sh` 脚本单独构建镜像。默认镜像标签为 `latest`，可通过 `IMAGE_TAG` 指定其他标签。

GitHub Actions 会在 Java 21 环境中编译各服务 JAR，再用小型临时构建上下文制作运行镜像；不会在 Docker 构建阶段重复运行 Maven。前端镜像继续由其多阶段 Dockerfile 构建。

前端容器使用 Nginx 提供生产构建文件，并将 `/api` 请求转发到网关：

```bash
docker-compose up -d crawler-admin-web
# http://127.0.0.1:5173
```

本机可在被 Git 忽略的 `docker-compose.override.yml` 中覆盖 MySQL root 和各服务的 `MYSQL_PASSWORD`；Compose 会自动合并该文件。不要将包含真实口令的覆盖文件提交到仓库。已有 MySQL 数据卷不会因修改环境变量而自动更改数据库用户密码；必须确保覆盖配置与数据库当前密码一致。

Compose 内默认使用 `http://crawler-gateway:8080`，适用于前端与网关位于同一 Docker 网络的场景。若在 1Panel 中单独部署前端容器，请为其设置 `API_UPSTREAM` 环境变量，值为**从前端容器内部可访问**的网关地址，例如 `http://<网关容器IP或网络别名>:8080`。配置后重建并重新部署前端镜像；浏览器仍通过同域 `/api` 请求，不需要在前端源码中配置 API 地址。

已有数据库升级时，需执行 `docker/mysql/migration/008_spider_skip_tls_verify.sql`，再部署更新后的服务。爬虫配置中的“跳过 TLS 校验”默认为关闭；仅当目标站点证书异常且确认站点可信时才应开启，启用后会跳过 HTTPS 证书链及主机名校验。

启用模拟投资功能时，已有数据库需依次执行 `docker/mysql/migration/017_paper_investment.sql`、`docker/mysql/migration/018_investment_type_menus.sql`、`docker/mysql/migration/019_merge_stock_trading_into_investment_overview.sql`、`docker/mysql/migration/020_fund_history_cache.sql`、`docker/mysql/migration/021_fund_watchlist.sql`、`docker/mysql/migration/022_fund_catalog.sql`、`docker/mysql/migration/023_move_fund_pages_to_catalog.sql`、`docker/mysql/migration/024_fund_profile.sql` 和 `docker/mysql/migration/027_fund_refresh_schedule.sql`。模拟投资和基金行情由独立的 `crawler-finance-service` 提供；模拟账户在用户首次打开功能时创建，每个账户默认有 ¥100,000 虚拟资金。投资页面包含投资总览、基金管理和股票管理；从基金或股票管理页的“管理操作”下拉框进入同步管理。基金管理按代码、名称或拼音筛选基金；基金目录可在同步管理页面更新。基金详情首次请求时若数据库没有档案，会自动从东方财富获取并保存；之后展示已保存的档案与缓存行情，可点击“更新基金档案”主动刷新基金规模、管理团队、费率及投资背景等信息。场内基金和场外基金行情/定投页面通过基金管理目录页上的按钮弹窗打开。关注清单在弹窗中维护，已有缓存可手动更新或清除；取消关注不会删除已有缓存。可在关注清单中设置全局自动同步频率：每小时整点、每个工作日 18:30 或每周一 18:30（上海时区，默认为每个工作日 18:30）。实时行情来自腾讯财经公开报价接口；场内基金历史行情和周定投回测使用腾讯历史行情接口，场外基金历史净值和周定投回测使用东方财富接口。场内和场外历史行情均会缓存到数据库，查询时可选择 API（查询后缓存）或数据库；选择数据库时需要先通过 API 查询缓存对应代码、周期和日期范围的数据。finance-service 自动刷新数据库中已有缓存或关注清单中的基金历史行情（场内日/周/月线和场外净值）；刷新只重查最近数据窗口，以更新近期及最近公布的行情。历史行情分析包含区间/年化收益、最大回撤、年化波动率、夏普/索提诺比率、月度收益和滚动一年收益；风险调整比率假设无风险利率为 0%，场内使用复权收盘价，场外优先使用累计净值，缺失累计净值时使用单位净值。定投回测同时按相同累计投入对比区间首日一次性买入，费用和整手规则依基金类型估算。所有指标基于所选历史区间，不代表未来收益。场外基金定投回测中，申购费率从每期投入中扣除，期末赎回费按用户输入的比例估算；实际净值公布、申购确认和持有期赎回费可能与模拟假设不同。

股票管理需在已有数据库执行 `docker/mysql/migration/025_stock_management.sql`。股票目录默认从新浪财经公开接口同步 A 股代码和名称（不需要 API Key；该接口不提供行业字段），并按代码识别市场；可通过 Finance 服务配置项 `investment.stock-list-url` 覆盖目录接口地址。股票详情通过东方财富补充并保存上市公司档案，另提供腾讯财经日线历史行情查询与缓存、每周定投回测（并与相同投入的一次性买入对比）；使用已有数据库时还需执行 `docker/mysql/migration/026_stock_history_cache.sql`。历史行情可选 API 查询并缓存或读取数据库已有缓存；回测按前复权收盘价、100 股整手和佣金 0.03%（最低 ¥5）估算，不含卖出及印花税。目录中的“模拟投资”操作会打开预填股票代码的模拟交易窗口。请确认部署 Finance 服务的主机能够通过 HTTPS 访问所配置的数据源。

股票关注清单需在已有数据库执行 `docker/mysql/migration/028_stock_watchlist.sql` 和 `docker/mysql/migration/030_stock_refresh_schedule.sql`。股票管理页面可从目录关注/取消股票，在关注清单中查看缓存状态、手动更新行情，并设置自动同步频率：每小时整点、每个工作日 18:35 或每周一 18:35（上海时区，默认为每个工作日 18:35）；Finance 服务会自动刷新关注股票最近 30 天的日线行情。

Cloudflare IP 优选由独立的 `crawler-ip-service`（8086）提供，已有数据库需执行 `docker/mysql/migration/036_cf_ip_management.sql` 和 `docker/mysql/migration/037_remove_vless_fields.sql`。管理员可从 Cloudflare 官方接口初始化 IPv4/IPv6 CIDR 清单，并为全球任播网段维护自定义地区标签；每个网段默认抽测一个地址，最多每段 5 个且单次总量限制为 200 个，IPv6 网段不会展开；抽样会跳过候选列表中已有的 IP，重复执行时继续抽取尚未添加的地址。常规探测记录从 IP 服务主机 TCP 连接候选 IP:443 的耗时、针对指定 SNI 且校验证书的 TLS 握手耗时，以及经 `speed.cloudflare.com` 获取最多 256 KiB 的 HTTPS 下载速度（Mbps）。SNI 仅随常规 TLS 本次测试结果返回，不存入数据库，前端刷新后不会保留。也可手工添加公网 IP，批量常规测试最多 100 个样本；可导出当前页面会话内使用当前 SNI 测试通过的 IP、端口、SNI 和测试指标 CSV，供配置 v2rayN 节点时使用（该 CSV 不是可直接导入的完整节点配置）。测试从 IP 服务主机发起；该功能只维护候选清单和测试结果，不会修改 DNS、代理或爬虫请求配置。

投资总览与股票模拟交易已移至股票管理页面的按钮弹窗中；已有数据库需执行 `docker/mysql/migration/029_move_investment_overview_to_stock_management.sql` 移除旧的独立菜单权限，原 `/investment/overview` 地址会兼容跳转至股票管理页面。

基金和股票管理页的“管理操作”下拉框提供“同步管理”弹窗，分别管理对应目录同步和历史行情同步，可查看任务记录并配置历史行情 API 与并发。历史行情任务可同步目录中全部基金（场内日/周/月线、场外净值）或股票（日线）。手动查询股票或场内基金历史行情时，可按次选择 API：Tencent 或 Eastmoney；设置中可多选启用可供手动查询选择的 API。两种数据源均由 Finance 服务中的 Java HTTP 客户端调用。后台“同步全部历史行情”仍使用现有 Tencent 数据源路径，不受手动查询 API 选择或启用列表影响。任务按标的记录状态、同步数据点数与错误详情；执行中的任务可取消，当前标的完成后停止，尚未开始的标的会标记为已取消，已写入的数据不会回滚。弹窗内可配置历史行情任务并发数（1-20）和请求间隔（默认 1000 毫秒，0 表示不限制）；间隔限制 Finance 服务所有历史行情 API 请求的开始频率，两个请求开始之间至少经过配置时长。并发数及间隔保存后立即生效且在服务重启后保留。已有数据库升级时需依次执行 `docker/mysql/migration/031_history_sync_tasks.sql`、`docker/mysql/migration/032_history_sync_concurrency.sql`、`docker/mysql/migration/033_history_sync_apis.sql` 和 `docker/mysql/migration/035_history_request_interval.sql`；若曾保存过 BaoStock/AKShare 配置，再执行 `docker/mysql/migration/034_history_sync_java_providers.sql`。

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
| Finance Service | 8085 |
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