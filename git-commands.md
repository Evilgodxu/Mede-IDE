# Git 指令大全

## 基础配置

```bash
# 设置用户名和邮箱
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"

# 查看配置
git config --list
```

**示例:**
```bash
git config --global user.name "张三"
git config --global user.email "zhangsan@example.com"
```

---

## 仓库操作

```bash
# 初始化新仓库
git init

# 克隆远程仓库
git clone <url>
git clone <url> <directory-name>
```

**示例:**
```bash
# 在当前目录初始化
git init

# 克隆 GitHub 项目
git clone https://github.com/user/project.git

# 克隆到指定目录
git clone https://github.com/user/project.git my-project
```

---

## 文件操作

```bash
# 查看文件状态
git status

# 添加文件到暂存区
git add <file>           # 添加指定文件
git add .                # 添加所有修改

# 提交更改
git commit -m "message"
git commit -am "message" # 添加并提交

# 撤销操作
git restore <file>       # 撤销文件修改
git restore --staged <file>  # 取消暂存
```

**示例:**
```bash
# 查看当前状态
git status

# 添加单个文件
git add src/main.js

# 添加所有修改
git add .

# 提交
git commit -m "feat: 添加用户登录功能"

# 修改了文件想撤销
git restore src/main.js

# 不小心 git add 了，想取消
git restore --staged src/main.js
```

---

## 分支管理

```bash
# 查看分支
git branch               # 本地分支
git branch -r            # 远程分支
git branch -a            # 所有分支

# 创建/切换分支
git branch <name>        # 创建分支
git checkout <name>      # 切换分支
git checkout -b <name>   # 创建并切换
git switch <name>        # 新方式切换
git switch -c <name>     # 新方式创建并切换

# 合并分支
git merge <branch>
git merge --abort        # 取消合并

# 删除分支
git branch -d <name>     # 删除已合并分支
git branch -D <name>     # 强制删除
```

**示例:**
```bash
# 查看所有分支
git branch -a

# 创建新功能分支
git checkout -b feature/login

# 或新语法
git switch -c feature/login

# 开发完成，切回 main
git switch main

# 合并功能分支
git merge feature/login

# 删除已合并的功能分支
git branch -d feature/login

# 分支还没合并但想强制删除
git branch -D feature/login
```

---

## 远程操作

```bash
# 查看远程仓库
git remote -v

# 添加/修改远程仓库
git remote add origin <url>
git remote set-url origin <url>

# 推送/拉取
git push origin <branch>
git push -u origin <branch>  # 首次推送并关联
git pull origin <branch>
git fetch origin

# 删除远程分支
git push origin --delete <branch>
```

**示例:**
```bash
# 查看远程仓库地址
git remote -v

# 关联远程仓库
git remote add origin https://github.com/user/repo.git

# 修改远程地址
git remote set-url origin https://github.com/newuser/newrepo.git

# 首次推送并关联
git push -u origin main

# 后续推送
git push

# 拉取最新代码
git pull origin main

# 删除远程分支
git push origin --delete old-branch
```

---

## 查看历史

```bash
# 提交日志
git log
git log --oneline        # 简洁模式
git log --graph          # 图形化显示
git log -n 5             # 最近5条

# 查看修改
git diff                 # 工作区 vs 暂存区
git diff --staged        # 暂存区 vs 最新提交
git diff <commit> <commit>

# 查看某次提交详情
git show <commit-hash>
```

**示例:**
```bash
# 查看提交历史（简洁）
git log --oneline

# 图形化显示分支合并历史
git log --graph --oneline --all

# 查看最近3条
git log -n 3

# 查看工作区修改了哪些内容
git diff

# 查看已暂存的修改
git diff --staged

# 查看某次提交的详细修改
git show abc1234
```

---

## 撤销与回退

```bash
# 回退版本
git reset --soft HEAD~1   # 回退到上一版本，保留修改
git reset --mixed HEAD~1  # 回退到上一版本，取消暂存
git reset --hard HEAD~1   # 回退到上一版本，丢弃修改

# 查看所有操作记录
git reflog

# 撤销某次提交（生成新提交）
git revert <commit-hash>
```

**示例:**
```bash
# 刚提交完发现有问题，想重新提交
git reset --soft HEAD~1
# 修改后重新提交
git commit -m "修正后的提交"

# 误操作 reset --hard 了，想找回
git reflog
# 找到之前的 commit hash，比如 abc1234
git reset --hard abc1234

# 想撤销某次已推送的提交（安全方式）
git revert abc1234
# 这会生成一个新的提交，撤销那次修改
```

---

## 标签管理

```bash
# 创建标签
git tag <tagname>
git tag -a <tagname> -m "message"

# 推送标签到远程
git push origin <tagname>
git push origin --tags

# 删除标签
git tag -d <tagname>
```

**示例:**
```bash
# 给当前版本打标签
git tag -a v1.0.0 -m "第一个正式版本"

# 推送标签到远程
git push origin v1.0.0

# 推送所有标签
git push origin --tags

# 删除本地标签
git tag -d v1.0.0
```

---

## 储藏（Stash）

```bash
# 临时保存修改
git stash
git stash push -m "描述"

# 查看储藏列表
git stash list

# 恢复储藏
git stash pop            # 恢复并删除
git stash apply          # 恢复不删除
git stash drop           # 删除储藏
```

**示例:**
```bash
# 正在开发，临时需要切到其他分支修复bug
git stash push -m "登录功能开发中"

# 查看储藏列表
git stash list
# stash@{0}: On main: 登录功能开发中

# 修复完bug回来，恢复储藏
git stash pop

# 或者只恢复不删除储藏
git stash apply stash@{0}
```

---

## 常用场景示例

### 场景1: 日常开发流程
```bash
# 开始工作前拉取最新代码
git pull origin main

# 修改代码...

# 提交修改
git add .
git commit -m "fix: 修复按钮样式"

# 推送
git push origin main
```

### 场景2: 功能开发流程
```bash
# 从 main 创建功能分支
git checkout -b feature/user-profile

# 开发功能...
git add .
git commit -m "feat: 添加用户资料页"
git commit -m "feat: 添加头像上传"

# 推送到远程
git push -u origin feature/user-profile

# 创建 Pull Request 合并后，删除本地分支
git checkout main
git pull origin main
git branch -d feature/user-profile
```

### 场景3: 解决冲突
```bash
# 拉取代码遇到冲突
git pull origin main
# Auto-merging src/main.js
# CONFLICT (content): Merge conflict in src/main.js

# 打开冲突文件，手动解决冲突标记
# <<<<<<< HEAD
# 你的代码
# =======
# 别人的代码
# >>>>>>> main

# 解决后标记为已解决
git add src/main.js

# 完成合并
git commit -m "merge: 解决合并冲突"
```

### 场景4: 提交信息写错了
```bash
# 修改最后一次提交信息
git commit --amend -m "正确的提交信息"

# 修改最后一次提交，添加遗漏的文件
git add forgotten-file.js
git commit --amend --no-edit
```

### 场景5: 忽略文件
```bash
# 创建 .gitignore 文件
echo "node_modules/" > .gitignore
echo ".env" >> .gitignore
echo "*.log" >> .gitignore

# 添加并提交
git add .gitignore
git commit -m "chore: 添加 gitignore"
```

### 场景6: 查看谁改了哪行代码
```bash
# 查看文件的每一行是谁写的
git blame src/main.js

# 查看某个函数的修改历史
git log -p -S "functionName" -- src/main.js
```
