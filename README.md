# 热点事件检测系统

一个基于Java技术栈的热点事件检测网站系统，采用前后端分离架构，定时从网上抓取热点事件数据并展示。

## 技术栈

### 后端
- **框架**: Spring Boot 3.2.5
- **ORM**: Spring Data JPA + MyBatis-Plus 3.5.5
- **数据库**: MySQL 8.0
- **HTTP工具**: Hutool + Jsoup
- **定时任务**: Spring Scheduler
- **构建工具**: Maven

### 前端
- **框架**: Vue 3 + Vite 5
- **UI组件**: Element Plus
- **状态管理**: Pinia
- **路由**: Vue Router 4
- **HTTP请求**: Axios
- **图表**: ECharts 5
- **日期处理**: Day.js

## 功能特性

- 多数据源支持（微博、知乎、百度）
- 定时自动抓取热点事件
- 热点事件列表展示与搜索
- 事件详情查看
- 数据统计与可视化图表
- 抓取记录管理
- Docker一键部署

## 项目结构

```
.
├── backend/                 # 后端项目
│   ├── src/
│   │   └── main/
│   │       ├── java/com/hotevent/
│   │       │   ├── crawler/     # 爬虫模块
│   │       │   ├── entity/      # 实体类
│   │       │   ├── repository/  # 数据访问层
│   │       │   ├── service/     # 业务逻辑层
│   │       │   ├── controller/  # 控制层
│   │       │   ├── task/        # 定时任务
│   │       │   ├── config/      # 配置类
│   │       │   └── common/      # 公共类
│   │       └── resources/
│   ├── Dockerfile
│   └── pom.xml
├── frontend/               # 前端项目
│   ├── src/
│   │   ├── views/             # 页面组件
│   │   ├── api/               # API接口
│   │   ├── router/            # 路由配置
│   │   ├── utils/             # 工具函数
│   │   └── styles/            # 样式文件
│   ├── Dockerfile
│   ├── nginx.conf
│   ├── vite.config.js
│   └── package.json
├── docker/                  # Docker相关
│   └── mysql/
│       └── init.sql         # 数据库初始化脚本
├── docker-compose.yml       # Docker Compose配置
├── .env.example             # 环境变量示例
├── .gitignore
└── README.md
```

## 快速开始

### 方式一：Docker部署（推荐）

#### 前置要求
- Docker 20.10+
- Docker Compose 2.0+

#### 部署步骤

1. **克隆项目**
```bash
git clone <repository-url>
cd hot-event-detector
```

2. **配置环境变量**
```bash
cp .env.example .env
# 根据需要修改 .env 中的配置
```

3. **启动服务**
```bash
docker-compose up -d
```

4. **访问应用**
- 前端: http://localhost
- 后端API: http://localhost:8080/api

5. **停止服务**
```bash
docker-compose down
```

### 方式二：本地开发

#### 前置要求
- JDK 17+
- Maven 3.6+
- Node.js 18+
- MySQL 8.0+

#### 后端启动

1. **创建数据库**
```sql
CREATE DATABASE hot_event_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. **修改配置**
编辑 `backend/src/main/resources/application.yml`，配置数据库连接信息。

3. **启动后端**
```bash
cd backend
mvn spring-boot:run
```

后端服务将在 http://localhost:8080/api 启动。

#### 前端启动

1. **安装依赖**
```bash
cd frontend
npm install
```

2. **启动开发服务器**
```bash
npm run dev
```

前端服务将在 http://localhost:3000 启动。

## API 接口

### 热点事件
- `GET /api/events` - 获取热点事件列表
- `GET /api/events/{id}` - 获取事件详情
- `GET /api/events/sources` - 获取可用数据源
- `GET /api/events/statistics` - 获取统计数据
- `DELETE /api/events/{id}` - 删除事件
- `POST /api/events` - 保存事件

### 爬虫管理
- `POST /api/crawler/crawl-all` - 抓取所有数据源
- `POST /api/crawler/crawl/{source}` - 抓取指定数据源
- `GET /api/crawler/sources` - 获取可用爬虫源

### 抓取记录
- `GET /api/crawl-records` - 获取抓取记录列表
- `GET /api/crawl-records/recent` - 获取最近抓取记录
- `GET /api/crawl-records/statistics` - 获取抓取统计

## 配置说明

### 爬虫配置
```yaml
hot-event:
  crawler:
    enabled: true              # 是否启用爬虫
    cron: "0 */30 * * * ?"    # 定时抓取Cron表达式（每30分钟）
    sources:                   # 启用的数据源
      - weibo
      - zhihu
      - baidu
    timeout: 10000             # 请求超时时间（毫秒）
    user-agent: "Mozilla/5.0..."  # User-Agent
```

### 数据库配置
可通过环境变量配置：
- `DB_HOST` - 数据库地址
- `DB_PORT` - 数据库端口
- `DB_NAME` - 数据库名
- `DB_USERNAME` - 数据库用户名
- `DB_PASSWORD` - 数据库密码

## Git 使用指南

### 初始化仓库
```bash
git init
git add .
git commit -m "Initial commit: 热点事件检测系统"
```

### 关联远程仓库
```bash
git remote add origin <your-repo-url>
git branch -M main
git push -u origin main
```

### 常用命令
```bash
# 查看状态
git status

# 添加文件
git add .
git add <filename>

# 提交
git commit -m "your commit message"

# 拉取
git pull origin main

# 推送
git push origin main

# 创建分支
git checkout -b feature-branch

# 合并分支
git checkout main
git merge feature-branch
```

## 扩展开发

### 添加新的数据源

1. 创建爬虫类，继承 `AbstractHotEventCrawler`
2. 实现 `getSourceName()`、`getCrawlUrl()`、`parseHtml()` 等方法
3. 添加 `@Component` 注解
4. 在配置中启用该数据源

### 添加新的API接口

1. 在 Service 层实现业务逻辑
2. 在 Controller 层添加接口
3. 在前端 api 目录添加接口封装
4. 创建对应的页面组件

## 注意事项

1. 爬虫功能可能受目标网站反爬策略影响，如遇抓取失败会使用模拟数据
2. 请合理设置抓取频率，避免对目标网站造成压力
3. 生产环境部署时，请修改默认密码
4. 建议配置 HTTPS 以保证数据传输安全

## License

MIT License
