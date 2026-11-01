# HomeMedGuard — 技术栈交接文档

**系统全称**：HomeMedGuard — Home Medicine Cabinet Expiry & Drug-Conflict Guardian
**中文名**：家用药箱效期与用药冲突监护系统
**交付形态**：Vue3 + Spring Boot 单体项目，`mvn package` 产出一个可运行 jar（前端构建产物打进 `src/main/resources/static`）
**文档日期**：2026-11-01
**GitHub 仓库**：`home-med-guard`（public）

---

## 一、技术栈总清单

### 后端

| 层 | 选型 | 版本建议 | 用途 |
|---|---|---|---|
| 语言/运行时 | Java | 21 LTS | 虚拟线程、record、switch 模式匹配 |
| 主框架 | Spring Boot | 3.3.x ~ 3.5.x | Web / IoC / 配置 / Actuator |
| Web | spring-boot-starter-web | 随 Boot | REST API |
| 持久层 | MyBatis-Plus | 3.5.x | CRUD + 分页 + 逻辑删除 + 字段加解密 TypeHandler |
| 数据库 | MySQL Community | 8.0+ | 主业务库 |
| 数据库迁移 | Flyway | 随 Boot | `V1__init.sql` 版本化建表 |
| 连接池 | HikariCP | 随 Boot（默认） | 无需额外配置 |
| 缓存 | Caffeine | 3.x | 相互作用规则、药品字典本地缓存（单机够用，不引入 Redis） |
| 定时任务 | Spring `@Scheduled` + `@EnableScheduling` | 随 Boot | 每日效期扫描、余量推算、复诊提醒 |
| 动态任务（可选） | Quartz | 随 Boot starter | 需要"每个用户自定义提醒时间"时用；否则用 ScheduledThreadPool 自排 |
| 安全 | Spring Security 6 + JWT (jjwt 0.12.x) | — | 认证、`@PreAuthorize` 角色鉴权 |
| 参数校验 | jakarta.validation (Hibernate Validator) | 随 Boot | DTO 校验 + 自定义剂量范围注解 |
| 规则表达式 | **AviatorScript** 5.x | — | 规则库存 DB，表达式动态求值（见 §四） |
| Bean 映射 | MapStruct | 1.5.x / 1.6.x | Entity ↔ DTO ↔ VO |
| 简化 | Lombok | 1.18.x | — |
| 接口文档 | springdoc-openapi 2.x + Knife4j 4.x | — | `/doc.html` 在线调试 |
| Excel 导出 | EasyExcel（或 Apache POI） | 4.x | 用药清单导出、报表导出 |
| 条码解析 | ZXing (com.google.zxing) | 3.5.x | 药品追溯码 / 条形码识别 |
| 单元测试 | JUnit 5 + Mockito + AssertJ | 随 Boot | 规则引擎与排程求解必须有表驱动测试 |
| 覆盖率 | JaCoCo | 0.8.x | 答辩截图用 |
| 代码规范 | Spotless + Checkstyle | — | 统一格式 |

### 前端

| 层 | 选型 | 用途 |
|---|---|---|
| 框架 | Vue 3.5+（Composition API + `<script setup>`） | 全部页面 |
| 构建 | Vite 5/6 | dev server + 生产构建 |
| 语言 | TypeScript 5.x | 规则/风险类型必须有类型 |
| 状态 | Pinia + pinia-plugin-persistedstate | 用户会话、家庭组、在服方案 |
| 路由 | Vue Router 4 + 路由守卫 | 角色路由（成员 / 管理员） |
| UI 组件 | Element Plus 2.x | 表单、表格、抽屉、日历 |
| 图表 | ECharts 5 + vue-echarts | 浪费率、依从性、支出趋势 |
| 请求 | Axios + 请求拦截器 | 统一 token、错误码、风险告警拦截 |
| 时间计算 | Day.js | 效期倒计时、服药时点（与后端 `java.time` 对齐，全用 `LocalDate/LocalDateTime`，时区固定 `Asia/Shanghai`） |
| 组合式工具 | VueUse | 定时器、localStorage、权限指令 |
| 摄像头扫码 | `vue-qrcode-reader` 或 `jsQR` | H5 端扫追溯码 |
| 校验 | Zod（可选）或 Element 表单规则 | 复杂剂量字段 |
| 规范 | ESLint + Prettier + `eslint-plugin-vue` | — |

> 移动端如需 App：把 `dist` 用 Capacitor 打包即可（你上一个项目已验证过这条路）。

### 构建与打包（"一个项目"怎么实现）

```
home-med-guard/
├── pom.xml
├── frontend/                    # Vue3 源码
│   ├── src/  package.json  vite.config.ts
├── src/main/java/com/homemedguard/
│   ├── common/                  # 统一返回、异常、加解密、审计
│   ├── config/                  # Security / OpenAPI / Cache / Scheduling / MyBatis
│   ├── domain/entity/           # MyBatis-Plus 实体
│   ├── dto/  vo/                # 入参 / 出参
│   ├── mapper/                  # + resources/mapper/*.xml
│   ├── service/                 # ★ 核心逻辑全在这里
│   │   ├── expiry/              # 效期状态机
│   │   ├── rule/                # 用药冲突规则引擎
│   │   ├── schedule/            # 服药排程求解
│   │   ├── stock/               # 批次库存与余量推算
│   │   ├── reminder/            # 提醒派发
│   │   └── report/              # 统计聚合
│   ├── job/                     # 定时任务
│   └── web/                     # Controller
└── src/main/resources/
    ├── application.yml
    ├── db/migration/V1__init.sql
    ├── mapper/*.xml
    └── static/                  # Vite 构建输出（.gitignore 忽略，CI/打包时生成）
```

`frontend-maven-plugin` 绑定到 `generate-resources` 阶段：自动装 Node → `npm ci` → `vite build` → 输出到 `static/`。
开发时前后端分开跑：Vite `server.proxy` 把 `/api` 转发到 `localhost:8080`。

---

## 二、功能模块 → 技术映射

### 1. 账号与家庭组

| 功能 | 技术实现 |
|---|---|
| 注册 / 登录 / 登出 | Spring Security + jjwt；密码 BCrypt（strength 10）；Refresh Token 存 DB 并支持吊销 |
| 角色：家庭管理员 / 普通成员 / 系统管理员 | `ROLE_` 权限 + `@PreAuthorize("hasRole(...)")`；家庭内角色另存 `family_member.role` |
| 家庭组建组与邀请 | 邀请码表 + 过期时间；邀请链接落库校验；事务保证一人只在一个默认家庭 |
| 成员健康档案（老人/儿童/孕妇/哺乳、过敏史、慢性病、肝肾功能分级） | `member_profile` 表 + 枚举标签；孕产/儿童用 `birth_date` 派生年龄段而非手填；过敏史与药品成分做归一化比对 |
| 健康数据保护 | 字段级 AES-256-GCM 加密（MyBatis `TypeHandler`），列表页脱敏 VO，审计日志 |

### 2. 药品档案与库存

| 功能 | 技术实现 |
|---|---|
| 标准药品字典（通用名/商品名/剂型/规格/厂家/OTC-Rx） | `drug_dict` 表 + 字典缓存（Caffeine，`@Cacheable`）；全文检索用 MySQL 前缀索引 + `LIKE`，数据量不大不上 ES |
| 名称搜索自动补全 | `/api/drugs/suggest?kw=` 返回 top10，走缓存 |
| 条码 / 追溯码扫描识别 | 前端解码 → 后端 `barcode → drug` 映射表；字典缺失时降级为"手工确认并入库" |
| 完全自建药 | `drug_custom`，继承字典字段，标记来源 `USER_INPUT` |
| 批次库存（同药多效期） | `drug_stock_batch`（药品、批次效期、开封日期、数量、存放位置）；库存视图按效期升序消费（FEFO，先到期先出） |
| 双轨时效（有效期 vs 开封后使用期） | 效期计算统一入口：`deadline = min(有效期, 开封日期 + 开封后天数)`；剂型规则表配置"开封后天数"（滴眼液 28 天、糖浆 90 天等） |
| 说明书图片 / 附件 | `spring.servlet.multipart` 上传 → 本地磁盘 `data/files/{yyyy}/{MM}/{uuid}` + 静态资源映射；量大再换 MinIO |
| 存放位置（药箱分层） | `storage_location` 树形表（`parent_id`） |

### 3. 效期状态机与处置

| 功能 | 技术实现 |
|---|---|
| 三级预警（临期 180/90/30 可配、已过期、开封超期） | 自研状态机 `EXPIRING_NEAR → EXPIRED → DISPOSED/RENEWED/HANDLED`；阈值存 `sys_config`，家庭级可覆盖 |
| 每日扫描生成待处理任务 | `@Scheduled(cron="0 5 3 * * ?")` 扫 `next_action_date`，写入 `action_task`；幂等：唯一键 `(batch_id, task_type, biz_date)` |
| 处置流程与台账 | `disposal_record`（动作、原因、金额、操作人、时间）；软删除保留追溯 |
| 特殊药品回收提醒 | 药品分类字典加 `disposal_class` 字段（抗生素/胰岛素/外用药/精神类），命中规则文案由 `rule_template` 渲染 |
| 安全库存与补货清单 | `min_safe_qty` + 消耗速率（近 90 天日均）→ `predicted_empty_date`；低于阈值进 `purchase_suggestion` |

### 4. 用药冲突监护（核心，后端决策）

| 功能 | 技术实现 |
|---|---|
| 药-药相互作用库，A/B/C 严重度 | `interaction_rule(drug_a_component, drug_b_component, severity, mechanism, advice)`；按**成分**匹配而非商品名 |
| 成分归一化 | 药品 → 成分多对多表 `drug_component`；成分别名表（"对乙酰氨基酚 / 扑热息痛 / acetaminophen"）统一映射到标准成分 ID |
| 重复成分叠加检测 | 在服方案 + 新增药，按 `component_id` 分组累加 `single_dose * frequency`，与 `max_daily_dose` 比对 |
| 剂量上限聚合校验 | `component_limit` 表（成人/儿童 mg·kg⁻¹·d⁻¹、老年减量系数）；单位换算用 `unit-conversion` 工具类（g/mg/μg/IU） |
| 禁忌人群校验 | `contraindication_rule(component_id, tag_code, severity)`；tag 来自 §1 成员档案（孕妇/哺乳/<12 岁/肝功能 Child-Pugh/肾功能 eGFR 分级/疾病史）；表达式引擎判定 |
| 药-食 / 药-酒相互作用 | 同一套规则表，增加 `target_type = FOOD | ALCOHOL` |
| 服药时间打架（需间隔） | `spacing_rule(component_a, component_b, min_gap_minutes)` → 交给 §5 排程求解器错开 |
| 实时风险评估报告 | `POST /api/risk/evaluate`：入参 = 成员 + 当前在服 + 拟新增 → 出参 = 风险条目列表（严重度、依据、建议、规则版本号） |
| **可解释性** | 每条命中记录 `rule_id + rule_version + 输入快照 JSON`；一次评估共享 `traceId`（MDC）；前端可展开"因为哪两条规则" |
| 规则版本化 | `rule_*` 表带 `version / status(DRAFT|PUBLISHED|RETIRED) / effective_from`；发布后规则不可原地改，只能新版本；评估结果固化 `rule_version` |

### 5. 服药排程与提醒

| 功能 | 技术实现 |
|---|---|
| 医嘱方案录入（频次/剂量/疗程/饭前饭后/需间隔） | `med_regimen` + `regimen_item`；频次用结构化字段（`times_per_day` + `preferred_clock_times` + `relative_to_meal`），不存自然语言 |
| 排程求解 | 自研求解器（见 §四）：候选时点生成 → 硬性约束过滤（间隔、睡眠禁服、餐前餐后窗口）→ 贪心打分 + 回溯重排 → 输出每日时间表 + 无解原因 |
| 每日时间表物化 | `med_schedule_slot` 落库（未来 N 天），改动方案时重算并保留已打卡记录 |
| 提醒渠道 | 抽象 `NotifyChannel` 接口 + 站内信实现；预留 Email（`spring-boot-starter-mail`）与微信模板消息 / 短信适配器 |
| 打卡、漏服、补服策略 | `med_adherence_log`（计划槽位、实际时间、状态）；漏服判定用定时任务扫过期未打卡；补服建议文案挂 `spacing_rule` |
| 依从性统计 | 按 `(member, drug, month)` 聚合打卡率，物化到月度统计表避免实时扫全表 |

### 6. 余量与复诊

| 功能 | 技术实现 |
|---|---|
| 耗尽日推算 | FEFO 消耗序列 + 日均用量 → `predicted_empty_date`；随打卡动态修正 |
| 疗程倒计时 / 复诊提醒 | `regimen.end_date - lead_days` 生成 `action_task` |
| 长期用药清单导出 | EasyExcel / Markdown 双格式；含成分、剂量、起始日期，供就诊时给医生 |

### 7. 应急与家庭安全

| 功能 | 技术实现 |
|---|---|
| 误服 / 过量处置指引 | 静态指引库按成分 + 剂量超阈值触发，前端强提示 + "立即就医"横幅；文案带来源与更新时间 |
| 家庭急救包配置检查 | `first_aid_checklist_template` 与家庭实际库存比对，缺失/过期列表化 |
| 说明书要点结构化 | `drug_leaflet(component_id, section_code, content)`，`section_code` 枚举（禁忌/孕妇/不良反应/相互作用） |
| 召回批次标记 | `recall_notice`（批准文号 + 批次号段 + 日期）→ 定时比对家庭批次库存，命中生成告警 |

### 8. 统计报表

| 功能 | 技术实现 |
|---|---|
| 浪费率（过期丢弃金额 ÷ 购药总额） | 处置台账金额聚合；口径写清"按购入价"，SQL 存 `mapper/report` |
| 支出趋势（按人 / 按类别） | 日增量汇总表 + ECharts 折线/堆叠柱 |
| 依从率、慢病连续覆盖天数 | 覆盖天数 = 排程槽位去重计数，缺口检测用日期区间合并算法 |
| 风险拦截统计（按严重度） | 评估日志表聚合 |
| 导出 | EasyExcel；报表一律走 `*ReportMapper` 手写 SQL，不用 ORM 拼复杂查询 |

### 9. 后台管理

| 功能 | 技术实现 |
|---|---|
| 药品与规则维护 + 审核流 | `rule_draft → submit → approve → publish` 状态字段；审批人权限 `ROLE_ADMIN` |
| 参数化配置（预警天数、剂量上限、阈值） | `sys_config` 键值表 + Caffeine 缓存 + 家庭级覆盖 |
| 用户与家庭组管理、数据导出 | 分页查询 + 脱敏展示 |
| 操作日志与审计 | AOP `@AuditLog` 注解 → 异步写 `audit_log`（操作人、IP、目标、before/after JSON） |

### 10. 工程底座与安全合规

| 项 | 技术实现 |
|---|---|
| 统一返回 / 异常体系 | `Result<T>` + `@RestControllerAdvice` + 业务错误码枚举；风险拦截专用错误码 `RISK_CONFIRM_REQUIRED` |
| 幂等 | 写接口带 `Idempotency-Key`；定时任务靠唯一键去重 |
| 事务 | `@Transactional(rollbackFor = Exception.class)`，跨家庭数据校验放事务内 |
| 限流 | 单用户接口级 `Bucket4j` 或简单 Guava RateLimiter（防脚本刷提醒） |
| 日志 | Logback + `traceId`（MDC）+ 按天滚动；敏感字段自定义 `PatternLayout` 脱敏 |
| 健康检查 | Actuator `/actuator/health`、`/metrics` |
| 数据保护 | HTTPS（部署层）、字段加密、软删除 `deleted` 逻辑删除列、导出水印、"删除我的全部数据"接口 |
| **免责声明（必须）** | 首次进入强制弹窗确认 + 全站底部常驻 + 每条风险告警下附注："本系统为家庭用药提醒辅助工具，规则库不完整，不构成诊疗建议，用药请遵医嘱" |

---

## 三、数据库表清单（约 30 张，核心）

**基础**：`sys_user` `family` `family_member` `family_member_profile` `sys_config` `audit_log`
**药品字典**：`drug_dict` `drug_component` `component_dict` `component_alias` `component_limit` `drug_leaflet` `recall_notice` `first_aid_checklist_template`
**家庭库存**：`storage_location` `family_drug` `stock_batch` `disposal_record` `purchase_suggestion`
**规则**：`interaction_rule` `contraindication_rule` `spacing_rule` `food_alcohol_rule` `rule_publish_log`
**方案与排程**：`med_regimen` `regimen_item` `med_schedule_slot` `adherence_log` `risk_evaluation_log`
**任务与提醒**：`action_task` `notify_message` `notify_channel_config`
**统计**：`stat_monthly_waste` `stat_monthly_adherence` `stat_expense_daily`

---

## 四、关键选型决策（这几个别选错）

1. **规则引擎：自研数据驱动 + AviatorScript，不用 Drools**
   Drools 学习成本高、KIE 容器重、答辩讲不清。做法：规则以行存 DB（条件字段 + Aviator 表达式 + 严重度 + 建议文案 + 版本），启动加载进 Caffeine，评估时对上下文变量求值。既做到"规则可维护、可版本化、可解释"，又只用一个几 KB 的表达式引擎。

2. **排程求解：贪心 + 回溯，不用 OptaPlanner/TimeChip**
   约束规模很小（一天 ≤ 8 个时点、≤ 15 种药）。流程：按餐次与作息生成候选时点 → 对每味药取可行集合 → 按"约束最少者优先（MRV）"排序逐个放置 → 违反间隔则回溯；无解时输出是哪个约束导致。这套逻辑本身就是后端亮点，值得写 20+ 表驱动单测。

3. **相互作用知识库来源 = 本项目最大风险点（务必先看）**
   公开、免费、结构化的中文药-药相互作用数据基本拿不到完整授权库。**现实方案**：只覆盖家庭常备药 + 常见慢病药约 60~120 条成分级规则，人工整理并标注来源（药品说明书原文、指南），每条规则记录出处与更新日期；系统定位写成"已知规则命中提醒"，绝不写"用药安全检查"。这一点在 README 和答辩里必须主动讲，否则容易被质疑专业性与合规性。

4. **不引入 Redis / MQ**
   单机 + 定时任务场景，Caffeine 本地缓存 + DB 任务表足够。要发短信/邮件就异步线程池 + 重试列，不上 Kafka。少一个中间件，部署和答辩都省事。

5. **前端只选一套组件库**
   Element Plus 覆盖桌面管理端和 H5 表单端。别 Element Plus + Vant 混用，主题与体积都会失控。日历用 `el-calendar` + 自绘时间轴即可，不引 FullCalendar。

6. **时间一律后端计算**
   效期、疗程、排程全部在 Java 侧用 `java.time` + 固定时区算好下发，前端只做展示；否则跨端时区会让"临期天数"抖动，测试很难过。

---

## 五、开发环境准备

```bash
# Windows（winget）
winget install Microsoft.OpenJDK.21
winget install Apache.Maven
winget install Oracle.MySQL            # 或直接装 MySQL 8.0 社区版
winget install Git.Git
node -v                               # 已有 v24.x，可直接用
```

```bash
# MySQL 建库
mysql -u root -p -e "CREATE DATABASE home_med_guard DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

# 首次运行（Flyway 自动建表 + 种子数据）
mvn spring-boot:run                  # http://localhost:8080/doc.html
cd frontend && npm ci && npm run dev # http://localhost:5173，/api 代理到 8080
```

打包：`mvn clean package` → `java -jar target/home-med-guard.jar`（内含前端产物，单 jar 交付）。

---

## 六、建议分期（每期可独立演示）

| 期 | 内容 | 后端看点 |
|---|---|---|
| M1 | 项目骨架 + 登录 + 家庭组 + 成员档案 + 药品字典 | 单体结构、JWT、Flyway、统一异常 |
| M2 | 批次库存 + 双轨效期 + 三级预警 + 处置台账 + 每日扫描 | FEFO、状态机、定时任务幂等 |
| M3 | **成分归一化 + 冲突规则引擎 + 风险评估报告** | 规则数据驱动、版本化、可解释命中 |
| M4 | **医嘱方案 + 排程求解 + 打卡依从性** | 约束求解、无解原因、补服策略 |
| M5 | 余量推算 + 复诊提醒 + 急救包检查 + 召回匹配 | 消耗预测、日期区间合并 |
| M6 | 统计报表 + 后台规则审核 + 审计日志 + 导出 + 部署文档 | 报表 SQL、审核流、Actuator |

M2、M3、M4 是"主要功能在后端"这条要求的得分点，不要为赶进度把它们做成前端本地计算。

---

## 七、风险与注意事项

- **合规**：本系统属健康提醒类，规则库不完整，必须全站明示免责；不要出现"诊断""安全用药已确认"等表述。
- **数据**：健康信息按敏感个人信息处理，演示环境也要用假数据；不要拿真实家庭成员病历截图进仓库。
- **仓库公开**：`application.yml` 里的数据库密码必须用占位符 + `application-local.yml`（`.gitignore` 掉），提供 `application-example.yml`；公开仓库泄露过一次密码就要换库并撤销提交历史。
- **知识库准确性**：每条规则标注来源与整理日期，允许管理员纠错，宁可少覆盖也不要写错。
