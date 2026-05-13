<script setup lang="ts" generic="T extends Record<string, unknown>">
import { ref, computed } from 'vue'
import { ElDialog, ElForm, ElButton } from 'element-plus'
import type { FormRules, FormInstance } from 'element-plus'

const props = withDefaults(
  defineProps<{
    title: string
    modelValue: boolean
    rules?: FormRules
    width?: string
    confirmText?: string
    cancelText?: string
    onSubmit?: (data: T) => Promise<void> | void
  }>(),
  {
    width: '520px',
    confirmText: '确定',
    cancelText: '取消',
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'submit', data: T): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const formRef = ref<FormInstance>()
const loading = ref(false)
const formData = ref<T>({} as T)
const isEdit = ref(false)

function open(data?: T, editMode = !!data) {
  formData.value = (data ? { ...data } : {}) as T
  isEdit.value = editMode
  visible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (props.onSubmit) {
    loading.value = true
    try {
      await props.onSubmit({ ...formData.value } as T)
      visible.value = false
    } catch {
      // parent handles error display
    } finally {
      loading.value = false
    }
  } else {
    emit('submit', { ...formData.value } as T)
  }
}

function handleClose() {
  formRef.value?.resetFields()
  formData.value = {} as T
  isEdit.value = false
  loading.value = false
}

defineExpose({ open })
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? `编辑${title}` : `新增${title}`"
    :width="width"
    :close-on-click-modal="false"
    destroy-on-close
    @closed="handleClose"
  >
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
      <slot :data="formData" />
    </el-form>

    <template #footer>
      <el-button @click="visible = false" :disabled="loading">{{ cancelText }}</el-button>
      <el-button type="primary" :loading="loading" @click="handleSubmit">
        {{ confirmText }}
      </el-button>
    </template>
  </el-dialog>
</template>
