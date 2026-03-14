# BotBell Jenkins Shared Library

Send pipeline notifications and approval requests to your phone via [BotBell](https://botbell.app).

**Zero dependencies.** Uses only built-in Java HTTP, works on any Jenkins agent.

## Setup

### 1. Add the Shared Library

Go to **Manage Jenkins → System → Global Pipeline Libraries** and add:

| Field | Value |
|-------|-------|
| Name | `botbell` |
| Default Version | `main` |
| Source Code Management | Git |
| Project Repository | `https://github.com/qq418716640/botbell-jenkins.git` |

### 2. Configure Bot Token

Add your BotBell Bot Token as a Jenkins credential:

1. Go to **Manage Jenkins → Credentials**
2. Add a **Secret text** credential with ID `botbell-token`
3. Paste your Bot Token (`bt_...`)

## Usage

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
                        message: "Deploy #${BUILD_NUMBER} to production?\nBranch: ${BRANCH_NAME}",
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
            botbell.notify(
                message: "#${BUILD_NUMBER} deployed to production",
                title: "✅ Deploy Success",
                url: "${BUILD_URL}",
            )
        }
        failure {
            botbell.notify(
                message: "#${BUILD_NUMBER} failed on ${BRANCH_NAME}\n${BUILD_URL}",
                title: "❌ Build Failed",
            )
        }
    }
}
```

## API

### `botbell.notify(config)`

Send a push notification (fire and forget).

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `message` | String | Yes | Message body |
| `title` | String | No | Message title |
| `url` | String | No | Attached URL |
| `imageUrl` | String | No | Image URL |
| `format` | String | No | `"text"` or `"markdown"` |
| `token` | String | No | Bot token (default: `env.BOTBELL_TOKEN`) |

### `botbell.approve(config)`

Send an approval request and block until user responds.

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `message` | String | Yes | — | Message body |
| `title` | String | No | `"🔔 Approval Required"` | Message title |
| `actions` | List | No | Approve / Reject | Action buttons |
| `timeout` | int | No | `1800` (30 min) | Max wait seconds |
| `pollInterval` | int | No | `5` | Seconds between polls |
| `token` | String | No | `env.BOTBELL_TOKEN` | Bot token |

**Returns** a Map: `[approved: true/false, action: "approve", message: ""]`

On rejection or timeout, the pipeline is automatically aborted with `error`.

### Custom Actions

```groovy
botbell.approve(
    message: "Release v2.1.0?",
    actions: [
        [key: "approve", label: "Ship it"],
        [key: "hold", label: "Hold off"],
        [key: "reason", label: "Not now because...", type: "input", placeholder: "Reason"],
    ],
    timeout: 3600,
)
```

### Markdown Messages

```groovy
botbell.notify(
    title: "📊 Build Report",
    message: """**Build #${BUILD_NUMBER}**
- Branch: `${BRANCH_NAME}`
- Duration: ${currentBuild.durationString}
- [View logs](${BUILD_URL}console)""",
    format: "markdown",
)
```

## How It Works

```
Jenkins pipeline reaches approval stage
    ↓
Push notification sent to your phone
    ↓
You see: [Approve] [Reject] buttons
    ↓
Tap Approve → Pipeline continues
Tap Reject  → Pipeline aborts
    ↓
No browser needed, no VPN needed
```

## License

MIT
