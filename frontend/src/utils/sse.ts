/**
 * SSE 事件流解析工具。
 * 使用 fetch + ReadableStream 手动解析 Server-Sent Events，
 * 支持 POST 请求（原生 EventSource 仅支持 GET，无法携带请求体）。
 *
 * 兼容两种服务端推送格式：
 * 1. 标准 SSE：`event: <type>` + `data: <payload>` 分行推送；
 * 2. 信封式 JSON：`data: {"type":"<type>","data":"<payload>"}`
 *    （本项目后端 AgentEvent 经 Spring MVC 序列化后的实际格式，无 event: 字段）。
 *    未使用 event: 字段时，若 data 是 {type, data} JSON 信封，自动解包为 (type, data)，
 *    避免把原始 JSON 当作文本展示给用户。
 */
/**
 * 可选配置。
 *
 * 用对象而不是"第 4 个参数是 signal"：访客入口需要在请求头里带匿名标识，
 * 而它不需要中断控制器。写成固定位置参数会让调用方传一堆 `undefined` 占位。
 */
export interface SseOptions {
  /** 中断控制器，用于用户主动停止生成 */
  signal?: AbortSignal
  /** 额外请求头，如用户侧客服窗的 `X-Visitor-Id` */
  headers?: Record<string, string>
}

export async function fetchSSE(
  url: string,
  body: unknown,
  onEvent: (type: string, data: string) => void,
  options: SseOptions = {}
): Promise<void> {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...options.headers },
    body: JSON.stringify(body),
    signal: options.signal
  })

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }

  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('响应不支持流式读取')
  }

  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    // SSE 事件以空行分隔
    let idx
    while ((idx = buffer.indexOf('\n\n')) >= 0) {
      const chunk = buffer.slice(0, idx)
      buffer = buffer.slice(idx + 2)
      parseSseChunk(chunk, onEvent)
    }
  }
  // 处理剩余
  if (buffer.trim()) {
    parseSseChunk(buffer, onEvent)
  }
}

function parseSseChunk(chunk: string, onEvent: (type: string, data: string) => void) {
  let eventType = 'message'
  const dataLines: string[] = []
  for (const line of chunk.split('\n')) {
    if (line.startsWith('event:')) {
      eventType = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }
  if (dataLines.length === 0) return

  const raw = dataLines.join('\n')

  // 无 event: 字段时，尝试将 data 作为 {type, data} JSON 信封解包
  if (eventType === 'message') {
    try {
      const parsed = JSON.parse(raw)
      if (
        parsed &&
        typeof parsed === 'object' &&
        typeof parsed.type === 'string' &&
        Object.prototype.hasOwnProperty.call(parsed, 'data')
      ) {
        const payload = parsed.data
        onEvent(parsed.type, typeof payload === 'string' ? payload : JSON.stringify(payload))
        return
      }
    } catch {
      // 非 JSON 数据，按默认事件处理
    }
  }

  onEvent(eventType, raw)
}
