<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowLeft, Check, MagicStick } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import { createWorkflow, type WorkflowRequest } from '@/api/workflow'

interface WorkflowForm {
  name: string
  description: string
  workflowJson: string
}

const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)

const exampleWorkflow = {
  status: 'DRAFT',
  version: 1,
  startNodeId: 'start',
  config: {
    scene: '智能客服分类工作流',
  },
  nodes: [
    {
      nodeId: 'start',
      nodeType: 'start',
      name: '开始',
      config: {},
      position: { x: 80, y: 120 },
    },
    {
      nodeId: 'classify_intent',
      nodeType: 'llm',
      name: '识别用户意图',
      config: {
        modelConfigId: 1,
        systemPrompt: '你是智能客服分流助手，只输出一个分类标签。',
        userPromptTemplate:
          '请将用户问题分类为：售后、物流、价格、人工。用户问题：{{inputs.userMessage}}',
        temperature: 0.2,
        outputVariable: 'category',
      },
      position: { x: 300, y: 120 },
    },
    {
      nodeId: 'is_after_sales',
      nodeType: 'condition',
      name: '是否售后',
      config: {
        expression: 'classify_intent.category == "售后"',
      },
      position: { x: 560, y: 120 },
    },
    {
      nodeId: 'answer_after_sales',
      nodeType: 'llm',
      name: '售后回复',
      config: {
        modelConfigId: 1,
        systemPrompt: '你是售后客服，回答要简洁、明确、可执行。',
        userPromptTemplate:
          '用户问题：{{inputs.userMessage}}\n请给出售后处理建议，包括退换货、质保或人工介入条件。',
        temperature: 0.4,
        outputVariable: 'answer',
      },
      position: { x: 820, y: 40 },
    },
    {
      nodeId: 'answer_general',
      nodeType: 'llm',
      name: '通用客服回复',
      config: {
        modelConfigId: 1,
        systemPrompt: '你是智能客服，回答要准确、礼貌、直接。',
        userPromptTemplate:
          '用户问题：{{inputs.userMessage}}\n分类结果：{{classify_intent.category}}\n请根据分类给出下一步处理建议。',
        temperature: 0.4,
        outputVariable: 'answer',
      },
      position: { x: 820, y: 210 },
    },
    {
      nodeId: 'end_after_sales',
      nodeType: 'end',
      name: '结束-售后',
      config: {
        responseTemplate: '{{answer_after_sales.answer}}',
      },
      position: { x: 1080, y: 40 },
    },
    {
      nodeId: 'end_general',
      nodeType: 'end',
      name: '结束-通用',
      config: {
        responseTemplate: '{{answer_general.answer}}',
      },
      position: { x: 1080, y: 210 },
    },
  ],
  edges: [
    {
      sourceNodeId: 'start',
      targetNodeId: 'classify_intent',
      edgeType: 'normal',
      priority: 0,
    },
    {
      sourceNodeId: 'classify_intent',
      targetNodeId: 'is_after_sales',
      edgeType: 'normal',
      priority: 0,
    },
    {
      sourceNodeId: 'is_after_sales',
      targetNodeId: 'answer_after_sales',
      edgeType: 'condition',
      conditionExpression: 'classify_intent.category == "售后"',
      priority: 0,
    },
    {
      sourceNodeId: 'is_after_sales',
      targetNodeId: 'answer_general',
      edgeType: 'condition',
      conditionExpression: 'classify_intent.category != "售后"',
      priority: 1,
    },
    {
      sourceNodeId: 'answer_after_sales',
      targetNodeId: 'end_after_sales',
      edgeType: 'normal',
      priority: 0,
    },
    {
      sourceNodeId: 'answer_general',
      targetNodeId: 'end_general',
      edgeType: 'normal',
      priority: 0,
    },
  ],
}

const form = reactive<WorkflowForm>({
  name: '智能客服分类工作流',
  description: '根据用户问题识别售后、物流、价格等意图，并路由到对应回复节点。',
  workflowJson: JSON.stringify(exampleWorkflow, null, 2),
})

const rules: FormRules<WorkflowForm> = {
  name: [{ required: true, message: '请输入工作流名称', trigger: 'blur' }],
  workflowJson: [{ required: true, message: '请输入工作流配置 JSON', trigger: 'blur' }],
}

function parseWorkflowJson(): Omit<WorkflowRequest, 'name' | 'description'> {
  try {
    return JSON.parse(form.workflowJson)
  } catch {
    throw new Error('工作流配置不是合法 JSON')
  }
}

function formatJson() {
  try {
    form.workflowJson = JSON.stringify(JSON.parse(form.workflowJson), null, 2)
    ElMessage.success('JSON 已格式化')
  } catch {
    ElMessage.error('工作流配置不是合法 JSON')
  }
}

async function submit() {
  if (!formRef.value) return
  await formRef.value.validate()

  let config: Omit<WorkflowRequest, 'name' | 'description'>
  try {
    config = parseWorkflowJson()
  } catch (e: any) {
    ElMessage.error(e.message)
    return
  }

  if (!config.startNodeId || !Array.isArray(config.nodes) || config.nodes.length === 0) {
    ElMessage.error('工作流配置必须包含 startNodeId 和 nodes')
    return
  }

  submitting.value = true
  try {
    await createWorkflow({
      ...config,
      name: form.name,
      description: form.description || '',
    })
    ElMessage.success('工作流已创建')
    router.push('/workflows')
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.push('/workflows')
}
</script>

<template>
  <PageHeader title="新建工作流" description="创建一个可绑定到 Agent 的客服工作流">
    <template #actions>
      <button class="btn btn-secondary" @click="goBack">
        <ArrowLeft :size="16" />
        返回列表
      </button>
    </template>
  </PageHeader>

  <div class="card workflow-create-card">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="workflow-form"
    >
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入工作流名称" maxlength="100" show-word-limit />
      </el-form-item>

      <el-form-item label="描述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          placeholder="可选，说明这个工作流的用途"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="工作流配置" prop="workflowJson">
        <el-input
          v-model="form.workflowJson"
          type="textarea"
          :rows="20"
          placeholder="请输入工作流 JSON"
          class="json-input"
        />
      </el-form-item>

      <div class="form-actions">
        <el-button :icon="MagicStick" @click="formatJson">格式化</el-button>
        <div class="submit-actions">
          <el-button @click="goBack">取消</el-button>
          <el-button type="primary" :icon="Check" :loading="submitting" @click="submit">
            提交
          </el-button>
        </div>
      </div>
    </el-form>
  </div>
</template>

<style scoped>
.workflow-create-card {
  max-width: 1080px;
}

.workflow-form {
  width: 100%;
}

.json-input :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  font-size: 13px;
  line-height: 1.55;
}

.form-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.submit-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

@media (max-width: 640px) {
  .form-actions {
    align-items: stretch;
    flex-direction: column;
  }

  .submit-actions {
    justify-content: flex-end;
  }
}
</style>
