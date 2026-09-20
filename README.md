# RatingSystem 智能考试辅助评分系统

## 1. 项目简介

RatingSystem 是供教师在本机使用的辅助阅卷系统。学校现有的极域课堂继续负责考试和收卷，本系统负责创建试卷评分方案、导入学生 DOCX 答卷、生成建议分数、教师人工审核以及汇总最终成绩。

系统支持手动创建试卷，也可以通过 AI 从文字型 DOCX 试卷生成可编辑草稿；支持以 ZIP 形式批量导入学生 DOCX 答卷。AI 和程序生成的分数都只是建议，最终成绩必须由教师逐题确认。

本系统不是在线考试平台，不提供学生登录、在线交卷或防作弊功能。

## 2. 下载与部署

### 2.1 环境要求

- Java 17
- MySQL 8
- 支持现代 JavaScript 的浏览器
- 如需 AI 导入试卷或 AI 建议评分，需要可用的 DeepSeek API Key 和网络连接

项目自带 Maven Wrapper，不需要另外安装 Maven。首次构建时需要联网下载 Maven 和项目依赖。

### 2.2 下载项目

本项目位于 GitHub 私有仓库，下载前需要先获得仓库访问权限。

使用 Git 下载：

```powershell
git clone https://github.com/ChengYuGallagher/RatingSystem.git
cd RatingSystem
```

也可以在 GitHub 仓库页面选择 **Code → Download ZIP**，解压后在 PowerShell 中进入项目根目录。

### 2.3 准备数据库

先安装并启动 MySQL 8，然后使用具有建库权限的账号登录 MySQL，执行以下 SQL 创建专用空数据库：

```sql
CREATE DATABASE rating_system
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;
```

Flyway 会在应用首次连接该空数据库时自动执行 V1、V2、V3 迁移并创建业务表。Flyway 不会安装或启动 MySQL，也不会代替上述建库操作。

在启动应用的同一个 PowerShell 窗口中设置数据库连接信息：

```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/rating_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:DB_USERNAME = "请填写数据库用户名"
$env:DB_PASSWORD = "请填写数据库密码"
```

### 2.4 构建并启动

确认当前目录包含 `pom.xml` 和 `mvnw.cmd`，然后使用 Java 17 构建项目：

```powershell
java -version
```

```powershell
.\mvnw.cmd package
```

构建成功后启动可执行 JAR：

```powershell
java -jar target/rating-system-0.0.1-SNAPSHOT.jar
```

启动成功后，在本机浏览器打开：

```text
http://127.0.0.1:8080
```

## 3. 使用说明

1. 点击页面右上角齿轮进入“系统设置”，填写教师或学校自己的 DeepSeek API Key、API Base URL 和模型名称，保存后可按需测试连接。测试连接可能产生少量 API 费用。
2. 在首页创建新试卷：可以手动录入，也可以上传文字型 DOCX 试卷，由 AI 生成可编辑草稿。
3. 核对题目、标准答案、满分、评分细则和评分点，修改无误后确认整份试卷的评分标准。
4. 将全班学生答卷整理为 ZIP 后上传。每名学生一个文件夹，文件夹名和 DOCX 文件名均使用 `学号_姓名`。

```text
班级答卷.zip
├── 990000001_测试甲/
│   └── 990000001_测试甲.docx
└── 990000002_测试乙/
    └── 990000002_测试乙.docx
```

5. 查看解析结果，修正身份、题目映射或答案文本存在异常的答卷；全部必要问题处理完成后，点击“确认并开始评分”。
6. 在“成绩与审核”中逐题查看学生答案、标准答案、建议分和评分理由，接受建议分或填写实际分数并确认。
7. 所有题目均完成教师审核后，系统才会生成该学生的最终成绩。

**建议分不是最终成绩。最终成绩只汇总教师已经确认的实际分数。**

网页保存的 AI 配置会立即用于新发起的 AI 请求，并优先于 `AI_API_KEY`、`AI_BASE_URL`、`AI_MODEL` 环境变量；已有评分结果不会因此改变。

## 4. 注意事项

- AI 生成的试卷草稿和建议分可能存在错误，必须由教师核对和审核。
- 编程题只保存和展示文字或代码答案，不会自动编译、运行或验证，必须由教师人工评分。
- 删除试卷会永久删除该试卷专属的答卷、导入记录、评分任务、建议评分、人工审核和成绩数据，且无法撤销。
- 当前版本没有身份认证和权限控制，默认仅监听 `127.0.0.1`，只适合受控的本机环境，不得直接开放到公网。
- 不得将 API Key、数据库密码、学生答卷、学生信息、成绩或数据库备份提交到 GitHub。
- 当前仅支持文字型 DOCX 试卷，以及包含 DOCX 答卷的 ZIP 批量导入；不支持 TXT、PDF、OCR、扫描图片或直接选择文件夹上传。
- 系统不提供学生端、在线考试、防作弊、排名、成绩导出或成绩发布功能。
- 正式交付和使用时必须配置教师或学校自己的 DeepSeek API Key，不能依赖开发者个人密钥或额度。
