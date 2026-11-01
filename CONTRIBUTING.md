# 贡献指南（HomeMedGuard · 三人协作）

完整流程见 **[docs/git-collaboration.md](docs/git-collaboration.md)**（含给协作者开写权限的完整操作、完整上传流程、上传失败对照表、冲突处理）。

## 怎么把代码弄进 main（二选一，不再强制走 PR）

```bash
# 方式 A：直推 main（小改动、自己模块内的功能，推荐日常用）
git pull --rebase                    # 推之前必做
mvn -B -DskipTests package           # 构建过得了再推
git push

# 方式 B：开 PR（跨模块改动、改了别人的东西、想要留个评审记录时用）
git push -u origin HEAD && gh pr create --base main --head <分支> --fill
```

- 谁写的代码谁自己合，不需要别人 approve；PR 用 squash 还是 merge 由作者决定
  （想保留自己的提交署名就用 merge commit，squash 会把作者算到发起人头上）
- 号段与技术约定见下面两节，它们不是流程门槛，是为了让三个人的分支不互相踩

## 唯一不能松的两条底线（安全，不是流程）

1. **密钥不进仓库**：`HMG_JWT_SECRET`、`HMG_DATA_KEY`、数据库密码只能放环境变量或 `application-local.yml`（已 gitignore）。公开仓库泄露过一次就要改库并清历史
2. **真实病历/真实家人数据不进仓库**：演示一律用假数据，连截图也不行

## 技术约定：模块之间互不影响，但数据可通

> 这条留着是因为它真的管用：ALSJJF 的 M2（+2985 行）从最新 main 切出去，**零冲突合入**，
> 全程没碰 `pom.xml`、没读别人的表，靠的就是下面这几条。

- 一个模块 = `service/<模块>` 一套目录 + 自己号段的 `V*.sql` + 模块开关 `app.module.<模块>`
- 跨模块只走对方暴露的 `XxxFacade` / `XxxQueryService` 接口 + DTO，或用 `ApplicationEventPublisher` 事件；**不注入别人的 Mapper、不 JOIN 别人的表**
- 每张表只有一个 owner，别人只读（读也走接口）；`common/` 只放技术设施，不放业务
- 禁止循环依赖。拿不准就发事件，别互相 import

## Flyway 号段（防撞号，不是审批）

`V1–V9`=A，`V10–V19`=B，`V20–V29`=A，`V30–V39`=C，`V40–V49`=C，`V50–V59`=B；规则种子数据写 `R__seed_*.sql`（幂等）。

**唯一硬限制**：已经合进 main 的 `V*.sql` 不能改内容——Flyway 会校验 checksum，改了全队启动就报错。要改就新开一个版本号。

## 分支命名

```
feat/<模块>-<短描述>    例：feat/m3-rule-engine
fix/<模块>-<短描述>     例：fix/m2-fefo-consumption
docs/<描述>             例：docs/defense-slides
hotfix/<描述>           演示前紧急修
```

分支活得越短越好（建议 ≤ 3 天）；一次改动做一件事，diff 别攒太大——这是建议，不是门槛。

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
mvn -B -DskipTests package                  # 本地先构建通过
git push                                    # 直推 main；或 git push -u origin HEAD 后开 PR
```

## 提交前自查清单

- [ ] `mvn -B -DskipTests package` 通过；前端 `npm run build` 通过
- [ ] 新业务逻辑有单测；规则引擎/排程求解有表驱动测试
- [ ] 没有提交 `application-local.yml`、`target/`、`node_modules/`、真实健康数据、任何密钥
- [ ] 迁移号段没撞别人的号，没改已入库的 `V*.sql`
- [ ] 接口字段变更同步了 `frontend/src/types/api.d.ts`
- [ ] 时间计算只在后端 `java.time` + `Asia/Shanghai`
- [ ] 无"诊断/安全用药已确认"等违规表述，风险页有免责声明
- [ ] 跨模块只走 Facade/事件：没注入别人的 Mapper、没写别人的表、没循环依赖

## 每天

```bash
git switch main && git pull --rebase
git switch -c feat/xxx
git add -p && git commit -m "feat(scope): ..."
git fetch origin && git rebase origin/main
git push -u origin HEAD
```

收工前必做一次 `git fetch && git rebase origin/main`，别把冲突留到合并当天。