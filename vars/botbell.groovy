import groovy.json.JsonSlurper
import groovy.json.JsonOutput

/**
 * BotBell Jenkins Shared Library
 *
 * Send push notifications and approval requests to your phone via BotBell.
 *
 * Usage:
 *   botbell.notify(message: "Build succeeded", title: "✅ Success")
 *   def reply = botbell.approve(message: "Deploy to prod?")
 */

private static final String DEFAULT_API_BASE = "https://api.botbell.app/v1"
private static final int DEFAULT_TIMEOUT = 1800       // 30 minutes
private static final int DEFAULT_POLL_INTERVAL = 5    // seconds

// ── Public API ──────────────────────────────────────────────────────

/**
 * Send a push notification.
 *
 * @param config Map with keys:
 *   message  (required) - Message body
 *   title    (optional) - Message title
 *   url      (optional) - Attached URL
 *   imageUrl (optional) - Image URL
 *   summary  (optional) - Message summary
 *   format   (optional) - "text" or "markdown"
 *   token    (optional) - Bot token (default: env.BOTBELL_TOKEN)
 *   apiBase  (optional) - API base URL
 * @return Map with messageId and delivered
 */
def notify(Map config) {
    validateRequired(config, 'message')
    def token = resolveToken(config)
    def apiBase = config.apiBase ?: DEFAULT_API_BASE

    def body = buildMessageBody(config)
    body.reply_mode = 'none'

    def resp = httpPost("${apiBase}/push/${token}", body)
    return [
        messageId: resp.data.message_id,
        delivered: resp.data.delivered,
    ]
}

/**
 * Send an approval request and wait for user response.
 *
 * @param config Map with keys:
 *   message      (required) - Message body
 *   title        (optional) - Message title (default: "🔔 Approval Required")
 *   actions      (optional) - List of action maps (default: Approve/Reject)
 *   timeout      (optional) - Max wait seconds (default: 1800 = 30 min)
 *   pollInterval (optional) - Poll interval seconds (default: 5)
 *   token        (optional) - Bot token (default: env.BOTBELL_TOKEN)
 *   apiBase      (optional) - API base URL
 * @return Map with keys: approved (boolean), action (string), message (string)
 * @throws hudson.AbortException on timeout or rejection
 */
def approve(Map config) {
    validateRequired(config, 'message')
    def token = resolveToken(config)
    def apiBase = config.apiBase ?: DEFAULT_API_BASE
    def timeout = config.containsKey('timeout') ? config.timeout : DEFAULT_TIMEOUT
    def pollInterval = config.containsKey('pollInterval') ? config.pollInterval : DEFAULT_POLL_INTERVAL

    def body = buildMessageBody(config)
    if (!body.title) body.title = '🔔 Approval Required'
    body.reply_mode = 'actions_only'
    body.actions = config.actions ?: [
        [key: 'approve', label: 'Approve'],
        [key: 'reject', label: 'Reject'],
    ]

    // Send the approval request
    def sendResp = httpPost("${apiBase}/push/${token}", body)
    def messageId = sendResp.data.message_id

    echo "BotBell: Approval request sent (${messageId}), waiting up to ${timeout}s..."

    // Poll for reply
    def deadline = System.currentTimeMillis() + timeout * 1000L

    while (System.currentTimeMillis() < deadline) {
        sleep(pollInterval)

        def pollResp = httpGet("${apiBase}/messages/poll", token)
        def messages = pollResp.data?.messages ?: []

        for (msg in messages) {
            if (msg.reply_to == messageId) {
                def result = [
                    approved: msg.action == 'approve',
                    action: msg.action ?: '',
                    message: msg.content ?: '',
                ]
                if (result.approved) {
                    echo "BotBell: Approved!"
                } else {
                    def reason = result.message ?: result.action
                    error "BotBell: Rejected — ${reason}"
                }
                return result
            }
        }
    }

    error "BotBell: Approval timed out after ${timeout}s"
}

// ── Internal ────────────────────────────────────────────────────────

private String resolveToken(Map config) {
    def token = config.token ?: env.BOTBELL_TOKEN
    if (!token) {
        error "BotBell: No token provided. Set BOTBELL_TOKEN env var or pass token parameter."
    }
    return token
}

private void validateRequired(Map config, String key) {
    if (!config[key]) {
        error "BotBell: '${key}' is required"
    }
}

private Map buildMessageBody(Map config) {
    def body = [message: config.message]
    if (config.title)    body.title = config.title
    if (config.url)      body.url = config.url
    if (config.imageUrl) body.image_url = config.imageUrl
    if (config.summary)  body.summary = config.summary
    if (config.format)   body.format = config.format
    if (config.actions)  body.actions = config.actions
    if (config.actionsDescription) body.actions_description = config.actionsDescription
    return body
}

@NonCPS
private Map httpPost(String url, Map body) {
    def conn = null
    try {
        conn = new URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = 'POST'
        conn.setRequestProperty('Content-Type', 'application/json')
        conn.setRequestProperty('Accept', 'application/json')
        conn.setRequestProperty('User-Agent', 'botbell-jenkins/0.1.0')
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.doOutput = true

        conn.outputStream.withWriter('UTF-8') { it.write(JsonOutput.toJson(body)) }

        return parseResponse(conn)
    } catch (IOException e) {
        throw new RuntimeException("BotBell: Connection failed — ${e.message}", e)
    } finally {
        conn?.disconnect()
    }
}

@NonCPS
private Map httpGet(String url, String token) {
    def conn = null
    try {
        conn = new URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = 'GET'
        conn.setRequestProperty('X-Bot-Token', token)
        conn.setRequestProperty('Accept', 'application/json')
        conn.setRequestProperty('User-Agent', 'botbell-jenkins/0.1.0')
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        return parseResponse(conn)
    } catch (IOException e) {
        throw new RuntimeException("BotBell: Connection failed — ${e.message}", e)
    } finally {
        conn?.disconnect()
    }
}

@NonCPS
private Map parseResponse(HttpURLConnection conn) {
    def code = conn.responseCode
    def stream = (code >= 200 && code < 300) ? conn.inputStream : conn.errorStream
    def text = ''
    try {
        text = stream?.getText('UTF-8') ?: '{}'
    } finally {
        stream?.close()
    }

    def json = new JsonSlurper().parseText(text) as Map
    if (code >= 400) {
        throw new RuntimeException(
            "BotBell API error ${json.code ?: code}: ${json.message ?: text}"
        )
    }
    return json
}
