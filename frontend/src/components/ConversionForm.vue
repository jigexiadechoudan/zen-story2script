<script setup>
import { BookOpenText, PenLine } from 'lucide-vue-next'
import { motion } from 'motion-v'
import FormatSelector from './FormatSelector.vue'
import GenerateButton from './GenerateButton.vue'

defineProps({
  form: {
    type: Object,
    required: true
  },
  text: {
    type: Object,
    required: true
  },
  targetOptions: {
    type: Array,
    required: true
  },
  chapterCount: {
    type: Number,
    required: true
  },
  sourceLength: {
    type: Number,
    required: true
  },
  minChaptersMet: {
    type: Boolean,
    required: true
  },
  loading: {
    type: Boolean,
    required: true
  },
  errorMessage: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['submit'])
</script>

<template>
  <motion.form
    class="studio-panel input-panel"
    :initial="{ opacity: 0, y: 24 }"
    :animate="{ opacity: 1, y: 0 }"
    :transition="{ duration: 0.58, delay: 0.08, ease: [0.22, 1, 0.36, 1] }"
    @submit.prevent="emit('submit')"
  >
    <div class="panel-heading">
      <div>
        <p class="section-kicker">{{ text.inputKicker }}</p>
        <h2>{{ text.inputTitle }}</h2>
      </div>
      <div class="input-meter" :class="{ ready: minChaptersMet }">
        <span>{{ chapterCount }} / 3</span>
        <small>{{ text.chapters }}</small>
      </div>
    </div>

    <label class="field with-icon">
      <span>{{ text.titleLabel }}</span>
      <BookOpenText :size="17" aria-hidden="true" />
      <input v-model="form.title" type="text" :placeholder="text.titlePlaceholder" autocomplete="off" />
    </label>

    <div class="field grow manuscript-field">
      <label class="field-label" for="source-textarea">{{ text.sourceLabel }}</label>
      <div class="manuscript-guide" :class="{ ready: minChaptersMet }">
        <p>{{ text.sourceFormatIntro }}</p>
        <ul>
          <li v-for="format in text.sourceSupportedFormats" :key="format">{{ format }}</li>
        </ul>
        <details class="chapter-template">
          <summary>{{ text.sourceExampleTitle }}</summary>
          <pre>{{ text.sourceExample }}</pre>
        </details>
      </div>
      <textarea id="source-textarea" v-model="form.sourceText" :placeholder="text.sourcePlaceholder" />
    </div>

    <div class="input-stats" :class="{ ready: minChaptersMet }">
      <span>{{ sourceLength }} {{ text.chars }}</span>
      <span>{{ chapterCount }} {{ text.chapters }}</span>
      <strong>{{ minChaptersMet ? text.chapterReady : text.chapterMissing }}</strong>
    </div>

    <FormatSelector
      v-model="form.targetFormat"
      :options="targetOptions"
      :label="text.targetLabel"
    />

    <label class="field with-icon">
      <span>{{ text.styleLabel }}</span>
      <PenLine :size="17" aria-hidden="true" />
      <input v-model="form.styleHint" type="text" :placeholder="text.stylePlaceholder" />
    </label>

    <div v-if="errorMessage" class="message error" role="alert">
      {{ errorMessage }}
    </div>

    <GenerateButton
      :loading="loading"
      :idle-label="text.convertIdle"
      :loading-label="text.convertLoading"
    />
  </motion.form>
</template>
