const DEFAULT_API_BASE_URL = 'http://localhost:8080'

const NETWORK_ERROR_MESSAGE = '浏览器未能访问后端，可能是后端未启动或 CORS 未放行。'
const LOCAL_MOCK_MESSAGE = '本地 mock，仅用于前端演示。'
const BACKEND_DEV_FALLBACK_MESSAGE = '后端已连通，但当前未配置真实模型，展示 dev fallback 结果。'

const formatLabels = {
  short_drama: '短剧',
  screenplay: '影视剧本',
  scene_outline: '分场大纲'
}

export async function convertNovel(payload) {
  try {
    const response = await fetch(`${getApiBaseUrl()}/api/convert`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
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

export class ApiError extends Error {
  constructor(message, code, status) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
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
    schemaVersion: '1.0',
    warnings: [
      LOCAL_MOCK_MESSAGE,
      error?.message ? `网络错误：${error.message}` : NETWORK_ERROR_MESSAGE
    ],
    qualityReport: {
      chapterCount,
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

function estimateChapterCount(sourceText) {
  const matches = sourceText.match(/(^|\n)\s*(第[一二三四五六七八九十百千万\d]+[章节回]|chapter\s+\d+)/gi)
  return matches?.length || 0
}

function escapeYamlValue(value) {
  return String(value).replace(/\\/g, '\\\\').replace(/"/g, '\\"')
}
