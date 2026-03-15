[English](README.md) | [中文](README.zh-CN.md)

# BotBell Jenkins Shared Library

通过 [BotBell](https://botbell.app) 将流水线通知和审批请求发送到你的手机。

**零依赖。** 仅使用内置 Java HTTP，适用于任何 Jenkins Agent。

## 配置

### 1. 添加 Shared Library

进入 **Manage Jenkins → System → Global Pipeline Libraries**，添加：

| 字段 | 值 |
|------|------|
| Name | `botbell` |
| Default Version | `main` |
| Source Code Management | Git |
| Project Repository | `https://github.com/qq418716640/botbell-jenkins.git` |

### 2. 配置 Bot Token

将 BotBell Bot Token 添加为 Jenkins 凭据：

1. 进入 **Manage Jenkins → Credentials**
2. 添加 **Secret text** 类型凭据，ID 为 `botbell-token`
3. 粘贴你的 Bot Token（`bt_...`）

## 使用

```groovy
@Library('botbell') _

pipeline {
    agent any
    environment {
        BOTBELL_TOKEN = credentials('botbell-token')
    }
    stages {
        stage('Build') {
            steps {
                sh 'make build'
            }
        }
        stage('Deploy Approval') {
            steps {
                script {
                    botbell.approve(
                        message: "将 #${BUILD_NUMBER} 部署到生产环境？\n分支: ${BRANCH_NAME}",
                    )
                }
            }
        }
        stage('Deploy') {
            steps {
                sh 'make deploy'
            }
        }
    }
    post {
        success {
            script {
                botbell.notify(
                    message: "#${BUILD_NUMBER} 已部署到生产环境",
                    title: "✅ 部署成功",
                    url: "${BUILD_URL}",
                )
            }
        }
        failure {
            script {
                botbell.notify(
                    message: "#${BUILD_NUMBER} 在 ${BRANCH_NAME} 上失败\n${BUILD_URL}",
                    title: "❌ 构建失败",
                )
            }
        }
    }
}
```

## API

### `botbell.notify(config)`

发送推送通知（发完即走）。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `message` | String | 是 | 消息内容 |
| `title` | String | 否 | 消息标题 |
| `url` | String | 否 | 附加 URL |
| `imageUrl` | String | 否 | 图片 URL |
| `format` | String | 否 | `"text"` 或 `"markdown"` |
| `token` | String | 否 | Bot Token（默认：`env.BOTBELL_TOKEN`） |

### `botbell.approve(config)`

发送审批请求并阻塞等待用户响应。

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `message` | String | 是 | — | 消息内容 |
| `title` | String | 否 | `"🔔 Approval Required"` | 消息标题 |
| `actions` | List | 否 | Approve / Reject | Action 按钮 |
| `timeout` | int | 否 | `1800`（30 分钟） | 最大等待秒数 |
| `pollInterval` | int | 否 | `5` | 轮询间隔秒数 |
| `token` | String | 否 | `env.BOTBELL_TOKEN` | Bot Token |

**返回** Map：`[approved: true/false, action: "approve", message: ""]`

拒绝或超时时，流水线自动通过 `error` 中止。

### 自定义 Actions

```groovy
botbell.approve(
    message: "发布 v2.1.0？",
    actions: [
        [key: "approve", label: "发布"],
        [key: "hold", label: "暂缓"],
        [key: "reason", label: "暂不发布，因为...", type: "input", placeholder: "原因"],
    ],
    timeout: 3600,
)
```

### Markdown 消息

```groovy
botbell.notify(
    title: "📊 构建报告",
    message: """**构建 #${BUILD_NUMBER}**
- 分支: `${BRANCH_NAME}`
- 耗时: ${currentBuild.durationString}
- [查看日志](${BUILD_URL}console)""",
    format: "markdown",
)
```

## 工作原理

```
Jenkins 流水线到达审批阶段
    ↓
推送通知发送到你的手机
    ↓
你看到：[批准] [拒绝] 按钮
    ↓
点击批准 → 流水线继续
点击拒绝 → 流水线中止
    ↓
无需打开浏览器，无需 VPN
```

## 许可证

MIT
