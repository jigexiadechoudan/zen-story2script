<script setup>
import { Clapperboard, FileText, PanelsTopLeft } from 'lucide-vue-next'

defineProps({
  modelValue: {
    type: String,
    required: true
  },
  options: {
    type: Array,
    required: true
  },
  label: {
    type: String,
    required: true
  }
})

const emit = defineEmits(['update:modelValue'])

const icons = {
  short_drama: Clapperboard,
  screenplay: FileText,
  scene_outline: PanelsTopLeft
}
</script>

<template>
  <fieldset class="selector-group format-group">
    <legend>{{ label }}</legend>
    <label v-for="option in options" :key="option.value" class="selector-option format-option">
      <input
        :checked="modelValue === option.value"
        type="radio"
        name="targetFormat"
        :value="option.value"
        @change="emit('update:modelValue', option.value)"
      />
      <component :is="icons[option.value] || FileText" :size="18" aria-hidden="true" />
      <span>{{ option.label }}</span>
    </label>
  </fieldset>
</template>
