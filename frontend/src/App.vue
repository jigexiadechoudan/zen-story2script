<script setup>
import { computed, reactive, ref } from 'vue'
import { ApiError, convertNovel } from './api'

const targetOptions = [
  { value: 'short_drama', label: '短剧' },
  { value: 'screenplay', label: '影视剧本' },
  { value: 'scene_outline', label: '分场大纲' }
]

const form = reactive({
  title: '',
  sourceText: '',
  targetFormat: 'short_drama',
  styleHint: ''
})

const loading = ref(false)
const errorMessage = ref('')
const result = ref(null)
const actionMessage = ref('')

const chapterCount = computed(() => estimateChapterCount(form.sourceText))
const sourceLength = computed(() => form.sourceText.trim().length)
const hasYaml = computed(() => Boolean(result.value?.yaml))

async function handleConvert() {
  errorMessage.value = ''
  actionMessage.value = ''

  const validationMessage = validateForm()
  if (validationMessage) {
    errorMessage.value = validationMessage
    return
  }

  loading.value = true
  try {
    result.value = await convertNovel({
      title: form.title.trim(),
      sourceText: form.sourceText.trim(),
      targetFormat: form.targetFormat,
      styleHint: form.styleHint.trim()
    })
  } catch (error) {
    if (error instanceof ApiError) {
      errorMessage.value = error.message
    } else {
      errorMessage.value = '网络请求失败，请检查后端服务或稍后重试。'
    }
  } finally {
    loading.value = false
  }
}

async function copyYaml() {
  if (!hasYaml.value) {
    return
  }

  actionMessage.value = ''
  try {
    await navigator.clipboard.writeText(result.value.yaml)
    actionMessage.value = 'YAML 已复制到剪贴板。'
  } catch {
    actionMessage.value = '复制失败，请手动选择 YAML 内容复制。'
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
  actionMessage.value = `已下载 ${fileName}。`
}

function validateForm() {
  if (!form.title.trim()) {
    return '请先填写小说标题。'
  }

  if (!form.sourceText.trim()) {
    return '请粘贴小说正文。'
  }

  if (sourceLength.value < 240 || chapterCount.value < 3) {
    return '小说正文明显不足，建议输入 3 章以上小说文本后再转换。'
  }

  return ''
}

function estimateChapterCount(sourceText) {
  const matches = sourceText.match(/(^|\n)\s*(第[一二三四五六七八九十百千万\d]+[章节回]|chapter\s+\d+)/gi)
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
        <h1 id="page-title">小说转剧本工作台</h1>
      </div>
      <div class="status-strip" aria-label="输入状态">
        <span>章节 {{ chapterCount }}</span>
        <span>{{ sourceLength }} 字</span>
      </div>
    </section>

    <section class="workspace-grid" aria-label="转换工作区">
      <form class="panel input-panel" @submit.prevent="handleConvert">
        <div class="panel-heading">
          <div>
            <p class="section-kicker">输入区</p>
            <h2>原文与改编目标</h2>
          </div>
        </div>

        <label class="field">
          <span>小说标题</span>
          <input v-model="form.title" type="text" placeholder="雾镇来信" autocomplete="off" />
        </label>

        <label class="field grow">
          <span>小说正文</span>
          <textarea
            v-model="form.sourceText"
            placeholder="第一章 ...&#10;&#10;第二章 ...&#10;&#10;第三章 ..."
          />
        </label>

        <fieldset class="format-group">
          <legend>改编目标</legend>
          <label v-for="option in targetOptions" :key="option.value" class="format-option">
            <input v-model="form.targetFormat" type="radio" name="targetFormat" :value="option.value" />
            <span>{{ option.label }}</span>
          </label>
        </fieldset>

        <label class="field">
          <span>风格提示</span>
          <input v-model="form.styleHint" type="text" placeholder="悬疑、现实主义、节奏紧凑" />
        </label>

        <div v-if="errorMessage" class="message error" role="alert">
          {{ errorMessage }}
        </div>

        <button class="primary-button" type="submit" :disabled="loading">
          {{ loading ? '转换中...' : '开始转换' }}
        </button>
      </form>

      <section class="panel result-panel" aria-label="结果区">
        <div class="panel-heading result-heading">
          <div>
            <p class="section-kicker">结果区</p>
            <h2>结构化 YAML</h2>
          </div>
          <div class="button-row">
            <button type="button" class="secondary-button" :disabled="!hasYaml" @click="copyYaml">复制 YAML</button>
            <button type="button" class="secondary-button" :disabled="!hasYaml" @click="downloadYaml">下载 YAML</button>
          </div>
        </div>

        <div v-if="actionMessage" class="message success" role="status">
          {{ actionMessage }}
        </div>

        <div v-if="result?.usedMock" class="message notice" role="status">
          后端未连通，当前展示 mock fallback，结构与接口响应保持一致。
        </div>

        <pre class="yaml-preview" :class="{ empty: !hasYaml }">{{ hasYaml ? result.yaml : '转换后将在这里预览 YAML 输出。' }}</pre>

        <div class="insight-grid">
          <article class="info-block">
            <div class="info-title">
              <h3>Agent Trace</h3>
              <span>{{ result?.agentTrace?.length || 0 }} 步</span>
            </div>
            <ol v-if="result?.agentTrace?.length" class="trace-list">
              <li v-for="step in result.agentTrace" :key="step">{{ step }}</li>
            </ol>
            <p v-else class="empty-text">暂无步骤摘要。</p>
          </article>

          <article class="info-block">
            <div class="info-title">
              <h3>Quality Report</h3>
              <span>v{{ result?.schemaVersion || '1.0' }}</span>
            </div>
            <dl v-if="result?.qualityReport && Object.keys(result.qualityReport).length" class="metric-list">
              <template v-for="(value, key) in result.qualityReport" :key="key">
                <dt>{{ key }}</dt>
                <dd>{{ Array.isArray(value) ? value.join('、') : value }}</dd>
              </template>
            </dl>
            <p v-else class="empty-text">暂无质量报告。</p>
          </article>
        </div>

        <article class="info-block warning-block">
          <div class="info-title">
            <h3>Warnings</h3>
            <span>{{ result?.warnings?.length || 0 }} 条</span>
          </div>
          <ul v-if="result?.warnings?.length" class="warning-list">
            <li v-for="warning in result.warnings" :key="warning">{{ warning }}</li>
          </ul>
          <p v-else class="empty-text">暂无警告。</p>
        </article>
      </section>
    </section>
  </main>
</template>
