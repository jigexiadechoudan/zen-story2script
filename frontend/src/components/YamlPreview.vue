<script setup>
import { Clipboard, Download, FileCode2 } from 'lucide-vue-next'
import { motion } from 'motion-v'

defineProps({
  title: {
    type: String,
    required: true
  },
  kicker: {
    type: String,
    required: true
  },
  yamlText: {
    type: String,
    required: true
  },
  hasYaml: {
    type: Boolean,
    required: true
  },
  loading: {
    type: Boolean,
    required: true
  },
  copyLabel: {
    type: String,
    required: true
  },
  downloadLabel: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['copy', 'download'])
</script>

<template>
  <div class="yaml-card">
    <div class="panel-heading result-heading">
      <div>
        <p class="section-kicker">{{ kicker }}</p>
        <h2>
          <FileCode2 :size="20" aria-hidden="true" />
          <span>{{ title }}</span>
        </h2>
      </div>
      <div class="button-row">
        <button type="button" class="icon-text-button" :disabled="!hasYaml" @click="emit('copy')">
          <Clipboard :size="16" aria-hidden="true" />
          <span>{{ copyLabel }}</span>
        </button>
        <button type="button" class="icon-text-button" :disabled="!hasYaml" @click="emit('download')">
          <Download :size="16" aria-hidden="true" />
          <span>{{ downloadLabel }}</span>
        </button>
      </div>
    </div>

    <motion.pre
      class="yaml-preview"
      :class="{ empty: !hasYaml && !loading, streaming: loading }"
      :initial="{ opacity: 0, y: 10 }"
      :animate="{ opacity: 1, y: 0 }"
      :transition="{ duration: 0.36, ease: [0.22, 1, 0.36, 1] }"
    >{{ yamlText }}</motion.pre>
  </div>
</template>
