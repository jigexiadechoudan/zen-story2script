<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ArrowLeft, Check, Grip, LoaderCircle, Send, WandSparkles, X } from 'lucide-vue-next'
import { chatWithInputAssistant } from '../api'

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
const capabilityOptions = [
  { id: 'format', label: '整理输入格式', hint: '把零散想法整理成清晰段落' },
  { id: 'style', label: '轻量风格建议', hint: '提示可选风格，不改核心意思' }
]
const styleOptions = ['悬疑', '治愈', '电影感', '短剧感', '轻喜剧', '赛博朋克']

const isOpen = ref(false)
const interactionStarted = ref(false)
const refining = ref(false)
const result = ref(null)
const userDraft = ref('')
const errorMessage = ref('')
const statusMessage = ref('')
const messages = ref([])
const assistantButton = ref(null)
const panel = ref(null)
const position = reactive(loadPosition())
const draft = reactive({
  selectedCapability: '',
  selectedStyles: []
})

let dragState = null

const panelStyle = computed(() => ({
  right: `${position.right}px`,
  bottom: `${position.bottom + 62}px`
}))

const triggerStyle = computed(() => ({
  right: `${position.right}px`,
  bottom: `${position.bottom}px`
}))

const canSend = computed(() => userDraft.value.trim().length > 0 && !refining.value)
const canApply = computed(() => Boolean(result.value?.enhancedInput))

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
    seedStyles()
  }
}

function seedStyles() {
  if (!draft.selectedStyles.length && props.styleHint.trim()) {
    draft.selectedStyles = props.styleHint
      .split(/[、,，]/)
      .map((style) => style.trim())
      .filter((style) => styleOptions.includes(style))
  }
}

function seedConversation() {
  if (messages.value.length) {
    return
  }

  messages.value = [{
    role: 'assistant',
    text: props.sourceText?.trim()
      ? `已选择：${selectedCapabilityLabel()}。我看到首页已有输入，你可以像聊天一样继续补充。`
      : `已选择：${selectedCapabilityLabel()}。直接告诉我故事、梗概或章节想法。`
  }]
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
  statusMessage.value = ''
  errorMessage.value = ''
  seedConversation()
  nextTick(() => panel.value?.querySelector('textarea')?.focus())
}

function backToCapabilitySelect() {
  interactionStarted.value = false
  messages.value = []
  result.value = null
  userDraft.value = ''
  statusMessage.value = ''
  errorMessage.value = ''
}

function toggleStyle(style) {
  if (draft.selectedStyles.includes(style)) {
    draft.selectedStyles = draft.selectedStyles.filter((item) => item !== style)
  } else {
    draft.selectedStyles = [...draft.selectedStyles, style]
  }
  statusMessage.value = ''
}

async function handleSend() {
  const text = userDraft.value.trim()
  if (!text) {
    return
  }

  appendMessage({ role: 'user', text })
  userDraft.value = ''
  refining.value = true
  errorMessage.value = ''
  statusMessage.value = ''

  try {
    const response = await chatWithInputAssistant({
      capability: draft.selectedCapability || 'format',
      homeInput: props.sourceText.trim(),
      messages: messages.value.map((message) => ({
        role: message.role,
        text: message.text
      })),
      selectedStyles: draft.selectedStyles,
      target: 'story_to_script_home'
    })
    result.value = response
    appendMessage({
      role: 'assistant',
      text: response.assistantMessage || '我已整理当前输入，你可以继续补充或应用到首页。',
      suggestions: response.suggestions || []
    })
    statusMessage.value = response.usedFallback
      ? (response.fallbackReason || '模型暂不可用，已用本地整理。')
      : ''
  } catch {
    errorMessage.value = '输入助手暂时不可用，请稍后重试。'
  } finally {
    refining.value = false
    scrollToBottom()
  }
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
    rawInput: buildContextInput()
  })
  statusMessage.value = '已应用到首页输入。'
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
  const margin = 12
  const buttonSize = 54
  const maxRight = Math.max(margin, window.innerWidth - buttonSize - margin)
  const maxBottom = Math.max(margin, window.innerHeight - buttonSize - margin)
  position.right = Math.min(Math.max(position.right, margin), maxRight)
  position.bottom = Math.min(Math.max(position.bottom, margin), maxBottom)
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

        <div class="assistant-style-strip" role="group" aria-label="风格标签">
          <button
            v-for="style in styleOptions"
            :key="style"
            type="button"
            class="assistant-chip"
            :class="{ active: draft.selectedStyles.includes(style) }"
            @click="toggleStyle(style)"
          >
            <Check v-if="draft.selectedStyles.includes(style)" :size="13" aria-hidden="true" />
            {{ style }}
          </button>
        </div>

        <div v-if="statusMessage" class="assistant-message success" role="status">{{ statusMessage }}</div>
        <div v-if="errorMessage" class="assistant-message error" role="alert">{{ errorMessage }}</div>

        <div v-if="canApply" class="assistant-apply-row">
          <button type="button" class="icon-text-button assistant-apply" @click="handleApply">
            应用到首页输入
          </button>
        </div>

        <div class="assistant-chat-composer">
          <textarea
            v-model="userDraft"
            placeholder="输入你的想法，Ctrl+Enter 发送"
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
