const DEFAULT_API_BASE_URL = 'http://localhost:8080'
const STREAM_TIMEOUT_MS = 420000

const NETWORK_ERROR_MESSAGE = '浏览器未拿到后端响应：请检查后端是否启动、端口是否为 8080，或 CORS 预检是否已放行。'
const LOCAL_MOCK_MESSAGE = '本地 mock，仅用于前端演示。'
const BACKEND_DEV_FALLBACK_MESSAGE = '后端已连通，但当前未配置真实模型，展示 dev fallback 结果。'

const formatLabels = {
  short_drama: '短剧',
  screenplay: '影视剧本',
  scene_outline: '分场大纲'
}

export async function refineInput(payload) {
  const fallback = () => createInputAssistantFallback(payload)

  try {
    const response = await fetch(`${getApiBaseUrl()}/api/assistant/refine-input`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      credentials: 'include',
      body: JSON.stringify(payload)
    })
    const data = await parseJsonResponse(response)

    if (!response.ok) {
      return {
        ...fallback(),
        usedFallback: true,
        fallbackReason: data?.message || '输入助手接口暂不可用，已使用本地整理。'
      }
    }

    return normalizeInputAssistantResponse(data)
  } catch {
    return {
      ...fallback(),
      usedFallback: true,
      fallbackReason: '输入助手接口连接失败，已使用本地整理。'
    }
  }
}

export async function chatWithInputAssistant(payload) {
  const fallback = () => createInputAssistantChatFallback(payload)

  try {
    const response = await fetch(`${getApiBaseUrl()}/api/assistant/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      credentials: 'include',
      body: JSON.stringify(payload)
    })
    const data = await parseJsonResponse(response)

    if (!response.ok) {
      return {
        ...fallback(),
        usedFallback: true,
        fallbackReason: data?.message || '输入助手聊天接口暂不可用，已使用本地整理。'
      }
    }

    const result = {
      ...normalizeInputAssistantResponse(data),
      assistantMessage: String(data?.assistantMessage || '').trim() || '我已整理当前输入。',
      usedFallback: false,
      fallbackReason: ''
    }
    if (payload?.capability === 'style') {
      result.enhancedInput = normalizeStyleApplyText(result.enhancedInput, result.assistantMessage, result.styleHints, payload)
    }
    return result
  } catch {
    return {
      ...fallback(),
      usedFallback: true,
      fallbackReason: '输入助手聊天接口连接失败，已使用本地整理。'
    }
  }
}

export async function convertNovel(payload, onEvent) {
  if (typeof ReadableStream !== 'undefined') {
    return convertNovelStream(payload, onEvent)
  }

  try {
    const response = await fetch(`${getApiBaseUrl()}/api/convert`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      credentials: 'include',
      body: JSON.stringify(payload)
    })

    const data = await parseJsonResponse(response)

    if (!response.ok) {
      throw new ApiError(data?.message || '转换失败，请稍后重试。', data?.code, response.status)
    }

    return normalizeConvertResponse(data, {
      source: 'backend',
      networkMessage: '',
      localMockMessage: ''
    })
  } catch (error) {
    if (error instanceof ApiError) {
      throw error
    }

    return normalizeConvertResponse(createMockResponse(payload, error), {
      source: 'local_mock',
      networkMessage: NETWORK_ERROR_MESSAGE,
      localMockMessage: LOCAL_MOCK_MESSAGE
    })
  }
}

export async function convertNovelStream(payload, onEvent) {
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), STREAM_TIMEOUT_MS)

  try {
    const response = await fetch(`${getApiBaseUrl()}/api/convert/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream'
      },
      credentials: 'include',
      body: JSON.stringify(payload),
      signal: controller.signal
    })

    if (!response.ok || !response.body) {
      const data = await parseJsonResponse(response)
      throw new ApiError(data?.message || '流式转换启动失败。', data?.code, response.status)
    }

    return await readEventStream(response.body, payload, onEvent)
  } catch (error) {
    if (error instanceof ApiError) {
      throw error
    }
    if (error.name === 'AbortError') {
      throw new ApiError('模型转换超时：请先使用快速模式、缩短章节正文，或切换更快的模型后重试。', 'STREAM_TIMEOUT', 408)
    }
    return normalizeConvertResponse(createMockResponse(payload, error), {
      source: 'local_mock',
      networkMessage: NETWORK_ERROR_MESSAGE,
      localMockMessage: LOCAL_MOCK_MESSAGE
    })
  } finally {
    window.clearTimeout(timeout)
  }
}

async function readEventStream(body, payload, onEvent) {
  const reader = body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let finalResult = null

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split('\n\n')
    buffer = parts.pop() || ''

    for (const part of parts) {
      const event = parseSseEvent(part)
      if (!event) {
        continue
      }
      onEvent?.(event)
      if (event.payload?.type === 'result' && event.payload.data) {
        finalResult = normalizeConvertResponse(event.payload.data, {
          source: 'backend',
          networkMessage: '',
          localMockMessage: ''
        })
      }
    }
  }

  if (!finalResult) {
    throw new ApiError('流式转换结束但没有返回结果。', 'STREAM_RESULT_MISSING', 502)
  }
  return finalResult
}

function parseSseEvent(raw) {
  const lines = raw.split(/\r?\n/)
  const eventName = lines.find((line) => line.startsWith('event:'))?.slice(6).trim() || 'message'
  const dataLines = lines
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())

  if (!dataLines.length) {
    return null
  }

  const text = dataLines.join('\n')
  try {
    return {
      name: eventName,
      payload: JSON.parse(text)
    }
  } catch {
    return {
      name: eventName,
      payload: { type: eventName, message: text }
    }
  }
}

export class ApiError extends Error {
  constructor(message, code, status) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

export async function getCurrentUser() {
  const response = await fetch(`${getApiBaseUrl()}/api/auth/me`, {
    method: 'GET',
    credentials: 'include'
  })
  const data = await parseJsonResponse(response)

  if (response.status === 401) {
    return null
  }
  if (!response.ok) {
    throw new ApiError(data?.message || 'Failed to load current user.', data?.code, response.status)
  }
  return data?.user || null
}

export async function login(payload) {
  return submitAuth('/api/auth/login', payload)
}

export async function register(payload) {
  return submitAuth('/api/auth/register', payload)
}

export async function logout() {
  const response = await fetch(`${getApiBaseUrl()}/api/auth/logout`, {
    method: 'POST',
    credentials: 'include'
  })
  if (!response.ok) {
    const data = await parseJsonResponse(response)
    throw new ApiError(data?.message || 'Logout failed.', data?.code, response.status)
  }
}

async function submitAuth(path, payload) {
  const response = await fetch(`${getApiBaseUrl()}${path}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    body: JSON.stringify(payload)
  })
  const data = await parseJsonResponse(response)

  if (!response.ok) {
    throw new ApiError(data?.message || 'Authentication failed.', data?.code, response.status)
  }
  return data?.user || null
}

function getApiBaseUrl() {
  return (import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL).replace(/\/+$/, '')
}

async function parseJsonResponse(response) {
  const text = await response.text()
  if (!text) {
    return null
  }

  try {
    return JSON.parse(text)
  } catch {
    if (!response.ok) {
      return { message: text }
    }
    throw new ApiError('后端返回了无法解析的响应。', 'INVALID_JSON', response.status)
  }
}

function normalizeConvertResponse(data, meta) {
  const trace = normalizeAgentTrace(data?.agentTrace)
  const qualityReport = normalizeQualityReport(data?.qualityReport)
  const warnings = Array.isArray(data?.warnings) ? data.warnings : []
  const backendFallback = meta.source === 'backend' && hasBackendFallbackSignal(warnings, trace, data?.agentTrace)

  return {
    yaml: data?.yaml || '',
    schemaVersion: data?.schemaVersion || '1.0',
    warnings,
    qualityReport,
    agentTrace: trace,
    source: meta.source,
    networkMessage: meta.networkMessage,
    localMockMessage: meta.localMockMessage,
    backendFallbackMessage: backendFallback ? BACKEND_DEV_FALLBACK_MESSAGE : '',
    usedMock: meta.source === 'local_mock',
    usedBackendFallback: backendFallback
  }
}

function normalizeAgentTrace(agentTrace) {
  if (Array.isArray(agentTrace)) {
    return agentTrace
  }

  if (Array.isArray(agentTrace?.steps)) {
    return agentTrace.steps
  }

  return []
}

function normalizeQualityReport(report) {
  if (!report || typeof report !== 'object') {
    return {}
  }

  if (Array.isArray(report.checks)) {
    return {
      confidence: report.confidence,
      checks: report.checks
    }
  }

  return report
}

function hasBackendFallbackSignal(warnings, trace, rawAgentTrace) {
  const values = [
    ...warnings,
    ...trace,
    rawAgentTrace?.mode,
    rawAgentTrace?.status,
    rawAgentTrace?.message
  ]
    .filter(Boolean)
    .map((value) => String(value).toLowerCase())

  return values.some((value) =>
    [
      'dev fallback',
      '未调用真实大模型',
      '未配置真实模型',
      'no spring ai chatmodel',
      'chatmodel unavailable',
      'fallback tool',
      'mock agent'
    ].some((signal) => value.includes(signal.toLowerCase()))
  )
}

function createMockResponse(payload, error) {
  const chapterCount = estimateChapterCount(payload.sourceText)
  const formatLabel = formatLabels[payload.targetFormat] || payload.targetFormat
  const title = payload.title || '未命名作品'
  const styleHint = payload.styleHint?.trim() || '节奏清晰、人物动机明确'

  return {
    yaml: [
      'schema_version: "1.0"',
      `title: "${escapeYamlValue(title)}"`,
      `target_format: "${payload.targetFormat}"`,
      `style_hint: "${escapeYamlValue(styleHint)}"`,
      'logline: "一段来自旧事件的线索，推动主角追问真相并完成改编。"',
      'characters:',
      '  - name: "主角"',
      '    role: "推动调查与情感选择的核心人物"',
      '  - name: "关键证人"',
      '    role: "提供旧事件线索和反转信息"',
      'scenes:',
      '  - id: "S01"',
      '    title: "异常线索出现"',
      '    type: "setup"',
      '    summary: "主角收到异常信息，确认过去事件仍未结束。"',
      '    beats:',
      '      - type: "discovery"',
      '        text: "旧物或来信暴露隐藏矛盾。"',
      '  - id: "S02"',
      '    title: "追问与对质"',
      '    type: "confrontation"',
      '    summary: "主角根据线索找到知情人，冲突逐步升级。"',
      '    beats:',
      '      - type: "turning_point"',
      '        text: "关键证词改变主角对真相的判断。"',
      '  - id: "S03"',
      '    title: "真相校准"',
      '    type: "resolution"',
      '    summary: "证据闭合，主角选择公开真相并承担后果。"',
      '    beats:',
      '      - type: "resolution"',
      '        text: "核心误差被保留下来，成为最终证据。"'
    ].join('\n'),
    yaml: createPlayableMockYaml(payload, chapterCount, title, styleHint),
    schemaVersion: '1.0',
    warnings: [
      LOCAL_MOCK_MESSAGE,
      error?.message ? `网络错误：${error.message}` : NETWORK_ERROR_MESSAGE
    ],
    qualityReport: {
      chapterCount,
      conversionMode: payload.conversionMode || 'fast',
      characterCount: 2,
      sceneCount: 3,
      reactSteps: 4,
      repaired: false
    },
    agentTrace: [
      '本地 mock 已接管转换流程',
      '已完成章节解析',
      `已识别目标格式：${formatLabel}`,
      '已抽取角色和关键事件',
      '已生成场景计划',
      'YAML 校验通过'
    ]
  }
}

function createPlayableMockYaml(payload, chapterCount, title, styleHint) {
  const safeChapterCount = Math.max(chapterCount, 3)
  const english = String(payload.language || '').toLowerCase().startsWith('en')
  const lead = english ? 'Lead' : '主角'
  const witness = english ? 'Witness' : '证人'

  return [
    'schema_version: "1.0"',
    'work:',
    `  title: "${escapeYamlValue(title)}"`,
    '  original_author: ""',
    `  language: "${english ? 'en-US' : 'zh-CN'}"`,
    '  source_chapters:',
    `    count: ${safeChapterCount}`,
    `    range: "Chapter 1-${safeChapterCount}"`,
    'adaptation:',
    `  target_format: "${payload.targetFormat}"`,
    '  target_duration: "10-15min"',
    `  genre: "${english ? 'demo' : '演示'}"`,
    `  tone: "${escapeYamlValue(styleHint)}"`,
    `  logline: "${english ? 'A local mock scene turns source clues into playable action and dialogue.' : '本地 mock 将原文线索转换为可表演的动作和对白。'}"`,
    '  principles:',
    `    - "${english ? 'Prefer performable scene beats over abstract outline notes.' : '优先生成可表演场景节拍，而不是抽象大纲。'}"`,
    'characters:',
    '  - id: "char_001"',
    `    name: "${lead}"`,
    '    role: "protagonist"',
    `    identity: "${english ? 'writer adapting an old event' : '正在改编旧事件的作者'}"`,
    `    personality: "${english ? 'restrained, observant' : '克制、敏锐'}"`,
    `    goal: "${english ? 'Turn hidden clues into a playable first draft.' : '把隐藏线索改写成可表演初稿。'}"`,
    `    arc: "${english ? 'From reading evidence to speaking the truth aloud.' : '从阅读证据到说出真相。'}"`,
    '    relationships:',
    `      - target: "${witness}"`,
    `        relation: "${english ? 'questions' : '追问'}"`,
    '  - id: "char_002"',
    `    name: "${witness}"`,
    '    role: "supporting"',
    `    identity: "${english ? 'keeper of the old event' : '旧事件知情人'}"`,
    `    personality: "${english ? 'guarded' : '戒备'}"`,
    `    goal: "${english ? 'Avoid saying the detail that changes the case.' : '回避改变案件判断的关键细节。'}"`,
    `    arc: "${english ? 'From silence to reluctant testimony.' : '从沉默到勉强作证。'}"`,
    '    relationships:',
    `      - target: "${lead}"`,
    `        relation: "${english ? 'answers under pressure' : '在压力下回答'}"`,
    'plot_outline:',
    '  - source_chapter: "Chapter 1-3"',
    '    key_events:',
    `      - "${english ? 'The lead receives a clue from the old event.' : '主角收到旧事件线索。'}"`,
    `      - "${english ? 'The witness reveals the missing detail under pressure.' : '证人在压力下说出缺失细节。'}"`,
    `    adaptation_choice: "${english ? 'Convert narration into visible action and speakable dialogue.' : '将叙述转换为可见动作和可说出口的对白。'}"`,
    'scenes:',
    '  - scene_id: "S001"',
    '    scene_type: "INT"',
    `    location: "${english ? 'Archive room' : '档案室'}"`,
    '    time_of_day: "NIGHT"',
    '    characters:',
    `      - "${lead}"`,
    `      - "${witness}"`,
    `    summary: "${english ? 'The lead confronts the witness with a clue from the old event.' : '主角用旧事件线索逼问证人。'}"`,
    `    dramatic_purpose: "${english ? 'Replace abstract discovery with playable pressure between two characters.' : '用两人之间的压力替代抽象发现。'}"`,
    '    beats:',
    '      - type: "action"',
    `        content: "${english ? 'The lead places the letter under the desk lamp and waits until the witness looks at it.' : '主角把信放到台灯下，等证人看清信纸。'}"`,
    '      - type: "dialogue"',
    `        speaker: "${lead}"`,
    `        content: "${english ? 'Do not summarize it for me. Say the line you refused to say that night.' : '别替我概括。把那晚你不肯说的话说出来。'}"`,
    '      - type: "parenthetical"',
    `        content: "${english ? 'The witness folds the edge of the letter but does not tear it.' : '证人折起信角，却没有把它撕掉。'}"`,
    '      - type: "dialogue"',
    `        speaker: "${witness}"`,
    `        content: "${english ? 'The clock was not wrong. We made everyone believe it was.' : '钟没有错，是我们让所有人相信它错了。'}"`,
    'notes:',
    `  adaptation_summary: "${english ? 'Local mock output shaped as a playable screenplay YAML draft.' : '本地 mock 输出已整理为可表演剧本 YAML 初稿。'}"`,
    '  omitted_elements: []',
    '  risks:',
    `    - "${english ? 'Mock output is only a fallback when the backend request fails.' : 'mock 输出只用于后端请求失败时降级展示。'}"`,
    '  next_steps:',
    `    - "${english ? 'Run the real backend conversion for full model-generated scenes.' : '运行真实后端转换以获得完整模型生成场景。'}"`
  ].join('\n')
}

function estimateChapterCount(sourceText) {
  const matches = sourceText.match(
    /(^|\n)\s*(#{1,6}\s*)?((第\s*[一二三四五六七八九十百千万零〇两\d]+\s*[章节回卷])|(chapter\s+\d+))/gi
  )
  return matches?.length || 0
}

function escapeYamlValue(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/"/g, '\\"')
}

function normalizeInputAssistantResponse(data) {
  const enhancedInput = String(data?.enhancedInput || '').trim()
  const styleHints = Array.isArray(data?.styleHints) ? data.styleHints.filter(Boolean).map(String) : []
  const suggestions = Array.isArray(data?.suggestions) ? data.suggestions.filter(Boolean).map(String) : []
  const formatHints = data?.formatHints && typeof data.formatHints === 'object' ? data.formatHints : {}

  return {
    enhancedInput,
    styleHints,
    formatHints,
    suggestions,
    usedFallback: false,
    fallbackReason: ''
  }
}

function createInputAssistantFallback(payload) {
  const rawInput = String(payload?.rawInput || '').trim()
  const styles = Array.isArray(payload?.selectedStyles)
    ? [...new Set(payload.selectedStyles.map((style) => String(style).trim()).filter(Boolean))]
    : []

  return {
    enhancedInput: buildChapterizedInput(rawInput),
    styleHints: styles,
    formatHints: {
      contentType: '小说转脚本',
      tone: styles.length ? styles.join('、') : '未指定'
    },
    suggestions: [],
    usedFallback: true,
    fallbackReason: ''
  }
}

function buildChapterizedInput(rawInput) {
  const input = String(rawInput || '').trim()
  if (!input) {
    return ''
  }
  if (countChapterHeadings(input) >= 3) {
    return input
      .replace(/^\s*#{1,6}\s*(第\s*[一二三四五六七八九十百千万零〇两\d]+\s*[章节回卷].*)$/gim, '$1')
      .replace(/\n{3,}/g, '\n\n')
      .trim()
  }

  const parts = splitIntoChapterParts(input)
  while (parts.length < 3) {
    parts.push(input)
  }
  return parts.slice(0, 3).map((part, index) => {
    return `${chineseChapterNumber(index + 1)}\n\n${part.trim() || input}`
  }).join('\n\n')
}

function splitIntoChapterParts(input) {
  const paragraphs = input
    .split(/\n\s*\n/)
    .map((part) => part.trim())
    .filter(Boolean)
  if (paragraphs.length >= 3) {
    return paragraphs.slice(0, 3)
  }

  const sentences = input
    .split(/(?<=[。！？!?])/)
    .map((part) => part.trim())
    .filter(Boolean)
  if (sentences.length < 3) {
    return paragraphs.length ? paragraphs : [input]
  }

  const size = Math.ceil(sentences.length / 3)
  const parts = []
  for (let index = 0; index < sentences.length && parts.length < 3; index += size) {
    parts.push(sentences.slice(index, index + size).join(''))
  }
  return parts
}

function countChapterHeadings(value) {
  return String(value || '').match(
    /(^|\n)\s*(#{1,6}\s*)?((第\s*[一二三四五六七八九十百千万零〇两\d]+\s*[章节回卷])|(chapter\s+\d+))/gi
  )?.length || 0
}

function chineseChapterNumber(index) {
  return ['第一章', '第二章', '第三章'][index - 1] || `第${index}章`
}

function createInputAssistantChatFallback(payload) {
  const rawInput = [
    payload?.homeInput ? `首页已有输入：\n${payload.homeInput}` : '',
    ...(Array.isArray(payload?.messages)
      ? payload.messages.filter((message) => message?.role === 'user').map((message) => message.text)
      : [])
  ].filter(Boolean).join('\n\n')

  if (payload?.capability === 'style') {
    const styleText = createStyleHintFallback(rawInput, payload?.currentStyleHint)
    return {
      enhancedInput: styleText,
      styleHints: splitStyleHints(styleText),
      formatHints: {
        contentType: '小说转脚本',
        tone: styleText
      },
      suggestions: [
        '可以补充节奏偏好，例如紧凑或舒缓',
        '可以补充情绪基调，例如温暖、压抑或轻松'
      ],
      usedFallback: true,
      fallbackReason: '',
      assistantMessage: '我先给出一版可直接放进风格提示的建议，你可以继续补充偏好的节奏或情绪。'
    }
  }

  const refined = createInputAssistantFallback({
    rawInput,
    selectedStyles: payload?.selectedStyles
  })

  return {
    ...refined,
    assistantMessage: payload?.capability === 'style'
      ? '我给出轻量风格建议，并整理了一个可回填的输入草稿。'
        : '我已整理输入格式，并生成一个可回填的清晰草稿。'
  }
}

function createStyleHintFallback(rawInput, currentStyleHint) {
  const source = `${rawInput || ''}\n${currentStyleHint || ''}`
  const hints = []
  if (/悬疑|失踪|调查|真相|案件|秘密/.test(source)) {
    hints.push('悬疑感')
  }
  if (/亲情|成长|和解|温暖|治愈/.test(source)) {
    hints.push('治愈感')
  }
  if (/短剧|反转|爽点|冲突|钩子/.test(source)) {
    hints.push('短剧感')
  }
  if (!hints.length) {
    hints.push('电影感')
  }

  return [
    `${hints.join('、')}，节奏清晰，场景有明确情绪推进。`,
    '对白保持可表演、不过度解释，优先用动作和场面调度呈现人物关系。',
    '风格仅作为软建议，不改变原文核心人物、事件和结局。'
  ].join(' ')
}

function normalizeStyleApplyText(enhancedInput, assistantMessage, styleHints, payload) {
  const candidates = [
    enhancedInput,
    assistantMessage,
    Array.isArray(styleHints) ? styleHints.join('、') : '',
    payload?.currentStyleHint || '',
    ...(Array.isArray(payload?.messages)
      ? payload.messages.filter((message) => message?.role === 'user').map((message) => message.text)
      : [])
  ]
  const styleSentences = candidates
    .flatMap((value) => extractStyleSentences(value))
    .filter(Boolean)

  const unique = [...new Set(styleSentences)]
  let text = unique.join('。').trim()
  if (!text) {
    text = createStyleHintFallback('', payload?.currentStyleHint)
  }
  if (!text.includes('软建议')) {
    text += ' 风格仅作为软建议，不改变原文核心人物、事件和结局。'
  }
  return text
    .replace(/\s+/g, ' ')
    .replace(/首页已有输入[:：].*/g, '')
    .replace(/用户硬约束[:：].*/g, '')
    .slice(0, 220)
    .trim()
}

function extractStyleSentences(value) {
  return String(value || '')
    .replace(/【[^】]+】/g, '')
    .replace(/首页已有输入[:：][\s\S]*/g, '')
    .replace(/用户硬约束[:：][\s\S]*/g, '')
    .replace(/正文[:：][\s\S]*/g, '')
    .split(/[。！？!?\n]+/)
    .map((sentence) => sentence.trim())
    .filter((sentence) => /风格|基调|氛围|节奏|情绪|视听|画面|镜头|场景调度|对白|叙事|悬疑|治愈|电影感|短剧感|轻喜剧|赛博朋克|温柔|温暖|冷峻|压抑|轻松|年代感|抒情|回望|不悬疑|真实|细腻|克制|紧凑|舒缓|烟火气|日常感/.test(sentence))
}

function splitStyleHints(styleText) {
  return String(styleText)
    .split(/[、,，。；;\s]+/)
    .map((style) => style.trim())
    .filter(Boolean)
    .slice(0, 6)
}
