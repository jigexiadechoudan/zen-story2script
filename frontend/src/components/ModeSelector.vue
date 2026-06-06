<script setup>
import { FastForward, Route } from 'lucide-vue-next'

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
  fast: FastForward,
  react: Route
}
</script>

<template>
  <fieldset class="selector-group mode-group">
    <legend>{{ label }}</legend>
    <label v-for="option in options" :key="option.value" class="selector-option mode-option">
      <input
        :checked="modelValue === option.value"
        type="radio"
        name="conversionMode"
        :value="option.value"
        @change="emit('update:modelValue', option.value)"
      />
      <component :is="icons[option.value] || Route" :size="18" aria-hidden="true" />
      <span>
        <strong>{{ option.label }}</strong>
        <small>{{ option.hint }}</small>
      </span>
    </label>
  </fieldset>
</template>
