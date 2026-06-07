<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { motion } from 'motion-v'
import { ApiError, convertNovel, getCurrentUser, login, logout, register } from './api'
import AppHeader from './components/AppHeader.vue'
import AuthPanel from './components/AuthPanel.vue'
import ConversionForm from './components/ConversionForm.vue'
import InputAssistant from './components/InputAssistant.vue'
import ModeSelector from './components/ModeSelector.vue'
import ProgressTimeline from './components/ProgressTimeline.vue'
import QualityPanel from './components/QualityPanel.vue'
import StatusBadge from './components/StatusBadge.vue'
import WarningList from './components/WarningList.vue'
import YamlPreview from './components/YamlPreview.vue'

const targetOptions = [
  { value: 'short_drama', label: '短剧' },
  { value: 'screenplay', label: '影视剧本' },
  { value: 'scene_outline', label: '分场大纲' }
]

const conversionModeOptions = [
  { value: 'fast', label: '快速模式' },
  { value: 'react', label: '完整 ReAct' }
]

const form = reactive({
  title: '',
  sourceText: '',
  targetFormat: 'short_drama',
  styleHint: '',
  conversionMode: 'fast'
})

const authForm = reactive({
  email: '',
  password: '',
  displayName: '',
  inviteCode: ''
})

const loading = ref(false)
const authLoading = ref(false)
const authChecking = ref(true)
const errorMessage = ref('')
const authErrorMessage = ref('')
const result = ref(null)
const actionMessage = ref('')
const authActionMessage = ref('')
const locale = ref('zh')
const authMode = ref('login')
const currentUser = ref(null)
const streamSteps = ref([])
const elapsedSeconds = ref(0)
let elapsedTimer = null

const localeOptions = [
  { value: 'zh', label: '中文' },
  { value: 'en', label: 'EN' }
]

const messages = {
  zh: {
    productName: 'Zen Story2Script',
    productSubtitle: '小说转结构化剧本 YAML',
    appTitle: '小说转剧本工作台',
    connectionStatus: '连接状态',
    backendConnected: '后端已连接',
    backendPending: '等待后端结果',
    localMock: '本地 mock',
    devFallback: 'dev fallback',
    inputStatus: '输入状态',
    chapters: '章节',
    chars: '字',
    workspace: '创作者 AI 剧本工作台',
    inputKicker: 'Manuscript',
    inputTitle: '输入原文',
    titleLabel: '小说标题',
    titlePlaceholder: '雾镇来信',
    sourceLabel: '小说正文',
    sourcePlaceholder: '第一章 ...\n\n第二章 ...\n\n第三章 ...',
    targetLabel: '改编目标',
    modeLabel: '转换模式',
    modeSummary: '当前模式',
    elapsed: '耗时',
    styleLabel: '风格提示',
    stylePlaceholder: '悬疑、现实主义、节奏紧凑',
    convertIdle: '开始转换',
    convertLoading: '智能体工作中',
    resultKicker: 'Structured Draft',
    resultTitle: '结构化 YAML',
    copyYaml: '复制 YAML',
    downloadYaml: '下载剧本草稿',
    emptyYaml: '转换完成后，这里会显示可复制、可下载的 YAML 剧本草稿。',
    streamingYaml: '智能体正在分阶段生成剧本，最终 YAML 会在完成后显示。',
    agentTrace: '智能体步骤',
    qualityReport: '质量报告',
    warnings: '警告',
    stepUnit: '步',
    warningUnit: '条',
    noTrace: '暂无步骤摘要。',
    noQuality: '暂无质量报告。',
    noWarnings: '暂无警告。',
    progressTitle: '转换进度',
    currentStep: '当前步骤',
    waitingStep: '等待智能体返回步骤。',
    finalizing: '正在整理最终 YAML。',
    copied: 'YAML 已复制到剪贴板。',
    copyFailed: '复制失败，请手动选择 YAML 内容复制。',
    downloaded: '已下载 {fileName}。',
    networkFailed: '网络请求失败，请检查后端服务或稍后重试。',
    requireTitle: '请先填写小说标题。',
    requireSource: '请粘贴小说正文。',
    requireChapters: '至少需要 3 个章节后才能开始转换。支持“第一章”“# 第二章”“Chapter 1”等标题格式。',
    sourceTooShort: '小说正文过短，请补充每章的主要情节后再转换。',
    chapterReady: '已满足章节要求',
    chapterMissing: '至少需要 3 个章节后才能开始转换',
    targetOptions: {
      short_drama: '短剧',
      screenplay: '影视剧本',
      scene_outline: '分场大纲'
    },
    modeOptions: {
      fast: '快速模式',
      react: '完整 ReAct'
    },
    modeHints: {
      fast: '单次生成，适合联调、预览和快速草稿。',
      react: '章节解析、故事分析、场景规划与校验修复更完整。'
    },
    qualityKeys: {
      confidence: '置信度',
      checks: '检查项',
      conversionMode: '转换模式',
      chapterCount: '章节数',
      characterCount: '角色数',
      sceneCount: '场景数',
      reactSteps: '智能体步骤数',
      repaired: '是否修复'
    },
    checkLabels: {
      fast_mode: '快速模式',
      chapter_parse: '章节解析',
      story_analysis: '故事分析',
      scene_planning: '场景规划',
      yaml_write: 'YAML 生成',
      yaml_validation: 'YAML 校验',
      yaml_repair: 'YAML 修复',
      yaml_validation_after_repair: '修复后校验'
    },
    traceLabels: {
      dev_fallback: '演示降级',
      chapter_parse: '章节解析',
      story_analysis: '故事分析',
      scene_planning: '场景规划',
      yaml_write: 'YAML 生成',
      yaml_validation: 'YAML 校验',
      yaml_repair: 'YAML 修复'
    },
    traceMessages: {
      dev_fallback: '当前使用演示降级输出。',
      chapter_parse: '已识别并校验小说章节。',
      story_analysis: '已分析角色、事件与冲突。',
      scene_planning: '已根据故事分析规划剧本场景。',
      yaml_write: '已生成结构化剧本 YAML 草稿。',
      yaml_validation: 'YAML 结构校验通过。',
      yaml_repair: '已尝试修复 YAML 结构。'
    }
  },
  en: {
    productName: 'Zen Story2Script',
    productSubtitle: 'Novel to structured screenplay YAML',
    appTitle: 'Novel to Screenplay Workspace',
    connectionStatus: 'Connection status',
    backendConnected: 'Backend connected',
    backendPending: 'Awaiting backend result',
    localMock: 'Local mock',
    devFallback: 'dev fallback',
    inputStatus: 'Input status',
    chapters: 'chapters',
    chars: 'chars',
    workspace: 'Creator AI screenplay workspace',
    inputKicker: 'Manuscript',
    inputTitle: 'Source text',
    titleLabel: 'Novel title',
    titlePlaceholder: 'Letter from Fog Town',
    sourceLabel: 'Novel text',
    sourcePlaceholder: 'Chapter 1 ...\n\nChapter 2 ...\n\nChapter 3 ...',
    targetLabel: 'Adaptation target',
    modeLabel: 'Conversion mode',
    modeSummary: 'Mode',
    elapsed: 'Elapsed',
    styleLabel: 'Style hint',
    stylePlaceholder: 'Suspense, realism, tight pacing',
    convertIdle: 'Start conversion',
    convertLoading: 'Agent at work',
    resultKicker: 'Structured Draft',
    resultTitle: 'Structured YAML',
    copyYaml: 'Copy YAML',
    downloadYaml: 'Download draft',
    emptyYaml: 'The YAML screenplay draft will appear here after conversion.',
    streamingYaml: 'The agent is generating the screenplay in stages. Final YAML will appear when complete.',
    agentTrace: 'Agent steps',
    qualityReport: 'Quality Report',
    warnings: 'Warnings',
    stepUnit: 'steps',
    warningUnit: 'warnings',
    noTrace: 'No step summary yet.',
    noQuality: 'No quality report yet.',
    noWarnings: 'No warnings.',
    progressTitle: 'Progress',
    currentStep: 'Current step',
    waitingStep: 'Waiting for agent progress.',
    finalizing: 'Finalizing YAML.',
    copied: 'YAML copied to clipboard.',
    copyFailed: 'Copy failed. Select the YAML manually.',
    downloaded: 'Downloaded {fileName}.',
    networkFailed: 'Network request failed. Check the backend service and try again.',
    requireTitle: 'Please enter a novel title.',
    requireSource: 'Please paste the novel text.',
    requireChapters: 'Please enter at least 3 chapters. Supported headings include 第一章, # 第二章, and Chapter 1.',
    sourceTooShort: 'The novel text is too short. Add the main events for each chapter before converting.',
    chapterReady: 'Chapter requirement met',
    chapterMissing: 'At least 3 chapters are required before conversion',
    targetOptions: {
      short_drama: 'Short Drama',
      screenplay: 'Screenplay',
      scene_outline: 'Scene Outline'
    },
    modeOptions: {
      fast: 'Fast Mode',
      react: 'Full ReAct'
    },
    modeHints: {
      fast: 'Single generation for integration, preview, and quick drafts.',
      react: 'Richer chapter parsing, story analysis, scene planning, validation, and repair.'
    },
    qualityKeys: {
      confidence: 'Confidence',
      checks: 'Checks',
      conversionMode: 'Conversion mode',
      chapterCount: 'Chapter count',
      characterCount: 'Character count',
      sceneCount: 'Scene count',
      reactSteps: 'Agent steps',
      repaired: 'Repaired'
    },
    checkLabels: {
      fast_mode: 'Fast mode',
      chapter_parse: 'Chapter parsing',
      story_analysis: 'Story analysis',
      scene_planning: 'Scene planning',
      yaml_write: 'YAML writing',
      yaml_validation: 'YAML validation',
      yaml_repair: 'YAML repair',
      yaml_validation_after_repair: 'Post-repair validation'
    },
    traceLabels: {
      dev_fallback: 'Demo fallback',
      chapter_parse: 'Chapter parsing',
      story_analysis: 'Story analysis',
      scene_planning: 'Scene planning',
      yaml_write: 'YAML writing',
      yaml_validation: 'YAML validation',
      yaml_repair: 'YAML repair'
    },
    traceMessages: {
      dev_fallback: 'Using demo fallback output.',
      chapter_parse: 'Parsed and validated the source chapters.',
      story_analysis: 'Analyzed characters, events, and conflicts.',
      scene_planning: 'Planned screenplay scenes from the story analysis.',
      yaml_write: 'Generated the structured screenplay YAML draft.',
      yaml_validation: 'YAML structure validation passed.',
      yaml_repair: 'Attempted to repair the YAML structure.'
    }
  }
}

const authMessages = {
  zh: {
    kicker: 'Studio Access',
    title: '登录后进入创作者工作台',
    checking: '正在检查登录状态...',
    loginTab: '登录',
    registerTab: '注册',
    emailLabel: '邮箱',
    emailPlaceholder: 'writer@example.com',
    passwordLabel: '密码',
    passwordPlaceholder: '至少 8 位',
    displayNameLabel: '昵称',
    displayNamePlaceholder: '编剧工作者',
    inviteCodeLabel: '注册邀请码',
    inviteCodePlaceholder: '输入私有邀请码',
    loginButton: '登录工作台',
    registerButton: '注册并登录',
    logoutButton: '登出',
    signedInAs: '当前用户',
    ready: '已登录，可以开始转换。',
    requireEmail: '请填写邮箱。',
    requirePassword: '请填写密码。',
    requirePasswordLength: '密码至少 8 位。',
    requireDisplayName: '请填写昵称。',
    requireInviteCode: '请填写注册邀请码。',
    flowNovel: '小说手稿',
    flowAgent: 'Agent 工作流'
  },
  en: {
    kicker: 'Studio Access',
    title: 'Sign in to enter the creator workspace',
    checking: 'Checking sign-in status...',
    loginTab: 'Sign in',
    registerTab: 'Register',
    emailLabel: 'Email',
    emailPlaceholder: 'writer@example.com',
    passwordLabel: 'Password',
    passwordPlaceholder: 'At least 8 characters',
    displayNameLabel: 'Display name',
    displayNamePlaceholder: 'Screenwriter',
    inviteCodeLabel: 'Invite code',
    inviteCodePlaceholder: 'Enter your private invite code',
    loginButton: 'Sign in',
    registerButton: 'Register and sign in',
    logoutButton: 'Sign out',
    signedInAs: 'Current user',
    ready: 'Signed in. You can start converting.',
    requireEmail: 'Please enter your email.',
    requirePassword: 'Please enter your password.',
    requirePasswordLength: 'Password must be at least 8 characters.',
    requireDisplayName: 'Please enter a display name.',
    requireInviteCode: 'Please enter the registration invite code.',
    flowNovel: 'Novel manuscript',
    flowAgent: 'Agent workflow'
  }
}

const t = computed(() => messages[locale.value])
const authText = computed(() => authMessages[locale.value])
const chapterCount = computed(() => estimateChapterCount(form.sourceText))
const sourceLength = computed(() => form.sourceText.trim().length)
const minChaptersMet = computed(() => chapterCount.value >= 3)
const hasYaml = computed(() => Boolean(result.value?.yaml))
const localizedTargetOptions = computed(() =>
  targetOptions.map((option) => ({
    ...option,
    label: t.value.targetOptions[option.value] || option.label
  }))
)
const localizedConversionModeOptions = computed(() =>
  conversionModeOptions.map((option) => ({
    ...option,
    label: t.value.modeOptions[option.value] || option.label,
    hint: t.value.modeHints[option.value] || ''
  }))
)
const rawAgentTrace = computed(() => (loading.value ? streamSteps.value : result.value?.agentTrace || []))
const localizedAgentTrace = computed(() => rawAgentTrace.value.map(formatTraceStep))
const currentStepText = computed(() => {
  if (!loading.value) {
    return localizedAgentTrace.value.at(-1) || t.value.noTrace
  }
  return localizedAgentTrace.value.at(-1) || t.value.waitingStep
})
const elapsedText = computed(() => formatDuration(elapsedSeconds.value))
const activeModeLabel = computed(() => t.value.modeOptions[form.conversionMode] || form.conversionMode)
const yamlPreviewText = computed(() => {
  if (hasYaml.value) {
    return result.value.yaml
  }
  if (loading.value && localizedAgentTrace.value.length) {
    return localizedAgentTrace.value.map((step, index) => `${index + 1}. ${step}`).join('\n')
  }
  return loading.value ? t.value.streamingYaml : t.value.emptyYaml
})
const localizedQualityEntries = computed(() => {
  const report = result.value?.qualityReport
  if (!report || typeof report !== 'object') {
    return []
  }
  return Object.entries(report).map(([key, value]) => ({
    key,
    label: t.value.qualityKeys[key] || key,
    value: formatQualityValue(key, value)
  }))
})
const statusMessages = computed(() => {
  if (!result.value) {
    return []
  }

  return [
    result.value.networkMessage,
    result.value.localMockMessage,
    result.value.backendFallbackMessage
  ].filter(Boolean)
})
const headerStatusBadges = computed(() => {
  if (result.value?.usedMock) {
    return [{ tone: 'warning', icon: 'offline', label: t.value.localMock }]
  }

  if (result.value?.usedBackendFallback) {
    return [{ tone: 'warning', icon: 'warning', label: t.value.devFallback }]
  }

  if (loading.value) {
    return [{ tone: 'neutral', icon: 'info', label: t.value.backendPending }]
  }

  return [{ tone: 'success', icon: 'online', label: t.value.backendConnected }]
})
const isRegistering = computed(() => authMode.value === 'register')
const authSubmitLabel = computed(() => (isRegistering.value ? authText.value.registerButton : authText.value.loginButton))

onMounted(async () => {
  try {
    currentUser.value = await getCurrentUser()
  } catch {
    currentUser.value = null
  } finally {
    authChecking.value = false
  }
})

async function handleAuthSubmit() {
  authErrorMessage.value = ''
  authActionMessage.value = ''

  const validationMessage = validateAuthForm()
  if (validationMessage) {
    authErrorMessage.value = validationMessage
    return
  }

  authLoading.value = true
  try {
    if (isRegistering.value) {
      currentUser.value = await register({
        email: authForm.email.trim(),
        password: authForm.password,
        displayName: authForm.displayName.trim(),
        inviteCode: authForm.inviteCode.trim()
      })
    } else {
      currentUser.value = await login({
        email: authForm.email.trim(),
        password: authForm.password
      })
    }
    authForm.password = ''
    authActionMessage.value = authText.value.ready
  } catch (error) {
    if (error instanceof ApiError) {
      authErrorMessage.value = error.message
    } else {
      authErrorMessage.value = t.value.networkFailed
    }
  } finally {
    authLoading.value = false
  }
}

async function handleLogout() {
  authErrorMessage.value = ''
  authActionMessage.value = ''
  try {
    await logout()
  } catch {
    // Keep logout usable even if the session has already expired server-side.
  } finally {
    currentUser.value = null
    result.value = null
    streamSteps.value = []
    stopElapsedTimer()
    loading.value = false
  }
}

function switchAuthMode(mode) {
  authMode.value = mode
  authErrorMessage.value = ''
  authActionMessage.value = ''
}

async function handleConvert() {
  errorMessage.value = ''
  actionMessage.value = ''

  const validationMessage = validateForm()
  if (validationMessage) {
    errorMessage.value = validationMessage
    return
  }

  loading.value = true
  result.value = null
  streamSteps.value = []
  startElapsedTimer()
  try {
    result.value = await convertNovel({
      title: form.title.trim(),
      sourceText: form.sourceText.trim(),
      targetFormat: form.targetFormat,
      styleHint: form.styleHint.trim(),
      conversionMode: form.conversionMode
    }, (event) => {
      if (event.payload?.type === 'status') {
        streamSteps.value = [...streamSteps.value, event.payload.message]
      }
      if (event.payload?.type === 'step') {
        streamSteps.value = [...streamSteps.value, event.payload.message]
      }
    })
  } catch (error) {
    if (error instanceof ApiError) {
      errorMessage.value = error.message
    } else {
      errorMessage.value = t.value.networkFailed
    }
  } finally {
    loading.value = false
    stopElapsedTimer()
  }
}

function handleAssistantApply(payload) {
  const enhancedInput = String(payload?.enhancedInput || '').trim()
  if (!enhancedInput) {
    return
  }

  form.sourceText = enhancedInput

  const newStyles = Array.isArray(payload?.styleHints)
    ? payload.styleHints.map((style) => String(style).trim()).filter(Boolean)
    : []
  if (newStyles.length) {
    const currentStyles = form.styleHint
      .split(/[、,，]/)
      .map((style) => style.trim())
      .filter(Boolean)
    form.styleHint = [...new Set([...currentStyles, ...newStyles])].join('、')
  }

  actionMessage.value = '首页输入助手已回填正文输入区。'
}

async function copyYaml() {
  if (!hasYaml.value) {
    return
  }

  actionMessage.value = ''
  try {
    await navigator.clipboard.writeText(result.value.yaml)
    actionMessage.value = t.value.copied
  } catch {
    actionMessage.value = t.value.copyFailed
  }
}

function downloadYaml() {
  if (!hasYaml.value) {
    return
  }

  const fileName = `${sanitizeFileName(form.title.trim() || 'screenplay')}.yaml`
  const blob = new Blob([result.value.yaml], { type: 'text/yaml;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
  actionMessage.value = t.value.downloaded.replace('{fileName}', fileName)
}

function validateForm() {
  if (!form.title.trim()) {
    return t.value.requireTitle
  }

  if (!form.sourceText.trim()) {
    return t.value.requireSource
  }

  if (chapterCount.value < 3) {
    return t.value.requireChapters
  }

  if (sourceLength.value < 80) {
    return t.value.sourceTooShort
  }

  return ''
}

function validateAuthForm() {
  if (!authForm.email.trim()) {
    return authText.value.requireEmail
  }

  if (!authForm.password) {
    return authText.value.requirePassword
  }

  if (authForm.password.length < 8) {
    return authText.value.requirePasswordLength
  }

  if (isRegistering.value && !authForm.displayName.trim()) {
    return authText.value.requireDisplayName
  }

  if (isRegistering.value && !authForm.inviteCode.trim()) {
    return authText.value.requireInviteCode
  }

  return ''
}

function formatTraceStep(step) {
  const text = String(step || '')
  if (text === 'conversion_started') {
    return locale.value === 'zh'
      ? '转换已启动，正在连接小说转剧本智能体。'
      : 'Conversion started. Connecting to the novel-to-screenplay agent.'
  }
  const match = text.match(/^\s*(?:\d+\.\s*)?([a-z_]+):\s*(.*)$/i)
  if (!match) {
    return text
  }
  const action = match[1]
  const label = t.value.traceLabels[action] || action
  const message = t.value.traceMessages[action] || match[2]
  return `${label}: ${message}`
}

function formatQualityValue(key, value) {
  if (Array.isArray(value)) {
    return value.map((item) => t.value.checkLabels[item] || item).join(locale.value === 'zh' ? '、' : ', ')
  }
  if (typeof value === 'boolean') {
    return locale.value === 'zh' ? (value ? '是' : '否') : String(value)
  }
  if (key === 'conversionMode') {
    return t.value.modeOptions[value] || value
  }
  return value
}

function startElapsedTimer() {
  stopElapsedTimer()
  elapsedSeconds.value = 0
  elapsedTimer = window.setInterval(() => {
    elapsedSeconds.value += 1
  }, 1000)
}

function stopElapsedTimer() {
  if (elapsedTimer) {
    window.clearInterval(elapsedTimer)
    elapsedTimer = null
  }
}

function formatDuration(totalSeconds) {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  if (minutes <= 0) {
    return `${seconds}s`
  }
  return `${minutes}m ${String(seconds).padStart(2, '0')}s`
}

function estimateChapterCount(sourceText) {
  const matches = sourceText.match(
    /(^|\n)\s*(#{1,6}\s*)?((第\s*[一二三四五六七八九十百千万零〇两\d]+\s*[章节回卷])|(chapter\s+\d+))/gi
  )
  return matches?.length || 0
}

function sanitizeFileName(value) {
  return value.replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_').slice(0, 80)
}
</script>

<template>
  <main class="workspace-shell">
    <AppHeader
      v-model:locale="locale"
      :current-user="currentUser"
      :locale-options="localeOptions"
      :auth-text="authText"
      :text="t"
      :status-badges="headerStatusBadges"
      @logout="handleLogout"
    />

    <AuthPanel
      v-if="authChecking || !currentUser"
      :checking="authChecking"
      :auth-form="authForm"
      :auth-text="authText"
      :auth-mode="authMode"
      :is-registering="isRegistering"
      :auth-loading="authLoading"
      :auth-submit-label="authSubmitLabel"
      :auth-error-message="authErrorMessage"
      :auth-action-message="authActionMessage"
      @submit="handleAuthSubmit"
      @switch-mode="switchAuthMode"
    />

    <section v-else class="workspace-grid" :aria-label="t.workspace">
      <ConversionForm
        :form="form"
        :text="t"
        :target-options="localizedTargetOptions"
        :chapter-count="chapterCount"
        :source-length="sourceLength"
        :min-chapters-met="minChaptersMet"
        :loading="loading"
        :error-message="errorMessage"
        @submit="handleConvert"
      />

      <motion.section
        class="studio-panel result-panel"
        :aria-label="t.resultKicker"
        :initial="{ opacity: 0, y: 24 }"
        :animate="{ opacity: 1, y: 0 }"
        :transition="{ duration: 0.58, delay: 0.16, ease: [0.22, 1, 0.36, 1] }"
      >
        <div class="result-control-row">
          <ModeSelector
            v-model="form.conversionMode"
            :options="localizedConversionModeOptions"
            :label="t.modeLabel"
          />

          <div class="run-status" :class="{ active: loading }" role="status">
            <div>
              <span>{{ t.modeSummary }}</span>
              <strong>{{ activeModeLabel }}</strong>
            </div>
            <div>
              <span>{{ t.elapsed }}</span>
              <strong>{{ elapsedText }}</strong>
            </div>
            <div class="run-status-step">
              <span>{{ loading ? t.currentStep : t.progressTitle }}</span>
              <strong>{{ loading ? currentStepText : (localizedAgentTrace.length ? t.finalizing : t.noTrace) }}</strong>
            </div>
          </div>
        </div>

        <div v-if="actionMessage" class="message success" role="status">
          {{ actionMessage }}
        </div>

        <div v-if="statusMessages.length" class="status-message-stack">
          <StatusBadge
            v-for="message in statusMessages"
            :key="message"
            tone="warning"
            icon="warning"
            :label="message"
          />
        </div>

        <YamlPreview
          :kicker="t.resultKicker"
          :title="t.resultTitle"
          :yaml-text="yamlPreviewText"
          :has-yaml="hasYaml"
          :loading="loading"
          :copy-label="t.copyYaml"
          :download-label="t.downloadYaml"
          @copy="copyYaml"
          @download="downloadYaml"
        />

        <div class="insight-grid">
          <ProgressTimeline
            :title="t.agentTrace"
            :steps="localizedAgentTrace"
            :empty-text="t.noTrace"
            :step-unit="t.stepUnit"
            :loading="loading"
          />

          <QualityPanel
            :title="t.qualityReport"
            :entries="localizedQualityEntries"
            :schema-version="result?.schemaVersion || '1.0'"
            :empty-text="t.noQuality"
          />
        </div>

        <WarningList
          :title="t.warnings"
          :warnings="result?.warnings || []"
          :warning-unit="t.warningUnit"
          :empty-text="t.noWarnings"
        />
      </motion.section>
    </section>
    <InputAssistant
      v-if="currentUser"
      :source-text="form.sourceText"
      :style-hint="form.styleHint"
      @apply="handleAssistantApply"
    />
  </main>
</template>
