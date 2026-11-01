# 新成员接入指南（B / C 直接照着做）

> 你是 **HomeMedGuard** 的协作者。这份文档从零到"把代码传进 `At210Co60/home-med-guard`"，约 10 分钟。
> 完整协作规则见 [git-collaboration.md](git-collaboration.md)，本文只讲**第一次接入**和**每次怎么上传**。

---

## 0. 先确认两件事（1 分钟，传不上去多半是这里没做）

1. **你已经被 Owner 加成 Collaborator（Write 权限）** —— 让 A 在仓库 `Settings → Collaborators → Add people` 里加你的 GitHub 用户名。
2. **你已经点过"接受邀请"** —— GitHub 右上角铃铛（https://github.com/notifications）或邮箱里的 `View invitation → Accept invitation`。
   ⚠️ 没接受邀请，后面 push 一定报 `Repository not found`，不是权限没给，是邀请没接。

确认方式：浏览器能打开 https://github.com/At210Co60/home-med-guard 并看到代码。

---

## 1. 一次性环境准备（装一次，以后不用再弄）

```bash
git --version      # 没装就先装 Git for Windows: winget install Git.Git

# 身份（必须和你 GitHub 账号一致）
git config --global user.name  "你的GitHub用户名"
git config --global user.email "你的GitHub邮箱"

# Windows 换行 + 拉取变基 + 冲突记忆 + 中文文件名
git config --global core.autocrlf true
git config --global pull.rebase true
git config --global rerere.enabled true
git config --global core.quotepath false
```

---

## 2. 把仓库克隆到本地

```bash
cd D:\work                                  # 你放项目的目录（别放在 C:\Users\<你> 根目录）
git clone https://github.com/At210Co60/home-med-guard.git
cd home-med-guard
git remote -v                               # 必须指向 https://github.com/At210Co60/home-med-guard.git
git log --oneline -3                        # 能看到提交 = 克隆成功
```

> ❌ **不要用网页上的 Fork**。我们采用"**同一个仓库 + 各自分支**"模式：你的 `origin` 就是 A 的仓库，你推上去的分支和 PR 都直接长在 A 的仓库里。
> ✅ 只有在你**没有** Write 权限（只读）时，才走 fork：`Fork → clone 自己的 fork → 推自己的 fork → 网页向 At210Co60/home-med-guard 开 PR`。

---

## 3. 验证 Write 权限（第一次必做，30 秒）

```bash
git switch -c test/hello-你的名字
git commit --allow-empty -m "chore: 权限验证"
git push -u origin HEAD
```

- 第一次推送会弹出 **Git Credential Manager** 窗口：选 `Browser` 用**你自己的 GitHub 账号**登录授权（不是 A 的账号）。
- 看到 `new branch test/hello-xxx` = 权限 OK。

删掉测试分支（别留着）：

```bash
git push origin --delete test/hello-你的名字
git switch main
git branch -D test/hello-你的名字
```

推送失败 → 直接看 §7 的对照表。

---

## 4. 本地配置（不泄密、不提问）

M1 骨架推上来之后，本地复制一份配置模板并填**你自己的**数据库账号：

```bash
copy src\main\resources\application-example.yml src\main\resources\application-local.yml
# 然后用编辑器改 application-local.yml 里的 MySQL 账号密码
```

- `application-local.yml` 已在 `.gitignore` 里，**永远不会被上传**，放心填。
- 别把密码写进 `application.yml`（那个文件会入库）。
- MySQL 建库（每人本地一个库）：
  ```bash
  mysql -u root -p -e "CREATE DATABASE home_med_guard DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
  ```

---

## 5. 上传流程（每次做完一个功能）

> **核心**：你不直接往 `main` 传。你把自己的分支推到 A 的仓库，再开 PR 让 `main` 合并它。
> `main` 有分支保护，**直接 push main 会被拒绝**（这是故意的）。

```bash
# ① 从最新 main 开分支
git switch main
git pull --rebase
git switch -c feat/stock-batch-fefo          # 命名：feat/<模块>-<短描述>

# ② 写代码，小步提交（按逻辑分块，不要 git add .）
git add -p
git commit -m "feat(stock): 批次库存按 FEFO 排序消费"

# ③ 和 main 对齐（push 前必做，每天至少一次）
git fetch origin
git rebase origin/main

# ④ 本地先构建通过再推（M1 骨架还没有 pom.xml 时跳过）
mvn -q -DskipTests package
cd frontend && npm run build && cd ..

# ⑤ 推你自己的分支（不是 main）
git push -u origin HEAD
# 终端会打印 "Create a pull request" 链接 → 点开
# 同一分支以后继续提交，直接 git push 即可（不用再加 -u）

# ⑥ 网页开 PR：标题同提交信息；base = main；reviewer 选 A 或另一个队友 → Create pull request
# ⑦ 拿到 1 个 Approve 后，作者自己点 "Squash and merge" → "Delete branch"
# ⑧ 回本地同步
git switch main
git pull --rebase
git branch -d feat/stock-batch-fefo
```

**团队怎么知道"你上传了"**：GitHub 上直接能看到你的分支和 PR，不用在群里喊。PR 描述里写清"做了什么 / 怎么验证 / 影响面"（模板见 [git-collaboration.md §9](git-collaboration.md)）。

---

## 6. "上传成功"的验收标准（自己先过一遍）

- [ ] GitHub 上能看到你的分支，以及一个指向 `main` 的 PR
- [ ] PR 的 `Files changed` 里**没有** `application-local.yml`、`target/`、`node_modules/`、`static/`、大于 5MB 的文件
- [ ] 本地 `mvn package` / `npm run build` 通过，PR 里的 CI 是绿的
- [ ] 1 人 Approve 后已 `Squash and merge`，源分支已删除

---

## 7. 上传失败对照表（照着查）

| 报错 / 现象 | 原因 | 解决 |
|---|---|---|
| `remote: Repository not found` | ① 邀请没接受 ② 用错 GitHub 账号 ③ 地址打错 | 先接受邀请；`控制面板 → 凭据管理器 → Windows 凭据` 删掉 `git:https://github.com`，重新登录自己的账号 |
| `Permission denied (publickey)` | 用了 SSH 地址但没配 key | 换回 https：`git remote set-url origin https://github.com/At210Co60/home-med-guard.git` |
| `Authentication failed` / 反复弹窗 | 凭据存了别人的账号 | 同上，删掉 Windows 凭据后重新授权 |
| `schannel: ... CRYPT_E_NO_REVOCATION_CHECK` | 证书吊销检查被网络/代理挡住（国内常见） | 单次：`git -c http.schannelCheckRevoke=false push`；长期：`git config --global http.schannelCheckRevoke false` |
| `Failed to connect to github.com port 443` / 超时 | 网络问题（国内访问 github.com 常抖，`api.github.com` 通不代表 git 通） | 开代理/VPN；设代理：`git config --global http.proxy http://127.0.0.1:7890` 和 `git config --global https.proxy http://127.0.0.1:7890`；关代理：`git config --global --unset http.proxy` |
| `Updates were rejected because the remote contains work you do not have` | 远端有别人新提交 | `git pull --rebase origin main` → 解冲突 → 再 push |
| `protected branch hook declined` / `remote rejected` | 直接推了 `main` | 改成推 `feat/...` 分支，走 PR |
| `! [rejected] ... (non-fast-forward)` | 自己 rebase/amend 过，历史变了 | `git push --force-with-lease`（**只对自己分支**） |
| `Please tell me who you are` | 没配身份 | §1 的 `user.name` / `user.email` |
| 中文文件名显示成 `\344\275\240` | 转义显示 | `git config --global core.quotepath false` |
| `you are not currently on a branch` | 在 detached HEAD | `git switch -c feat/xxx` 重新锚定 |

冲突处理见 [git-collaboration.md §10](git-collaboration.md)：`git rebase origin/main` → 改文件 → `git add` → `git rebase --continue`，**不要** `checkout --ours` 盲选。

---

## 8. 三条不能破的规矩

1. **`main` 只进 PR**：不直接推、不强推；合并用 Squash and merge。
2. **模块化铁律**：模块之间互不影响、数据可通——不跨模块注入别人 Mapper、不跨模块写别人的表、不跨模块 JOIN 私有表；只走对方暴露的 `XxxFacade` + DTO，或领域事件。`common/` 不放业务。详见 [git-collaboration.md §6](git-collaboration.md)。
3. **共享文件先打招呼**：`pom.xml`、`application*.yml`、`frontend/package.json`、`router/index.ts`、`V1__init.sql`、`types/api.d.ts` 由守门人改。
   Flyway 号段：**B = V10–V19、V50–V59；C = V30–V39、V40–V49**；种子数据写 `R__seed_*.sql`。

---

## 9. 一页速查（复制到群里发）

```text
# 第一次
git clone https://github.com/At210Co60/home-med-guard.git
git config --global user.name "你的GitHub用户名"
git config --global user.email "你的GitHub邮箱"
git config --global core.autocrlf true
git config --global pull.rebase true

# 每次上传
git switch main && git pull --rebase
git switch -c feat/<模块>-<描述>
git add -p && git commit -m "feat(<scope>): 说明"
git fetch origin && git rebase origin/main
git push -u origin HEAD      # 然后网页开 PR → Approve → Squash and merge
```

> 记不住就记一句：**推到自己的分支，让 main 通过 PR 合并你；永远不直接推 main。**