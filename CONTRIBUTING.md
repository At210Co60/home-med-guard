# 贡献指南（HomeMedGuard · 三人协作）

完整流程见 **[docs/git-collaboration.md](docs/git-collaboration.md)**（含给协作者开写权限的完整操作、完整上传流程、上传失败对照表、冲突处理）。

## ⚠️ 第一铁律：完全模块化开发

> **模块与模块之间互不影响，但数据可通。**
> 可独立开发/编译/测试/开关/替换；一个模块出问题不拖垮其他模块；数据按契约经接口或事件流转。

违反即打回：

- ❌ 跨模块注入别人的 Mapper、跨模块写别人的表、跨模块 JOIN 私有表
- ✅ 只用对方暴露的 `XxxFacade` / `XxxQueryService` 接口 + DTO
- ✅ 跨模块状态变化用 `ApplicationEventPublisher` 事件解耦
- ✅ `common/` 只放技术设施，不放业务
- ✅ 禁止循环依赖；模块开关写在 `app.module.*`
- ✅ 一人一模块目录，跨模块改动两个 owner 一起 review

详细约束与正反例见 [docs/git-collaboration.md §6](docs/git-collaboration.md)。

## 三条硬规则

1. **`main` 只进 PR**：不允许直接 push，PR 需 1 人 Approve，用 **Squash and merge** 合并。
2. **共享文件单人守门**：`pom.xml`、`application*.yml`、`frontend/package.json`、`frontend/src/router/index.ts`、`src/main/resources/db/migration/V1__init.sql`、`frontend/src/types/api.d.ts` —— 要改先打招呼，由守门人合并。
3. **Flyway 迁移号按人分段**：`V1–V9`=A，`V10–V19`=B，`V20–V29`=A，`V30–V39`=C，`V40–V49`=C，`V50–V59`=B；规则种子数据写 `R__seed_*.sql`（幂等）。

## 分支命名

```
feat/<模块>-<短描述>    例：feat/m3-rule-engine
fix/<模块>-<短描述>     例：fix/m2-fefo-consumption
docs/<描述>             例：docs/defense-slides
hotfix/<描述>           演示前紧急修
```

分支 ≤ 3 天；一个 PR 只做一件事，diff 尽量 < 400 行。

## 提交信息

```
<type>(<scope>): <中文说明>
```

- type：`feat` `fix` `refactor` `test` `docs` `chore` `perf` `build`
- scope：`auth` `family` `drug` `stock` `expiry` `rule` `schedule` `reminder` `report` `admin` `web` `infra` `db`

## 上传（推到 GitHub）

```bash
git status                                  # 先看有没有脏文件/密钥
git add -p                                  # 逐块暂存
git commit -m "feat(scope): ..."
git fetch origin && git rebase origin/main  # push 前必做
mvn -q -DskipTests package                  # 本地先构建通过
git push -u origin HEAD                     # 推分支（不是 main）
# 网页开 PR → Approve → Squash and merge → 删分支
```

## PR 检查清单（作者自查 + reviewer 确认）

- [ ] `mvn -q -DskipTests package` 通过；前端 `npm run build` 通过
- [ ] 新业务逻辑有单测；规则引擎/排程求解有表驱动测试
- [ ] 没有提交 `application-local.yml`、`target/`、`node_modules/`、真实健康数据
- [ ] 迁移文件号段属于自己，未修改他人已提交的 `V*.sql`
- [ ] 接口字段变更同步了 `frontend/src/types/api.d.ts`
- [ ] 时间计算只在后端 `java.time` + `Asia/Shanghai`
- [ ] 无"诊断/安全用药已确认"等违规表述，风险页有免责声明
- [ ] 符合模块化铁律：无跨模块 Mapper、无跨模块写表、无循环依赖

## 每天

```bash
git switch main && git pull --rebase
git switch -c feat/xxx
git add -p && git commit -m "feat(scope): ..."
git fetch origin && git rebase origin/main
git push -u origin HEAD
```

收工前必做一次 `git fetch && git rebase origin/main`，别把冲突留到合并当天。