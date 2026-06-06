<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ApiError, convertNovel, getCurrentUser, login, logout, register } from './api'

const targetOptions = [
  { value: 'short_drama', label: '短剧' },
  { value: 'screenplay', label: '影视剧本' },
  { value: 'scene_outline', label: '分场大纲' }
]

const conversionModeOptions = [
  { value: 'fast', label: '快速' },
  { value: 'react', label: '完整' }
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
    appTitle: '小说转剧本工作台',
    inputStatus: '输入状态',
    chapters: '章节',
    chars: '字',
    workspace: '转换工作区',
    inputKicker: '输入区',
    inputTitle: '原文与改编目标',
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
    convertLoading: '转换中...',
    resultKicker: '结果区',
    resultTitle: '结构化 YAML',
    copyYaml: '复制 YAML',
    downloadYaml: '下载 YAML',
    emptyYaml: '转换后将在这里预览 YAML 输出。',
    streamingYaml: '智能体正在分阶段生成剧本，最终 YAML 将在完成后显示。',
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
    requireChapters: '请至少输入 3 章小说文本，支持 # 第一章、## 第二章、Chapter 1 等标题格式。',
    sourceTooShort: '小说正文过短，请补充每章的主要情节后再转换。',
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
      fast: '1 次模型生成，适合联调和 demo。',
      react: '多步分析规划，质量更细但更慢。'
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
    appTitle: 'Novel to Screenplay Workspace',
    inputStatus: 'Input status',
    chapters: 'Chapters',
    chars: 'chars',
    workspace: 'Conversion workspace',
    inputKicker: 'Input',
    inputTitle: 'Source and adaptation target',
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
    convertLoading: 'Converting...',
    resultKicker: 'Result',
    resultTitle: 'Structured YAML',
    copyYaml: 'Copy YAML',
    downloadYaml: 'Download YAML',
    emptyYaml: 'YAML output will appear here after conversion.',
    streamingYaml: 'The agent is generating the screenplay in stages. Final YAML will appear when complete.',
    agentTrace: 'Agent Trace',
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
    requireChapters: 'Please enter at least 3 chapters. Supported headings include # 第一章, ## 第二章, and Chapter 1.',
    sourceTooShort: 'The novel text is too short. Add the main events for each chapter before converting.',
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
      fast: 'One model generation for testing and demos.',
      react: 'Multi-step analysis and planning, slower but richer.'
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

const t = computed(() => messages[locale.value])
const authText = computed(() => {
  if (locale.value === 'en') {
    return {
      kicker: 'Account',
      title: 'Sign in to use the conversion workspace',
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
      signedInAs: 'Signed in as',
      ready: 'Signed in. You can start converting.',
      requireEmail: 'Please enter your email.',
      requirePassword: 'Please enter your password.',
      requirePasswordLength: 'Password must be at least 8 characters.',
      requireDisplayName: 'Please enter a display name.',
      requireInviteCode: 'Please enter the registration invite code.'
    }
  }

  return {
    kicker: '账号',
    title: '登录后使用转换工作台',
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
    loginButton: '登录',
    registerButton: '注册并登录',
    logoutButton: '退出',
    signedInAs: '当前账号',
    ready: '已登录，可以开始转换。',
    requireEmail: '请填写邮箱。',
    requirePassword: '请填写密码。',
    requirePasswordLength: '密码至少 8 位。',
    requireDisplayName: '请填写昵称。',
    requireInviteCode: '请填写注册邀请码。'
  }
})

const chapterCount = computed(() => estimateChapterCount(form.sourceText))
const sourceLength = computed(() => form.sourceText.trim().length)
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
    return locale.value === 'zh' ? '转换已启动，正在连接小说转剧本智能体。' : 'Conversion started. Connecting to the novel-to-screenplay agent.'
  }
  const match = text.match(/^\s*(?:\d+\.\s*)?([a-z_]+):\s*(.*)$/i)
  if (!match) {
    return text
  }
  const action = match[1]
  const label = t.value.traceLabels[action] || action
  const message = t.value.traceMessages[action] || match[2]
  return `${label}：${message}`
}

function formatQualityValue(key, value) {
  if (Array.isArray(value)) {
    return value.map((item) => t.value.checkLabels[item] || item).join(locale.value === 'zh' ? '、' : ', ')
  }
  if (typeof value === 'boolean') {
    return locale.value === 'zh' ? (value ? '是' : '否') : String(value)
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
  const matches = sourceText.match(/(^|\n)\s*(#{1,6}\s*)?(第\s*[一二三四五六七八九十百千万零〇两\d０-９]+\s*[章节回]|chapter\s+\d+)/gi)
  return matches?.length || 0
}

function sanitizeFileName(value) {
  return value.replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_').slice(0, 80)
}
</script>

<template>
  <main class="workspace-shell">
    <section class="workspace-header" aria-labelledby="page-title">
      <div>
        <p class="eyebrow">Zen Story2Script MVP</p>
        <h1 id="page-title">{{ t.appTitle }}</h1>
      </div>
      <div class="header-tools">
        <div v-if="currentUser" class="account-strip" :aria-label="authText.signedInAs">
          <span>{{ authText.signedInAs }}</span>
          <strong>{{ currentUser.displayName || currentUser.email }}</strong>
          <button type="button" class="secondary-button compact" @click="handleLogout">
            {{ authText.logoutButton }}
          </button>
        </div>
        <div class="language-toggle" aria-label="Language">
          <button
            v-for="option in localeOptions"
            :key="option.value"
            type="button"
            :class="{ active: locale === option.value }"
            @click="locale = option.value"
          >
            {{ option.label }}
          </button>
        </div>
        <div class="status-strip" :aria-label="t.inputStatus">
          <span>{{ t.chapters }} {{ chapterCount }}</span>
          <span>{{ sourceLength }} {{ t.chars }}</span>
        </div>
      </div>
    </section>

    <section v-if="authChecking" class="panel auth-panel auth-panel-compact" role="status">
      <p class="section-kicker">{{ authText.kicker }}</p>
      <h2>{{ authText.checking }}</h2>
    </section>

    <section v-else-if="!currentUser" class="panel auth-panel" aria-labelledby="auth-title">
      <div class="panel-heading">
        <div>
          <p class="section-kicker">{{ authText.kicker }}</p>
          <h2 id="auth-title">{{ authText.title }}</h2>
        </div>
        <div class="auth-tabs" role="tablist" aria-label="Authentication mode">
          <button
            type="button"
            :class="{ active: authMode === 'login' }"
            @click="switchAuthMode('login')"
          >
            {{ authText.loginTab }}
          </button>
          <button
            type="button"
            :class="{ active: authMode === 'register' }"
            @click="switchAuthMode('register')"
          >
            {{ authText.registerTab }}
          </button>
        </div>
      </div>

      <form class="auth-form" @submit.prevent="handleAuthSubmit">
        <label class="field">
          <span>{{ authText.emailLabel }}</span>
          <input v-model="authForm.email" type="text" :placeholder="authText.emailPlaceholder" autocomplete="email" />
        </label>

        <label v-if="isRegistering" class="field">
          <span>{{ authText.displayNameLabel }}</span>
          <input v-model="authForm.displayName" type="text" :placeholder="authText.displayNamePlaceholder" autocomplete="name" />
        </label>

        <label class="field">
          <span>{{ authText.passwordLabel }}</span>
          <input v-model="authForm.password" type="password" :placeholder="authText.passwordPlaceholder" autocomplete="current-password" />
        </label>

        <label v-if="isRegistering" class="field">
          <span>{{ authText.inviteCodeLabel }}</span>
          <input v-model="authForm.inviteCode" type="text" :placeholder="authText.inviteCodePlaceholder" autocomplete="off" />
        </label>

        <div v-if="authErrorMessage" class="message error" role="alert">
          {{ authErrorMessage }}
        </div>

        <div v-if="authActionMessage" class="message success" role="status">
          {{ authActionMessage }}
        </div>

        <button class="primary-button" type="submit" :disabled="authLoading">
          {{ authSubmitLabel }}
        </button>
      </form>
    </section>

    <section v-else class="workspace-grid" :aria-label="t.workspace">
      <form class="panel input-panel" @submit.prevent="handleConvert">
        <div class="panel-heading">
          <div>
            <p class="section-kicker">{{ t.inputKicker }}</p>
            <h2>{{ t.inputTitle }}</h2>
          </div>
        </div>

        <label class="field">
          <span>{{ t.titleLabel }}</span>
          <input v-model="form.title" type="text" :placeholder="t.titlePlaceholder" autocomplete="off" />
        </label>

        <label class="field grow">
          <span>{{ t.sourceLabel }}</span>
          <textarea
            v-model="form.sourceText"
            :placeholder="t.sourcePlaceholder"
          />
        </label>

        <fieldset class="format-group">
          <legend>{{ t.targetLabel }}</legend>
          <label v-for="option in localizedTargetOptions" :key="option.value" class="format-option">
            <input v-model="form.targetFormat" type="radio" name="targetFormat" :value="option.value" />
            <span>{{ option.label }}</span>
          </label>
        </fieldset>

        <fieldset class="mode-group">
          <legend>{{ t.modeLabel }}</legend>
          <label v-for="option in localizedConversionModeOptions" :key="option.value" class="mode-option">
            <input v-model="form.conversionMode" type="radio" name="conversionMode" :value="option.value" />
            <span>
              <strong>{{ option.label }}</strong>
              <small>{{ option.hint }}</small>
            </span>
          </label>
        </fieldset>

        <label class="field">
          <span>{{ t.styleLabel }}</span>
          <input v-model="form.styleHint" type="text" :placeholder="t.stylePlaceholder" />
        </label>

        <div v-if="errorMessage" class="message error" role="alert">
          {{ errorMessage }}
        </div>

        <button class="primary-button" type="submit" :disabled="loading">
          {{ loading ? t.convertLoading : t.convertIdle }}
        </button>
      </form>

      <section class="panel result-panel" :aria-label="t.resultKicker">
        <div class="panel-heading result-heading">
          <div>
            <p class="section-kicker">{{ t.resultKicker }}</p>
            <h2>{{ t.resultTitle }}</h2>
          </div>
          <div class="button-row">
            <button type="button" class="secondary-button" :disabled="!hasYaml" @click="copyYaml">{{ t.copyYaml }}</button>
            <button type="button" class="secondary-button" :disabled="!hasYaml" @click="downloadYaml">{{ t.downloadYaml }}</button>
          </div>
        </div>

        <div v-if="actionMessage" class="message success" role="status">
          {{ actionMessage }}
        </div>

        <div v-for="message in statusMessages" :key="message" class="message notice" role="status">
          {{ message }}
        </div>

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

        <pre class="yaml-preview" :class="{ empty: !hasYaml, streaming: loading }">{{ yamlPreviewText }}</pre>

        <div class="insight-grid">
          <article class="info-block">
            <div class="info-title">
              <h3>{{ t.agentTrace }}</h3>
              <span>{{ localizedAgentTrace.length }} {{ t.stepUnit }}</span>
            </div>
            <ol v-if="localizedAgentTrace.length" class="trace-list">
              <li v-for="step in localizedAgentTrace" :key="step">{{ step }}</li>
            </ol>
            <p v-else class="empty-text">{{ t.noTrace }}</p>
          </article>

          <article class="info-block">
            <div class="info-title">
              <h3>{{ t.qualityReport }}</h3>
              <span>v{{ result?.schemaVersion || '1.0' }}</span>
            </div>
            <dl v-if="localizedQualityEntries.length" class="metric-list">
              <template v-for="entry in localizedQualityEntries" :key="entry.key">
                <dt>{{ entry.label }}</dt>
                <dd>{{ entry.value }}</dd>
              </template>
            </dl>
            <p v-else class="empty-text">{{ t.noQuality }}</p>
          </article>
        </div>

        <article class="info-block warning-block">
          <div class="info-title">
            <h3>{{ t.warnings }}</h3>
            <span>{{ result?.warnings?.length || 0 }} {{ t.warningUnit }}</span>
          </div>
          <ul v-if="result?.warnings?.length" class="warning-list">
            <li v-for="warning in result.warnings" :key="warning">{{ warning }}</li>
          </ul>
          <p v-else class="empty-text">{{ t.noWarnings }}</p>
        </article>
      </section>
    </section>
  </main>
</template>
