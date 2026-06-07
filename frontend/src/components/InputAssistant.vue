<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ArrowLeft, Check, Grip, LoaderCircle, Send, WandSparkles, X } from 'lucide-vue-next'
import { chatWithInputAssistant, refineInput } from '../api'

const props = defineProps({
  sourceText: {
    type: String,
    default: ''
  },
  styleHint: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['apply'])

const STORAGE_KEY = 'story2script.inputAssistant.position'
const DEFAULT_POSITION = { right: 24, bottom: 24 }
const MAX_CONTEXT_MESSAGES = 8
const BUTTON_SIZE = 54
const PANEL_WIDTH = 390
const PANEL_HEIGHT = 620
const VIEWPORT_MARGIN = 12
const PANEL_GAP = 10
const capabilityOptions = [
  { id: 'format', label: '整理输入格式', hint: '立即把首页正文整理成可提交的清晰输入' },
  { id: 'style', label: '轻量风格建议', hint: '基于正文给出可回填到风格提示的建议' }
]

const isOpen = ref(false)
const interactionStarted = ref(false)
const refining = ref(false)
const result = ref(null)
const userDraft = ref('')
const formatDraft = ref('')
const errorMessage = ref('')
const statusMessage = ref('')
const messages = ref([])
const assistantButton = ref(null)
const panel = ref(null)
const position = reactive(loadPosition())
const draft = reactive({
  selectedCapability: ''
})

let dragState = null

const panelStyle = computed(() => ({
  left: `${panelPosition.value.left}px`,
  top: `${panelPosition.value.top}px`
}))

const triggerStyle = computed(() => ({
  right: `${position.right}px`,
  bottom: `${position.bottom}px`
}))

const panelPosition = computed(() => {
  const viewportWidth = window.innerWidth || PANEL_WIDTH
  const viewportHeight = window.innerHeight || PANEL_HEIGHT
  const panelWidth = Math.min(PANEL_WIDTH, viewportWidth - VIEWPORT_MARGIN * 2)
  const panelHeight = Math.min(PANEL_HEIGHT, viewportHeight - 90)
  const buttonLeft = viewportWidth - position.right - BUTTON_SIZE
  const buttonTop = viewportHeight - position.bottom - BUTTON_SIZE
  const buttonCenterX = buttonLeft + BUTTON_SIZE / 2
  const buttonCenterY = buttonTop + BUTTON_SIZE / 2

  let left = buttonCenterX < viewportWidth / 2
    ? buttonLeft
    : buttonLeft + BUTTON_SIZE - panelWidth
  let top = buttonCenterY < viewportHeight / 2
    ? buttonTop + BUTTON_SIZE + PANEL_GAP
    : buttonTop - panelHeight - PANEL_GAP

  left = clamp(left, VIEWPORT_MARGIN, viewportWidth - panelWidth - VIEWPORT_MARGIN)
  top = clamp(top, VIEWPORT_MARGIN, viewportHeight - panelHeight - VIEWPORT_MARGIN)

  return { left, top }
})

const isFormatMode = computed(() => draft.selectedCapability === 'format')
const isStyleMode = computed(() => draft.selectedCapability === 'style')
const canSend = computed(() => isStyleMode.value && userDraft.value.trim().length > 0 && !refining.value)
const canApply = computed(() => isFormatMode.value && Boolean(result.value?.enhancedInput) && !refining.value)

onMounted(() => {
  window.addEventListener('resize', clampAndPersistPosition)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', clampAndPersistPosition)
  stopDrag()
})

function togglePanel() {
  isOpen.value = !isOpen.value
  statusMessage.value = ''
  errorMessage.value = ''
  if (isOpen.value) {
    clampAndPersistPosition()
  }
}

function closePanel() {
  isOpen.value = false
}

function selectCapability(id) {
  draft.selectedCapability = id
  interactionStarted.value = true
  messages.value = []
  result.value = null
  userDraft.value = ''
  formatDraft.value = ''
  statusMessage.value = ''
  errorMessage.value = ''

  if (id === 'format') {
    formatDraft.value = props.sourceText.trim()
    nextTick(() => panel.value?.querySelector('.assistant-format-input')?.focus())
    return
  }

  startStyleConversation()
}

function backToCapabilitySelect() {
  interactionStarted.value = false
  messages.value = []
  result.value = null
  userDraft.value = ''
  formatDraft.value = ''
  statusMessage.value = ''
  errorMessage.value = ''
}

async function runFormatRefine() {
  const rawInput = formatDraft.value.trim() || props.sourceText.trim()
  if (!rawInput) {
    statusMessage.value = '请先粘贴需要整理的内容，或在首页正文输入框填写内容。'
    return
  }

  refining.value = true
  try {
    const response = await refineInput({
      rawInput,
      selectedStyles: [],
      target: 'story_to_script_home'
    })
    result.value = response
    statusMessage.value = response.usedFallback
      ? (response.fallbackReason || '模型暂不可用，已用本地整理。')
      : '已完成格式整理，可以应用到首页正文输入。'
  } catch {
    errorMessage.value = '输入助手暂时不可用，请稍后重试。'
  } finally {
    refining.value = false
  }
}

async function startStyleConversation() {
  messages.value = [{
    role: 'assistant',
    text: props.sourceText.trim()
      ? '我会先读取首页正文，给出可放进“风格提示”的轻量建议。'
      : '首页正文还没有内容。你可以直接描述故事或想要的风格，我会给出可回填的风格提示。'
  }]

  if (!props.sourceText.trim()) {
    nextTick(() => panel.value?.querySelector('textarea')?.focus())
    return
  }

  await requestStyleAdvice()
}

async function requestStyleAdvice() {
  refining.value = true
  errorMessage.value = ''
  statusMessage.value = ''

  try {
    const response = await chatWithInputAssistant({
      capability: 'style',
      homeInput: props.sourceText.trim(),
      currentStyleHint: props.styleHint.trim(),
      messages: messages.value.map((message) => ({
        role: message.role,
        text: message.text
      })),
      selectedStyles: [],
      target: 'story_to_script_home'
    })
    result.value = response
    appendMessage({
      role: 'assistant',
      text: response.assistantMessage || '我已给出风格建议，你可以继续追问或应用到首页风格提示。',
      suggestions: response.suggestions || []
    })
    statusMessage.value = response.usedFallback
      ? (response.fallbackReason || '模型暂不可用，已用本地风格建议。')
      : ''
  } catch {
    errorMessage.value = '输入助手暂时不可用，请稍后重试。'
  } finally {
    refining.value = false
    scrollToBottom()
    nextTick(() => panel.value?.querySelector('textarea')?.focus())
  }
}

async function handleSend() {
  const text = userDraft.value.trim()
  if (!text || !isStyleMode.value) {
    return
  }

  appendMessage({ role: 'user', text })
  userDraft.value = ''
  await requestStyleAdvice()
}

function appendMessage(message) {
  messages.value = [...messages.value, message].slice(-MAX_CONTEXT_MESSAGES)
}

function scrollToBottom() {
  nextTick(() => {
    const log = panel.value?.querySelector('.assistant-chat-log')
    if (log) {
      log.scrollTop = log.scrollHeight
    }
  })
}

function buildContextInput() {
  return [
    formatDraft.value.trim(),
    props.sourceText.trim(),
    ...messages.value.filter((message) => message.role === 'user').map((message) => message.text),
    result.value?.enhancedInput || ''
  ].filter(Boolean).join('\n\n')
}

function selectedCapabilityLabel() {
  return capabilityOptions.find((option) => option.id === draft.selectedCapability)?.label || ''
}

function handleKeydown(event) {
  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
    event.preventDefault()
    handleSend()
  }
}

function handleApply() {
  if (!canApply.value) {
    return
  }

  emit('apply', {
    enhancedInput: result.value.enhancedInput,
    styleHints: result.value.styleHints,
    rawInput: buildContextInput(),
    applyTarget: 'source'
  })
  statusMessage.value = '已应用到首页正文输入。'
}

function startDrag(event) {
  assistantButton.value?.setPointerCapture?.(event.pointerId)
  dragState = {
    pointerId: event.pointerId,
    startX: event.clientX,
    startY: event.clientY,
    startRight: position.right,
    startBottom: position.bottom,
    moved: false
  }
  window.addEventListener('pointermove', handleDrag)
  window.addEventListener('pointerup', endDrag)
}

function handleDrag(event) {
  if (!dragState) {
    return
  }

  const dx = event.clientX - dragState.startX
  const dy = event.clientY - dragState.startY
  if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
    dragState.moved = true
  }

  position.right = dragState.startRight - dx
  position.bottom = dragState.startBottom - dy
  clampPosition()
}

function endDrag(event) {
  if (!dragState) {
    return
  }

  assistantButton.value?.releasePointerCapture?.(dragState.pointerId)
  const wasDrag = dragState.moved
  stopDrag()
  persistPosition()

  if (!wasDrag) {
    togglePanel()
  }

  event.preventDefault()
}

function stopDrag() {
  window.removeEventListener('pointermove', handleDrag)
  window.removeEventListener('pointerup', endDrag)
  dragState = null
}

function clampAndPersistPosition() {
  clampPosition()
  persistPosition()
}

function clampPosition() {
  const maxRight = Math.max(VIEWPORT_MARGIN, window.innerWidth - BUTTON_SIZE - VIEWPORT_MARGIN)
  const maxBottom = Math.max(VIEWPORT_MARGIN, window.innerHeight - BUTTON_SIZE - VIEWPORT_MARGIN)
  position.right = clamp(position.right, VIEWPORT_MARGIN, maxRight)
  position.bottom = clamp(position.bottom, VIEWPORT_MARGIN, maxBottom)
}

function clamp(value, min, max) {
  return Math.min(Math.max(value, min), max)
}

function loadPosition() {
  try {
    const saved = JSON.parse(window.localStorage.getItem(STORAGE_KEY) || 'null')
    if (Number.isFinite(saved?.right) && Number.isFinite(saved?.bottom)) {
      return { right: saved.right, bottom: saved.bottom }
    }
  } catch {
    // Ignore invalid saved positions.
  }
  return { ...DEFAULT_POSITION }
}

function persistPosition() {
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify({
    right: position.right,
    bottom: position.bottom
  }))
}
</script>

<template>
  <section class="input-assistant-layer" aria-label="首页输入助手">
    <div
      v-if="isOpen"
      ref="panel"
      class="input-assistant-panel assistant-chat-panel"
      :style="panelStyle"
      role="dialog"
      aria-modal="false"
      aria-label="首页输入助手"
    >
      <div class="assistant-panel-header assistant-chat-header">
        <div>
          <p class="section-kicker">Input Helper</p>
          <h3><WandSparkles :size="16" aria-hidden="true" />首页输入助手</h3>
        </div>
        <button type="button" class="assistant-icon-button" aria-label="关闭首页输入助手" @click="closePanel">
          <X :size="16" aria-hidden="true" />
        </button>
      </div>

      <template v-if="!interactionStarted">
        <div class="assistant-capability-group" role="group" aria-label="助手能力">
          <span>选择本次助手能力</span>
          <button
            v-for="option in capabilityOptions"
            :key="option.id"
            type="button"
            class="assistant-capability"
            :class="{ active: draft.selectedCapability === option.id }"
            @click="selectCapability(option.id)"
          >
            <Check v-if="draft.selectedCapability === option.id" :size="15" aria-hidden="true" />
            <span>
              <strong>{{ option.label }}</strong>
              <small>{{ option.hint }}</small>
            </span>
          </button>
        </div>
      </template>

      <template v-else>
        <div class="assistant-session-bar">
          <span>已选择：{{ selectedCapabilityLabel() }}</span>
          <button type="button" class="assistant-back-button" @click="backToCapabilitySelect">
            <ArrowLeft :size="14" aria-hidden="true" />
            返回选择
          </button>
        </div>

        <template v-if="isFormatMode">
          <div class="assistant-format-body">
            <label class="assistant-format-editor">
              <span>粘贴需要整理的内容</span>
              <textarea
                v-model="formatDraft"
                class="assistant-format-input"
                placeholder="粘贴故事梗概、章节内容或零散需求；留空时会使用首页正文输入框内容。"
                rows="7"
              />
            </label>

            <button
              type="button"
              class="icon-text-button assistant-format-submit"
              :disabled="refining || (!formatDraft.trim() && !props.sourceText.trim())"
              @click="runFormatRefine"
            >
              <LoaderCircle v-if="refining" :size="15" class="assistant-spin" aria-hidden="true" />
              {{ refining ? '正在整理...' : '整理输入格式' }}
            </button>

            <div v-if="result?.enhancedInput" class="assistant-format-result">
              <span>整理结果预览</span>
              <pre>{{ result.enhancedInput }}</pre>
              <ul v-if="result.suggestions?.length" class="assistant-chat-suggestions">
                <li v-for="suggestion in result.suggestions.slice(0, 3)" :key="suggestion">{{ suggestion }}</li>
              </ul>
            </div>
            <div v-else-if="statusMessage" class="assistant-format-state">
              {{ statusMessage }}
            </div>
          </div>
        </template>

        <template v-else>
          <div class="assistant-chat-log" aria-live="polite">
            <div
              v-for="(message, index) in messages"
              :key="`${message.role}-${index}-${message.text}`"
              class="assistant-chat-message"
              :class="`assistant-chat-${message.role}`"
            >
              <p>{{ message.text }}</p>
              <ul v-if="message.suggestions?.length" class="assistant-chat-suggestions">
                <li v-for="suggestion in message.suggestions.slice(0, 3)" :key="suggestion">{{ suggestion }}</li>
              </ul>
            </div>
            <div v-if="refining" class="assistant-chat-message assistant-chat-assistant">
              <p><LoaderCircle :size="14" class="assistant-spin" aria-hidden="true" />正在思考...</p>
            </div>
          </div>
        </template>

        <div v-if="statusMessage && (isStyleMode || result?.enhancedInput)" class="assistant-message success" role="status">
          {{ statusMessage }}
        </div>
        <div v-if="errorMessage" class="assistant-message error" role="alert">{{ errorMessage }}</div>

        <div v-if="canApply" class="assistant-apply-row">
          <button type="button" class="icon-text-button assistant-apply" @click="handleApply">
            应用到首页输入
          </button>
        </div>

        <div v-if="isStyleMode" class="assistant-chat-composer">
          <textarea
            v-model="userDraft"
            placeholder="继续补充风格想法，Ctrl+Enter 发送"
            rows="1"
            @keydown="handleKeydown"
          />
          <button type="button" class="assistant-send-button" :disabled="!canSend" aria-label="发送" @click="handleSend">
            <LoaderCircle v-if="refining" :size="18" class="assistant-spin" aria-hidden="true" />
            <Send v-else :size="18" aria-hidden="true" />
          </button>
        </div>
      </template>
    </div>

    <button
      ref="assistantButton"
      type="button"
      class="input-assistant-trigger"
      :style="triggerStyle"
      aria-label="打开首页输入助手，可拖动"
      @pointerdown.prevent="startDrag"
    >
      <Grip :size="14" class="assistant-grip" aria-hidden="true" />
      <WandSparkles :size="24" aria-hidden="true" />
    </button>
  </section>
</template>
