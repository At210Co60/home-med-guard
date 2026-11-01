# HomeMedGuard 三人 Git 联合开发手册

> 适用：Vue3 + Spring Boot 单体项目（见 `handoff.md`），3 人、全 Windows、GitHub 账号 `At210Co60`、公有仓库 `home-med-guard`。
> 目标：`main` 永远能 `mvn package` 出可演示 jar；任何一次合并都不靠"手动对一遍代码"。

---

## 0. 先记住这 7 条（TL;DR）

1. **`main` 只进 PR，不直接 push**（仓库设置里加分支保护后，物理上推不上去）。
2. **一人一个 `feat/...` 分支，活不过 3 天**；每天开工、收工各 `git pull --rebase` 一次。
3. **共享文件单人守门**（`pom.xml`、`application*.yml`、`package.json`、`router/index.ts`、`V1__init.sql`）——见 §5，冲突 90% 出在这些文件上。
4. **Flyway 迁移号按人分段**（V1–V9 / V10–V19 / V20–29 / V30–39 / V40–49 / V50–59），规则种子数据用 `R__` 可重复迁移。
5. **`Squash and merge`** 合并，`main` 保持线性，rebase 才不会天天打架。
6. **⚠️ 完全模块化**：模块之间互不影响，但数据可通——这是本项目的第一设计红线，见 §6。
7. **公有仓库红线**：`application-local.yml`、密码、真实健康数据、`target/`、`node_modules/` 永不入库（`.gitignore` 已备好）。

---

## 1. 仓库与权限

### 1.1 建仓库（Owner = 你，只做一次）

GitHub → 右上 `+` → **New repository**：

- Owner：`At210Co60`
- Repository name：`home-med-guard`
- 可见性：**Public**（handoff 里定的）
- ⚠️ **Add a README / Add .gitignore / Choose a license 三个勾全部不选**（本地已有文件，勾了会多出一个提交，push 时要先合并）
- **Create repository**

> 目前远程仓库还没建：`git ls-remote https://github.com/At210Co60/home-med-guard.git` 返回 `Repository not found`。先建库，再 push。

### 1.2 怎么给另外两人开写权限（完整操作，跟着点）

**前提**：远程仓库已经建好。对方需要有自己的 GitHub 账号（没账号就用邮箱邀请，他注册后接受即可）。

1. 浏览器打开 `https://github.com/At210Co60/home-med-guard`。
2. 点仓库顶部右侧的 **`Settings`**（不是个人头像下的 Settings）。
3. 左侧边栏找到 **`Collaborators`**；新版界面叫 **`Collaborators and teams`**，组织仓库叫 **`Manage access`**，点进去。
   - 少数账号会要求输入一次密码/2FA 确认，正常。
4. 点绿色按钮 **`Add people`**（旧界面是 `Add people` → 输入框）。
5. 在搜索框里输入对方的 **GitHub 用户名**（也可用全名或注册邮箱），从下拉结果里选中他。
6. 右侧角色下拉选 **`Write`**（默认可能就是 Write，别选 Read / Admin）。
7. 点 **`Add ...`** 发送邀请。

**接下来这一步最容易漏：**

8. 对方必须**接受邀请**，否则他 push 会报 `Repository not found` 或 `403`。
   - 入口：`https://github.com/notifications`，或注册邮箱里的 **`View invitation`** 邮件 → 点 **`Accept invitation`**。
   - 最好把邀请链接直接微信发给他（邀请页 URL 形如 `https://github.com/At210Co60/home-med-guard/invitations`）。

**验证权限真的给了**（让对方执行）：

```bash
git clone https://github.com/At210Co60/home-med-guard.git
cd home-med-guard
git switch -c test/hello
git commit --allow-empty -m "chore: 权限验证"
git push -u origin HEAD        # 成功 = Write 权限已生效
# 验证完删掉这个分支
git push origin --delete test/hello
```

**常见卡点排查：**

| 现象 | 原因 | 处理 |
|---|---|---|
| 对方 push 报 `Repository not found` | ① 邀请没接受 ② 用错 GitHub 账号（Windows 凭据管理器里存的是另一个账号） ③ 仓库名/owner 打错 | 先接受邀请；`控制面板 → 凭据管理器 → Windows 凭据` 删掉 `git:https://github.com` 后重试 |
| 对方 push 报 `403` | 只有 Read 权限，或被分支保护拦（不能直接推 main） | 把角色改成 `Write`；改推 `feat/...` 分支再开 PR |
| 列表里显示 `Pending invite` | 邀请还没被接受 | 右侧 **`Resend invite`**，或复制邀请链接直接发给他 |
| 搜不到对方 | 他账号是新建的/隐私设置 | 用注册邮箱邀请 |
| 组织（Organization）仓库加不上人 | 必须 org Owner 操作，Free 组织 private 仓库要付费 | public 仓库直接加；或让对方 fork 后开 PR |
| 想撤权限 | — | `Collaborators` 列表右侧 **`Remove`**；要降级就点角色下拉改 |

**权限角色对照（GitHub 官方定义）：**

| 角色 | 能做什么 |
|---|---|
| **Read** | 只能 clone / 看代码 |
| **Write** | ✅ 本项目给这个：push 分支、开 PR、评论、改 issue |
| **Maintain** | 还能改仓库设置、管 issue 标签（本项目不需要） |
| **Admin** | 能删库、改权限（**永远别给**） |

> **兜底方案**：不想给 Write 也行——public 仓库默认允许对方 **fork** 后向你的仓库开 PR（PR 只能由你合并）。但 3 人项目里公共 DTO、前端底座有交叉，fork 模式多一层同步成本，不推荐。

### 1.3 保护 `main`（防止手滑直接 push）

`Settings` → 左侧 **`Branches`** / **`Rules`** → **Add branch ruleset**（或 `Add classic branch protection rule`），Branch name pattern 填 `main`，勾：

- ✅ Require a pull request before merging
- ✅ Require approvals: **1**
- ✅ Dismiss stale pull request approvals when new commits are pushed
- ✅ Require conversation resolution before merging
- ✅ Do not allow bypassing the above settings（Admin 也守规矩）
- ✅ Do not allow force pushes / deletions

效果：**谁都没法直接 `git push origin main`**，只能推 `feat/...` 开 PR——这就是"不会互相踩"的物理保障。

### 1.4 打开密钥扫描

`Settings` → `Code security and analysis` → 打开 **Secret scanning** 与 **Push protection**。防止 `application.yml` 里的数据库密码被推进公开仓库。

---

## 2. 一次性初始化

### 2.1 你（仓库创建者）执行

```bash
# ---- 先配本机身份（与 GitHub 账号一致，否则贡献归属会错）----
git config --global user.name  "At210Co60"
git config --global user.email "At210Co60@users.noreply.github.com"
git config --global core.autocrlf true      # Windows 换行
git config --global pull.rebase true        # 拉取默认变基，避免 "Merge branch 'main' of ..." 脏提交
git config --global rerere.enabled true     # 记住冲突解法，不重复解同一处
git config --global core.quotepath false    # 中文文件名不显示成 \344\275\240

# ---- 在项目目录（当前 HomeMedGuard/）----
git init -b main
git add .
git commit -m "chore: 初始化仓库与协作文档"
git remote add origin https://github.com/At210Co60/home-med-guard.git
git push -u origin main
```

> **前提**：远程仓库要先按 §1.1 建好，否则 push 会报 `Repository not found`。
>
> **注意**：你的用户目录下已经有一个误建的 git 仓库（`C:\Users\15271\.git`，没有任何提交，多半是某次在错误目录执行 `git init` 留下的）。它会让"在哪个目录执行 git"变得混乱，建议你确认无用后删掉 `C:\Users\15271\.git` 这个目录（只是删这个仓库元数据，不动你的文件）。本项目目录里新 `git init` 的仓库会覆盖它，互不影响。

### 2.2 另外两人（第一次加入）

> 📤 **直接把 [teammate-onboarding.md](teammate-onboarding.md) 转发给对方**：那是给他们看的独立文档，从"接受邀请"到"第一次 push 成功"每一步都有命令和报错对照表，不需要他们读本文。

```bash
git clone https://github.com/At210Co60/home-med-guard.git
cd home-med-guard
git config user.name  "B的GitHub用户名"
git config user.email "B的GitHub邮箱"
git config pull.rebase true
# 本地配置：复制占位文件，填自己的数据库密码，不要入库
copy src\main\resources\application-example.yml src\main\resources\application-local.yml
```

---

## 3. 完整上传流程（本地代码 → GitHub）

"上传"= 把本地提交发布到 GitHub。按顺序做，每一步都能单独排错。

### 3.1 上传前检查（30 秒，必做）

```bash
git status                  # 看有没有意外文件：application-local.yml / target/ / node_modules/ / 大文件
git diff --stat             # 看改了多少、改了哪些文件，是否符合预期
git diff                    # 关键逻辑再自己扫一眼
git log --oneline -5        # 确认当前分支和最近提交
git branch -vv              # 确认当前在 feat/... 分支，不是在 main 上裸奔
```

`git status` 出现红色 `??` 需要逐个确认；`application-local.yml` 如果被列出来，说明 `.gitignore` 没生效，**停下来先修**，别 `git add .`。

### 3.2 首次上传（把已有文件推上 GitHub，只做一次）

```bash
# 0) 先在网页建好空仓库（§1.1），不要勾 README/.gitignore/license
git init -b main                                  # 已经有 .git 会提示 Reinitialized，无害
git add .
git commit -m "chore: 初始化仓库与协作文档"
git remote add origin https://github.com/At210Co60/home-med-guard.git
git remote -v                                     # 确认 origin 地址
git push -u origin main

# 若远端已存在内容（比如建库时勾了 README）：
# git pull --rebase origin main   → 解冲突 → git push -u origin main
```

推送时会弹出 **Git Credential Manager** 浏览器授权窗口，登录 `At210Co60` 即可；也可以用 **PAT（Personal Access Token，勾 `repo` 权限）** 当密码。SSH 方式（可选）：

```bash
git remote set-url origin git@github.com:At210Co60/home-med-guard.git
ssh -T git@github.com          # 出现 "Hi At210Co60!" 即成功
```

### 3.3 日常上传（每完成一个小功能就传）

```bash
# 1) 分支（首次）
git switch main
git pull --rebase
git switch -c feat/m2-batch-expiry

# 2) 提交（小步，按逻辑分块）
git add -p                                   # 逐块挑，不要 git add .
git commit -m "feat(stock): 批次库存按 FEFO 排序消费"

# 3) 与 main 对齐（每天至少一次；push 前必做）
git fetch origin
git rebase origin/main                       # 冲突按 §10 处理

# 4) 本地构建通过再推（别把编译不过的代码推上去）
mvn -q -DskipTests package
cd frontend && npm run build && cd ..

# 5) 推分支
git push -u origin HEAD                      # 首次；之后同一分支直接 git push

# 6) 开 PR：终端会打印 "Create a pull request" 链接 → 点开 → 选 reviewer → 等 Approve
#    → Squash and merge → Delete branch

# 7) 同步回本地并清理
git switch main && git pull --rebase
git branch -d feat/m2-batch-expiry
```

**要求**：上传的每个分支都必须能单独构建通过。`main` 上永远是可演示状态。

### 3.4 只想上传部分改动

```bash
# 方式 1：按文件/按块挑
git add src/main/java/com/homemedguard/service/stock/StockService.java
git add -p docs/     # 逐块确认

# 方式 2：暂时收起不想要的改动
git stash push -u -m "wip: 排程调试"
git commit -m "feat(stock): FEFO 消费"
git stash pop

# 方式 3：改错了想丢弃（未提交）
git restore <file>              # 丢弃工作区改动
git restore --staged <file>     # 从暂存区撤回（保留工作区）
```

### 3.5 上传失败对照表（照着查）

| 报错 / 现象 | 原因 | 解决 |
|---|---|---|
| `remote: Repository not found` | 仓库没建 / owner 或名字写错 / 邀请没接受 | 核对 `https://github.com/At210Co60/home-med-guard`；让 Owner 重新邀请并接受 |
| `Authentication failed` / 弹窗反复要密码 | 凭据过期或存了别的账号 | `控制面板 → 凭据管理器 → Windows 凭据` 删除 `git:https://github.com`；或用 PAT |
| `Updates were rejected because the remote contains work that you do not have` | 远端有你没有的提交 | `git pull --rebase origin main` → 解冲突 → 再 push |
| `! [rejected] ... (non-fast-forward)` | 自己 rebase/amend 过，历史变了 | `git push --force-with-lease`（**只对自己分支**，永不对 main） |
| `pre-receive hook declined` / `protected branch hook declined` | 直接 push 了 `main`，被分支保护拦 | 改推 `feat/...` 分支，再开 PR |
| `Please tell me who you are` | 没配 `user.name/user.email` | `git config user.name "..."` + `git config user.email "..."` |
| `error: File: xxx 1.2 GB exceeds GitHub's file size limit` | 误加大文件进提交 | `git rm --cached <f>` 后重新提交；已推送的历史用 `git filter-repo` 清 |
| `HTTP 413` / 大文件反复失败 | 同上，或 LFS 没装 | 同上；确需大文件用 Git LFS 并写进 README |
| `fatal: not a git repository` | 在错误目录执行 | `cd` 到 `HomeMedGuard/`；`git rev-parse --show-toplevel` 确认 |
| `fatal: 'origin' does not appear to be a git repository` | 没加 remote | `git remote add origin <url>` |
| 文件名显示成 `\344\271\246` | 中文转义 | `git config --global core.quotepath false` |
| `CRLF will be replaced by LF` 警告 | Windows 换行 | 设 `core.autocrlf true`；仓库已有 `.gitattributes` 统一 |

### 3.6 已经上传了，想撤销

```bash
# A. 还没 push（本地提交）
git reset --soft HEAD~1        # 撤销提交，改动保留在暂存区，改完重新提交
git commit --amend             # 只改提交信息/补文件

# B. 已 push 到自己的 feat 分支
git commit --amend             # 或 git rebase -i HEAD~3
git push --force-with-lease    # 只对自己分支；团队里要提前说一声

# C. 已经合进 main（不要 reset 公共分支！）
git switch main && git pull --rebase
git switch -c fix/revert-bad-commit
git revert <坏提交的sha>        # 生成一个反向提交
git push -u origin HEAD        # 走正常 PR 流程

# D. 误传了密码/密钥（紧急）
#   1) 立刻到数据库/云控制台改密码、作废旧 token（这一步最重要，历史清理可以慢）
#   2) pip install git-filter-repo；git filter-repo --path <敏感文件> --invert-paths
#   3) git push --force-with-lease --all && git push --force-with-lease --tags
#   4) 通知全队重新 clone（旧 clone 里还留着历史）
```

### 3.7 上传后验收（2 分钟）

- [ ] 网页上 `main` 的 CI 是绿的（配了 §11 的 workflow 才有）
- [ ] `git log --oneline -1` 与网页最新提交一致
- [ ] 让另一个人 `git switch main && git pull --rebase` 后能编译通过
- [ ] PR 已 `Squash and merge` 且源分支已删除

---

## 4. 分支模型：轻量 trunk-based（三人最优）

| 分支 | 用途 | 生命周期 |
|---|---|---|
| `main` | 永远可运行、可打包演示 | 永久 |
| `feat/<模块>-<短描述>` | 新功能 | ≤ 3 天 |
| `fix/<模块>-<短描述>` | 修 bug | ≤ 1 天 |
| `docs/...` | 文档/答辩材料 | 随意 |
| `hotfix/...` | 演示前紧急修 | 小时级 |

**不设 `develop` 分支**：3 人项目多一条长期分支只会翻倍合并次数。里程碑用 tag 固定（§13）。

命名示例（模块名对齐 handoff 的分期与包名）：

```
feat/m1-auth-jwt
feat/m2-batch-expiry-state-machine
feat/m3-component-normalization
feat/m4-schedule-solver
feat/m5-empty-date-prediction
fix/m2-fefo-consumption
docs/defense-slides
```

---

## 5. 目录所有权表（三人开发不打架的核心）

**先按"文件/目录"划界，而不是按"功能"划界。** 每个人在自己的一片目录里随便改，跨界的文件走 PR + 打招呼。

| 区域 | 路径 | 守门人 | 规则 |
|---|---|---|---|
| 工程底座/依赖/打包 | `pom.xml`、`.mvn/`、`.github/`、`mvnw*` | **A（你）** | 别人不直接改，需要加依赖就开 issue 或 PR，由 A 合并 |
| 配置 | `src/main/resources/application*.yml` | **A** | 同上；`application-local.yml` 不入库 |
| 基础表迁移 | `src/main/resources/db/migration/V1__init.sql` | **A** | 其他人只读不改（改了全队 Flyway checksum 报错） |
| 后端公共层 | `common/`、`config/`、`dto/common`、`vo/common`、公共实体 | **A** | A 在 M1 定 `Result<T>`、错误码、分页、审计 AOP，之后冻结 |
| 前端底座 | `frontend/src/main.ts`、`router/index.ts`、`api/http.ts`、`stores/`、`styles/`、`package.json` | **C** | 装依赖、加路由先报备；其他人只往自己的 `views/` 里加 |
| 前端页面 | `frontend/src/views/<模块>/` | 各模块主责人 | 一人一目录，互不进入 |
| 类型契约 | `frontend/src/types/api.d.ts` | **A** 定契约 → C 维护同步 | 改接口字段先改这里，再改页面 |
| 种子/规则数据 | `db/migration/R__seed_*.sql` | 三人分工整理、**A 终审合并** | 必须幂等（`ON DUPLICATE KEY UPDATE`） |

### 数据库迁移：按人分段编号

Flyway 版本号全局唯一，两人同时写 `V2__` 就是必冲突。预分配号段：

| 号段 | 归属 | 用途 |
|---|---|---|
| `V1`–`V9` | **A** | 底座：`sys_user` `family` `family_member*` `sys_config` `audit_log` |
| `V10`–`V19` | **B** | M2 库存与效期：`storage_location` `family_drug` `stock_batch` `disposal_record` … |
| `V20`–`V29` | **A** | M3 规则：`component_dict` `drug_component` `interaction_rule` `contraindication_rule` `spacing_rule` … |
| `V30`–`V39` | **C** | M4 方案排程：`med_regimen` `regimen_item` `med_schedule_slot` `adherence_log` `risk_evaluation_log` |
| `V40`–`V49` | **C** | M5 余量/复诊/急救/召回 |
| `V50`–`V59` | **B** | M6 统计与后台：`stat_*`、`rule_publish_log` |
| `R__seed_*` | 三人 | 可重复迁移：药品字典、相互作用规则、配置默认值。改内容即自动重跑，不占版本号 |

新增列给别人的表？**先打招呼**，把改动写在自己号段的新文件里，别去改对方已提交的 `V*.sql`（Flyway 会因 checksum 变化启动失败；本地已乱可 `mvn flyway:repair`）。

---

## 6. ⚠️ 警戒：完全模块化开发（模块之间互不影响，但数据可通）

> **本项目第一铁律：完全模块化开发 —— 模块与模块之间互不影响，但数据可通。**
>
> - **互不影响**：每个模块可以单独开发、单独编译、单独测试、单独替换、单独开关；一个模块的 bug、崩溃、性能问题，不允许拖垮其他模块。
> - **数据可通**：模块之间共享同一套数据库与接口契约，数据必须能按契约在模块之间流转；共享的是"数据的定义"，不是"数据的写入权"。
>
> 这句话不是口号，是验收项：**M2 之后，任意关掉一个模块的开关，系统必须仍能启动，其他模块功能不受影响。**

### 6.1 落地约束（违反任何一条，PR 直接打回）

1. **一个模块 = 一个包 + 一组表 + 自己的 Controller/Service/Mapper + 自己的前端目录。**
   后端：`com.homemedguard.service.<module>`；前端：`frontend/src/views/<module>/`；表前缀按模块命名。
2. **禁止跨模块直连**：
   - ❌ A 模块的 Service 直接注入并调用 B 模块的 **Mapper**
   - ❌ 跨模块写对方的表（`UPDATE stock_batch` 只能来自 stock 模块）
   - ❌ 在 A 模块的 XML/SQL 里 JOIN B 模块的私有表
   - ✅ 只允许：注入对方暴露的 **Service 接口**，传 **DTO**，自己一行 SQL 都不写
3. **模块间通信只走两种方式**：
   - ① **同步**：对方提供的 `XxxFacade`/`XxxQueryService` 接口 + DTO（接口定义在对方模块，实现也在对方模块）
   - ② **异步**：Spring `ApplicationEventPublisher` 领域事件（如"库存变动"→ 提醒模块监听），发送方不关心谁消费
4. **表的归属唯一**：每张表有且只有一个 owner 模块，owner 之外**只读**（读也走对方的 Query 接口，不裸查）。
5. **`common/` 只许放技术设施**：统一返回 `Result<T>`、异常、错误码、加解密、审计、工具类、分页约定。
   **绝对不许**塞业务逻辑；谁把业务写进 `common/`，谁负责拆回去。
6. **禁止循环依赖**：A 依赖 B 时 B 不能依赖 A。发现循环 = 设计错误，抽公共接口到 `common/contract` 或改事件解耦，**不要互相 import**。
7. **契约先行**：接口字段变更先改 `types/api.d.ts` + DTO，再改实现；破坏性变更在 PR 描述里写清并通知全队。
8. **模块开关化**：每个模块的 Bean 用 `@ConditionalOnProperty(prefix="app.module", name="<module>", havingValue="true", matchIfMissing=true)` 或 `@Profile` 控制，`application.yml` 里有统一开关位（`app.module.stock: true` 这类）。这是"互不影响"的可验证证据。
9. **失败隔离**：模块间同步调用必须有超时/降级；定时任务按模块拆成独立 `@Scheduled` 方法（`job/expiry`、`job/schedule`），一个任务异常不阻断其他任务（`try/catch` + 日志 + 告警）。
10. **目录边界 = Git 边界**：模块目录与 §5 的所有权表一一对应，**一人一模块目录**，天然不产生 git 冲突；跨模块改动必须由两个 owner 同时 review。

### 6.2 正反例（照这个写）

```java
// ✅ 正确：只依赖对方模块对外暴露的接口 + DTO
@Service
@RequiredArgsConstructor
public class ReminderService {
    private final StockQueryFacade stockQuery;   // stock 模块暴露的接口
    public List<ReminderVO> build(Long memberId) {
        List<StockVO> stocks = stockQuery.listByMember(memberId);  // 数据可通
        ...
    }
}

// ❌ 错误：直接注入别的模块的 Mapper、跨模块查表
@Service
public class ReminderService {
    private final StockBatchMapper stockBatchMapper;  // 跨模块直连，打回
    public void x() { stockBatchMapper.selectList(null); }
}

// ✅ 正确：状态变化用事件广播，谁监听谁负责
inventoryEvents.publishEvent(new StockChangedEvent(batchId, delta));

// ✅ 正确：模块开关
@Configuration
@ConditionalOnProperty(prefix = "app.module", name = "stock", havingValue = "true", matchIfMissing = true)
public class StockModuleConfig { ... }
```

### 6.3 为什么这条对 Git 协作特别重要

- 模块解耦 → 目录不交叉 → **两人的分支几乎不会碰同一个文件** → 合并冲突自然消失。
- 契约先行 → 前后端、模块之间可以**并行开发**，不用互相等。
- 一个人重写自己模块的内部实现，**不需要通知其他人**；只要接口和数据结构不变，别人的代码不用改。
- 反之，一旦出现跨模块直连，改一个表结构就会牵动三个人的分支，冲突和返工随之而来。

### 6.4 现在不拆 Maven 多模块，但要为拆分留路

目录按模块分包、表按模块归属、接口按模块暴露——将来要拆 Maven 父子模块（`homemedguard-common` / `homemedguard-domain` / `homemedguard-web`）时，靠"搬包 + 改依赖"，不用重写。**现在别提前拆**，单模块构建快、部署简单（handoff 要的是一个 jar）。

---

## 7. 三人分工（映射 handoff 的 M1–M6）

| 人 | 里程碑 | 后端主责（模块） | 前端主责 | 迁移号段 | 备注 |
|---|---|---|---|---|---|
| **A（你）** | M1 骨架+登录+家庭组+成员档案；M3 规则引擎 | `common` `config` `security` `service/rule` | 前端底座、登录/家庭组、风险评估页 | V1–V9、V20–29 | 兼工程守门人、集成、打包、部署文档、答辩材料 |
| **B** | M2 库存+双轨效期+三级预警+处置；M6 报表与后台 | `service/expiry` `service/stock` `job/expiry` `mapper/report` `service/report` | 库存/预警/处置/统计报表 | V10–19、V50–59 | M2 是第一个可演示版本，优先做 |
| **C** | M4 排程求解+打卡依从性；M5 余量+复诊+急救+召回 | `service/schedule` `service/reminder` `service/forecast` `job/schedule` | 医嘱方案/时间表/打卡/复诊/急救 | V30–39、V40–49 | 前端公共组件（拦截器、权限指令、图表封装）也归 C |

交叉点处理（按 §6 的模块化铁律，提前说好）：

- **`service/stock` 包**：B 管批次与消耗（`StockBatch*`、`Consume*`），C 的余量推算放**独立模块包** `service/forecast/`，只通过 B 暴露的 `StockQueryFacade` 拿数据，**不注入 B 的 Mapper、不写 `stock_batch`**。
- **规则种子数据**：三人按药品类别分头整理（B 家庭常备药、C 慢病药、A 特殊人群），各写 `R__seed_rule_<类别>.sql`，A 终审合并——不同文件，天然无冲突。
- **接口契约**：A 在 M1 定完 `Result<T>`/错误码/分页后，任何接口字段变更先改 `types/api.d.ts` 与 DTO，再改实现；前端按契约写，不猜字段。

### 拆成 Issue（第一天做，30 分钟）

GitHub → Issues → 按上表给每个子任务开 issue，`Milestone` 选 M1…M6，`Labels`：

```
be:auth  be:stock  be:rule  be:schedule  be:report
fe:page  db:migration  module  risk  docs  infra
```

Projects 看板四列 `Todo / Doing / Review / Done`，一人最多同时挂 2 张 `Doing`。

---

## 8. 每天的标准动作

```bash
# ---- 开工 ----
git switch main
git pull --rebase
git switch -c feat/m2-batch-expiry

# ---- 写代码：小步提交，按逻辑分块 ----
git add -p
git commit -m "feat(stock): 批次库存按 FEFO 排序消费"

# ---- 每天至少一次，收工前必做 ----
git fetch origin
git rebase origin/main
git push -u origin HEAD

# ---- 开 PR → 他人 Approve → Squash and merge → 删分支 ----
```

纪律：

- **分支活得越久，冲突越狠**。一个 PR 只做一件事，diff 控制在 400 行内。
- 每完成一个可运行的小功能就推上去，别攒到最后一天。
- 每天 10–15 分钟站会：**昨天 / 今天 / 阻塞**。跨模块依赖当天就提。

---

## 9. PR 与合并规则

**PR 标题**与 commit 一致；描述模板：

```markdown
## 做了什么
- 新增 stock_batch 表与 FEFO 消费逻辑

## 怎么验证
- [ ] `mvn -q -DskipTests package` 通过
- [ ] 前端 `npm run build` 通过
- [ ] 本地走通"入库→临期→处置"
- [ ] 新增单测 `ExpiryStateMachineTest`

## 影响面 / 需要别人注意
- 迁移 V10__stock_batch.sql；新增 `StockQueryFacade` 接口（供 forecast 模块用）
- 模块开关：`app.module.stock`
```

合并方式：**Squash and merge**（把分支压成一个提交进 `main`），不要 `Create a merge commit`。

**Review 清单（reviewer 只看这些）**：

- [ ] 没有把 `application-local.yml`、`target/`、`node_modules/`、真实数据带进来
- [ ] 新业务逻辑有单测（规则引擎、排程求解**必须**有表驱动测试，handoff §一 已要求）
- [ ] 时间计算只在后端 `java.time` + `Asia/Shanghai`，前端只做展示（handoff §四.6）
- [ ] 没有出现"诊断""安全用药已确认"等违规表述，风险页有免责声明
- [ ] 接口变更同步了 `types/api.d.ts`
- [ ] 迁移号段没有越界
- [ ] **符合 §6 模块化铁律**：无跨模块 Mapper、无跨模块写表、无循环依赖

**谁能合**：自己开 PR、别人 approve 后由作者自己 Squash merge（3 人团队不必让 A 当唯一合并口，A 只守 §5 的共享文件）。

---

## 10. 冲突解决标准动作

90% 的冲突发生在 `pom.xml`、`package.json`、`router/index.ts`、`application.yml`、`V*.sql`、`types/api.d.ts`。

```bash
git fetch origin
git rebase origin/main
#   冲突文件会标 both modified
#   打开文件，保留两边需要的内容（不要无脑取 ours/theirs）
git add <file>
git rebase --continue
#   想整体放弃这次 rebase：
git rebase --abort
```

规矩：

- **不要在 rebase 过程中用 `git merge` 解决**，也别 `git checkout --ours .` 盲选，那会静默丢别人的代码。
- 两个人都要改同一个共享文件时：**先约好**，一个人改完合进 `main`，另一个 rebase 后再改。共享文件不要并行改。
- 自己的分支需要重写历史：`git push --force-with-lease`（**永不** `--force` 到 `main`）。
- 解完冲突**先在本地跑一次构建**再 push：`mvn -q -DskipTests package`（冲突常在 XML/JSON 里留语法错）。

---

## 11. 提交规范与 CI

### Commit（Conventional Commits，中文描述即可）

```
<type>(<scope>): <一句话说明>
```

`type`：`feat` `fix` `refactor` `test` `docs` `chore` `perf` `build`
`scope`：`auth` `family` `drug` `stock` `expiry` `rule` `schedule` `reminder` `report` `admin` `web` `infra` `db`

```bash
feat(rule): 成分归一化与重复成分叠加检测
fix(schedule): 间隔约束回溯时死循环
docs(readme): 补充免责声明与规则来源说明
test(expiry): 双轨时效 28/90 天表驱动用例
db(stock): 新增 V10 批次库存表
```

### CI（M1 就加，否则 `main` 迟早坏）

`.github/workflows/ci.yml`：

```yaml
name: CI
on:
  pull_request:
  push: { branches: [main] }
jobs:
  backend-build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }
      - run: mvn -B -ntp -DskipTests compile
      - run: mvn -B -ntp test          # 需要 DB 的集成测试用 @Tag("it") 排除
  frontend-build:
    runs-on: ubuntu-latest
    defaults: { run: { working-directory: frontend } }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '24', cache: npm, cache-dependency-path: frontend/package-lock.json }
      - run: npm ci
      - run: npm run build
```

> `frontend-maven-plugin` 会在 `mvn package` 时自动装 Node 并构建前端——CI 里为了快，用自定义 profile `-Dskip.frontend=true` 跳过，前端单独 job 验。**不要**把 `src/main/resources/static/` 提交进仓库。

提交前本地自检：

```bash
mvn -q spotless:apply
cd frontend && npm run lint && npm run build
git status                       # 再看一眼有没有脏文件
```

---

## 12. 保密与忽略（public 仓库红线）

- `application.yml` 里**不允许**出现真实密码，只写占位符或 `${DB_PASSWORD}`；真实值放 `application-local.yml`（已被 `.gitignore`）。
- 仓库根放 `src/main/resources/application-example.yml` 作为模板，字段齐全、值全占位符。
- 不入库：`target/`、`node_modules/`、`frontend/dist/`、`src/main/resources/static/`、`data/files/`、`*.log`、`.env`、IDE 配置。
- 演示数据一律假人名/假病历；说明书图片、扫描件放 `data/files/`（运行时目录，已忽略），需要入库的演示小图放 `docs/demo/` 且单张 ≤ 200KB。
- **误提交密钥**：① 立刻轮换密码/作废 token；② `git filter-repo` 清历史并强推；③ 全队重新 clone。只删文件不管历史等于没删（public 仓库可能已被爬）。

---

## 13. 里程碑与发版

```bash
# 每个 M 结束、答辩前，打一个 tag 冻结代码
git switch main && git pull --rebase
git tag -a v0.1.0-m1 -m "M1: 骨架 + 登录 + 家庭组 + 成员档案 + 药品字典"
git push origin v0.1.0-m1
```

GitHub → Releases 基于 tag 写"本版可演示功能 + 启动步骤 + 已知问题"。答辩前最后一天只做 `fix/` 和文档，不开 `feat/`。

---

## 14. 救命命令

| 场景 | 命令 |
|---|---|
| 改乱了某个文件想还原 | `git restore <file>` |
| 想临时收起未完成改动 | `git stash push -u -m "wip: 排程"` → 之后 `git stash pop` |
| 刚提交但还没 push，想改提交信息 | `git commit --amend` |
| 刚提交但还没 push，想撤销 | `git reset --soft HEAD~1` |
| 提交到了公共分支且已 push | `git revert <sha>`（**不要** `reset` 公共分支） |
| 自己分支历史乱了要强推 | `git push --force-with-lease` |
| 不知道自己在哪个仓库/分支 | `git rev-parse --show-toplevel` + `git branch -vv` |
| 想把别人的某次提交拿到自己分支 | `git cherry-pick <sha>` |

---

## 15. 第一个 Wed 的"协作演练"（30 分钟，强烈建议做）

三人各走一遍完整链路，把流程跑熟再写业务代码：

1. 每人 `git switch -c docs/hello-<name>`，新建 `docs/hello-<name>.md` 写一句话。
2. `git add` → `commit -m "docs: hello from <name>"` → `push -u origin HEAD`。
3. 网页开 PR，指定另一人 review。
4. reviewer 点 `Approve` → 作者点 `Squash and merge` → 删分支。
5. 三人 `git switch main && git pull --rebase`，看到三个 hello 文件都在。

演练顺带验证 §1.2 的写权限是否真的生效、§3.3 的上传流程是否跑得通。

---

## 16. 每周节奏

| 时间 | 动作 |
|---|---|
| 每天 ~10 min | 站会：昨日/今日/阻塞；确认今天有没有人动共享文件 |
| 周三 | 中间集成：各自分支 rebase `main` 后本地跑一次 `mvn package`，提前暴露碰撞 |
| 周五 | 合并 + 打 tag（两周一个 M，按 handoff §六 的节奏） |
| 每 M 末 | 演示走查：只从 `main` 拉代码，按 README 从零跑一遍（验证"新人可复现"） |

---

## 附：与 handoff 文档的对应关系

| handoff 章节 | 本手册对应 |
|---|---|
| §一 技术栈 / 构建打包 | §5 目录所有权、§11 CI（`frontend-maven-plugin`、`static/` 不入库） |
| §三 数据库表清单 | §5 迁移号段表 + §7 分工 |
| §六 建议分期 M1–M6 | §7 三人分工、§13 tag、§16 节奏 |
| §七 风险（密码、公开仓库、假数据） | §12 保密与忽略 |
| 项目架构要求 | §6 完全模块化开发铁律 |

> 有任何与 `handoff.md` 冲突的地方，以 `handoff.md` 的技术选型为准，本手册只补协作流程与模块化约束。