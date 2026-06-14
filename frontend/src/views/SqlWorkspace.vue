<template>
  <div class="workspace">
    <!-- LEFT PANEL -->
    <div class="left-panel">
      <div class="toolbar">
        <el-button-group size="small" class="mode-switch">
          <el-button :type="mode === 'sql' ? 'primary' : 'default'" @click="mode = 'sql'">📝 SQL编辑</el-button>
          <el-button :type="mode === 'fill' ? 'primary' : 'default'" @click="mode = 'fill'">📋 智能填表</el-button>
        </el-button-group>

        <template v-if="mode === 'sql'">
          <el-button type="primary" @click="handleExecute" :loading="executing" size="small"><el-icon><CaretRight /></el-icon>执行</el-button>
          <el-button @click="handleClear" size="small">清空</el-button>
          <el-button type="warning" @click="handleFixCurrentSQL" :loading="fixing" :disabled="!sqlContent.trim()" size="small">🤖 AI修复</el-button>
          <el-divider direction="vertical" />
          <el-button :type="explainVisible && explainTab === 'ai' ? 'primary' : 'default'" @click="handleExplainCode" :loading="explainLoading" size="small">💡 解释代码</el-button>
          <el-button :type="explainVisible && explainTab === 'plan' ? 'primary' : 'default'" @click="handleExplainPlan" :loading="explainPlanLoading" size="small">📊 执行计划</el-button>

          <!-- History dropdown -->
          <el-popover placement="bottom-end" :width="520" trigger="click" :hide-after="0" popper-class="history-popover">
            <template #reference>
              <el-button size="small" @click="loadHistoryPanel">📜 历史记录</el-button>
            </template>
            <SqlHistoryPanel ref="historyPanelRef" @fill-sql="onHistoryFill" />
          </el-popover>

          <!-- Snippet dropdown -->
          <el-popover ref="snippetPopoverRef" placement="bottom-end" :width="480" trigger="click" :hide-after="0" popper-class="snippet-popover">
            <template #reference>
              <el-button size="small" @click="loadSnippetPanel">📑 片段</el-button>
            </template>
            <SqlSnippetPanel ref="snippetPanelRef" @fill-sql="onSnippetFill" />
          </el-popover>

          <el-button size="small" @click="saveCurrentSnippet" :disabled="!sqlContent.trim()">💾 保存片段</el-button>

          <el-checkbox v-model="enablePagination" size="small" style="margin-left:8px">分页</el-checkbox>
        </template>
        <template v-else>
          <span class="mode-hint">➕ 新增数据：AI生成INSERT → 填表预览 | 🖊️ 编辑数据：选表 → 筛选行 → 修改字段 → 生成UPDATE</span>
        </template>
      </div>

      <!-- ── SQL MODE ── -->
      <template v-if="mode === 'sql'">
        <div class="tab-bar">
          <div class="tab-list">
            <span
              v-for="tab in tabs"
              :key="tab.id"
              class="tab-item"
              :class="{ active: tab.id === activeTabId }"
              @click="switchTab(tab.id)"
              @dblclick="renameTab(tab)"
            >
              <span class="tab-title">{{ tab.title }}</span>
              <el-button
                v-if="tabs.length > 1"
                link
                size="small"
                class="tab-close"
                @click.stop="closeTab(tab.id)"
              >×</el-button>
            </span>
          </div>
          <el-button link size="small" @click="addTab" class="tab-add">➕</el-button>
        </div>
        <div class="editor-row">
          <div class="editor-area">
            <SqlEditor ref="editorRef" v-model="sqlContent" :schema="schema" @execute="handleExecute" />
          </div>
          <transition name="slide">
            <div v-if="explainVisible" class="explain-panel">
              <div class="explain-header">
                <el-tabs v-model="explainTab" class="explain-tabs">
                  <el-tab-pane label="💡 AI解释" name="ai" />
                  <el-tab-pane label="📊 执行计划" name="plan" />
                </el-tabs>
                <el-button link @click="explainVisible = false" size="small">✕</el-button>
              </div>
              <div class="explain-body" v-loading="explainTab === 'ai' ? explainLoading : explainPlanLoading">
                <template v-if="explainTab === 'ai'">
                  <div v-if="outputEffect" class="explain-section"><div class="explain-label">📊 预测输出效果</div><div class="explain-text">{{ outputEffect }}</div></div>
                  <div v-if="codeStructure" class="explain-section"><div class="explain-label">🔧 代码结构与函数</div><div class="explain-text">{{ codeStructure }}</div></div>
                  <div v-if="!outputEffect && !codeStructure && !explainLoading" class="explain-empty">选中代码或点击 💡 解释代码</div>
                </template>
                <template v-else>
                  <div v-if="explainPlanError" class="explain-error">{{ explainPlanError }}</div>
                  <div v-else-if="explainPlanResult">
                    <div class="explain-section" v-if="explainPlanJson">
                      <div class="explain-label">📋 执行计划 (JSON)</div>
                      <pre class="explain-plan-json">{{ explainPlanJson }}</pre>
                    </div>
                    <div class="explain-section" v-else-if="explainPlanRows?.length">
                      <div class="explain-label">📋 查询计划</div>
                      <el-table :data="explainPlanRows" border size="small" max-height="400">
                        <el-table-column v-for="col in explainPlanCols" :key="col" :label="col" min-width="80">
                          <template #default="{ row }">{{ fmtCell(row[col]) }}</template>
                        </el-table-column>
                      </el-table>
                    </div>
                  </div>
                  <div v-if="!explainPlanResult && !explainPlanLoading && !explainPlanError" class="explain-empty">点击 📊 执行计划 分析当前SQL</div>
                </template>
              </div>
              <div class="explain-actions" v-if="explainTab === 'ai' && outputEffect"><el-button link size="small" @click="addChatMsg('ai', '💡 ' + outputEffect + '\n\n🔧 ' + codeStructure)">→ 发送到对话</el-button></div>
            </div>
          </transition>
        </div>
        <div class="result-area">
          <ResultTable :result="queryResult" :show-pagination="enablePagination" :exporting-all="exportingAll" @page-change="handlePageChange" @export-all="handleExportAll" />
        </div>
      </template>

      <!-- ── FILL MODE ── -->
      <template v-if="mode === 'fill'">
        <div class="fill-area">
          <!-- Sub-mode tabs -->
          <div class="fill-tabs">
            <el-radio-group v-model="fillSubMode" size="small" @change="onFillSubModeChange">
              <el-radio-button value="insert">➕ 新增数据</el-radio-button>
              <el-radio-button value="edit">🖊️ 编辑已有数据</el-radio-button>
            </el-radio-group>
          </div>

          <!-- ═══ INSERT MODE ═══ -->
          <template v-if="fillSubMode === 'insert'">
            <div v-if="!fillSQL" class="fill-empty">
              <p>暂无填表数据</p>
              <span>在右侧对话框描述要插入的数据，AI生成INSERT后点击"→ 填表预览"即可在此编辑并执行</span>
            </div>
            <div v-else class="fill-result">
              <div class="sql-preview">
                <div class="section-title">{{ fillSQL.trim().toUpperCase().startsWith('INSERT') ? '➕ INSERT 填表' : '🔧 UPDATE 填表' }}</div>
                <code :class="fillSQL.trim().toUpperCase().startsWith('INSERT') ? 'insert-sql' : 'update-sql'">{{ fillSQL }}</code>
              </div>
              <div class="fill-actions">
                <el-button type="success" @click="handleExecFillSQL" :loading="fillExecuting">▶ 执行</el-button>
                <el-button @click="clearFill">清空</el-button>
                <el-button type="warning" @click="handleFixFillSQL" :loading="fixing">🤖 AI修复</el-button>
              </div>
              <el-table v-if="fillColumns.length" :data="fillColumns" size="small" border stripe style="margin-top:12px" max-height="350">
                <el-table-column prop="name" label="字段" width="180" />
                <el-table-column prop="type" label="类型" width="130" />
                <el-table-column label="值"><template #default="{row}"><el-input v-model="row.value" size="small" :class="{'ai-filled':row.aiFilled}" /></template></el-table-column>
                <el-table-column label="来源" width="70"><template #default="{row}"><el-tag v-if="row.aiFilled" size="small" type="warning">AI</el-tag><el-tag v-else size="small">手动</el-tag></template></el-table-column>
              </el-table>
              <el-button v-if="fillColumns.length" @click="rebuildFillSQL" size="small" style="margin-top:8px">🔄 根据修改重建SQL</el-button>
            </div>
          </template>

          <!-- ═══ EDIT MODE (行编辑) ═══ -->
          <template v-if="fillSubMode === 'edit'">
            <!-- Step 1: Row Selector -->
            <div class="edit-row-selector">
              <div class="selector-header">
                <span class="selector-title">🔍 选择目标行</span>
                <span class="selector-hint">表: <b>{{ selectedTable || '请先在右侧选择表' }}</b></span>
              </div>

              <!-- Dynamic filters -->
              <div class="edit-filters" v-if="selectedTable">
                <div class="filter-info">
                  <span>筛选条件数:</span>
                  <el-select v-model="editFilterCount" size="small" style="width:80px;margin-left:6px" @change="onFilterCountChange">
                    <el-option v-for="n in 5" :key="n" :label="String(n)" :value="n" />
                  </el-select>
                </div>
                <div v-for="i in editFilterCount" :key="i" class="filter-row">
                  <span class="filter-label">条件 {{ i }}</span>
                  <el-select v-model="editFilters[i-1].column" placeholder="列名" size="small" style="width:150px" filterable @change="(v:string) => onFilterColumnChange(i-1, v)">
                    <el-option v-for="col in editTableColumns" :key="col.name" :label="col.name" :value="col.name" />
                  </el-select>
                  <el-select v-model="editFilters[i-1].value" placeholder="值" size="small" style="flex:1" filterable :loading="editColValuesLoading[i-1]">
                    <el-option v-for="v in (editColumnValues[editFilters[i-1].column] || [])" :key="v" :label="v" :value="v" />
                  </el-select>
                  <el-button v-if="i === editFilterCount && editFilterCount > 1" link size="small" type="danger" @click="editFilterCount--">✕</el-button>
                </div>
                <el-button type="primary" size="small" @click="searchEditRows" :loading="editSearching" :disabled="!selectedTable">
                  🔍 查询匹配行
                </el-button>
                <span v-if="editSearchedRows.length" class="row-count">{{ editSearchedRows.length }} 行匹配</span>
              </div>
              <div v-else class="edit-no-table">
                <span>请先在右侧面板顶部选择一个目标表</span>
              </div>

              <!-- Search results: row list -->
              <div v-if="editSearchedRows.length > 0" class="edit-row-list">
                <div class="row-list-header">
                  <span>匹配的行（点击选择）</span>
                  <span v-if="editPkColumn" style="color:#909399;font-size:12px">PK: {{ editPkColumn }}</span>
                </div>
                <div
                  v-for="(r, idx) in editSearchedRows"
                  :key="idx"
                  :class="['edit-row-item', { active: editRowIndex === idx }]"
                  @click="selectEditRow(idx)"
                >
                  <span class="row-item-num">#{{ idx + 1 }}</span>
                  <span class="row-item-preview">{{ rowPreview(r) }}</span>
                  <span v-if="editPkColumn" class="row-item-pk">{{ r[editPkColumn] }}</span>
                </div>
              </div>
            </div>

            <!-- Step 2: Selected row form + multi-form navigator -->
            <div v-if="editSelectedRow" class="edit-row-form">
              <div class="form-navigator" v-if="editSearchedRows.length > 1">
                <el-button-group size="small">
                  <el-button :disabled="editRowIndex <= 0" @click="selectEditRow(editRowIndex - 1)">◀ 上一行</el-button>
                  <el-button disabled style="min-width:100px">第 {{ editRowIndex + 1 }}/{{ editSearchedRows.length }} 行</el-button>
                  <el-button :disabled="editRowIndex >= editSearchedRows.length - 1" @click="selectEditRow(editRowIndex + 1)">下一行 ▶</el-button>
                </el-button-group>
                <el-tag v-if="editPkColumn" size="small" type="info">PK: {{ editPkColumn }} = {{ editSelectedRow[editPkColumn] }}</el-tag>
              </div>

              <!-- SQL Preview for UPDATE -->
              <div class="sql-preview" v-if="editSQL">
                <div class="section-title">🔧 UPDATE 语句</div>
                <code class="update-sql">{{ editSQL }}</code>
              </div>

              <div class="fill-actions">
                <el-button type="success" @click="handleExecEditSQL" :loading="fillExecuting">▶ 执行UPDATE</el-button>
                <el-button @click="resetEditForm">↩ 撤销修改</el-button>
                <el-button type="warning" @click="handleAICompleteEdit" :loading="editAIWorking">🤖 AI补全空值</el-button>
                <el-button @click="clearEditMode">清空</el-button>
              </div>

              <!-- Four-color field table -->
              <el-table v-if="editColumns.length" :data="editColumns" size="small" border stripe style="margin-top:12px" max-height="400">
                <el-table-column label="状态" width="56">
                  <template #default="{row}">
                    <span v-if="row.pk" title="主键·只读">🔒</span>
                    <span v-else-if="row.modified" title="已修改">✏️</span>
                    <span v-else-if="row.wasNull" title="未填写">⚠️</span>
                    <span v-else-if="row.aiSuggested" title="AI建议">🤖</span>
                    <span v-else title="已有值">✅</span>
                  </template>
                </el-table-column>
                <el-table-column prop="name" label="字段" width="170" />
                <el-table-column prop="type" label="类型" width="120" />
                <el-table-column label="值">
                  <template #default="{row}">
                    <ComplexTypePopover v-if="row.value && isComplexCell(row.value) && !row.modified" :value="row.value" :column-name="row.name" />
                    <el-input
                      v-else
                      v-model="row.value"
                      size="small"
                      :disabled="row.pk"
                      :class="editFieldClass(row)"
                      :placeholder="row.wasNull ? '(未填写)' : ''"
                      @input="onEditFieldChange(row)"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="80">
                  <template #default="{row}">
                    <el-tag v-if="row.pk" size="small" type="info">🔒 主键</el-tag>
                    <el-tag v-else-if="row.modified" size="small" type="warning">✏️ 已修改</el-tag>
                    <el-tag v-else-if="row.wasNull" size="small" type="danger">⚠️ 未填写</el-tag>
                    <el-tag v-else-if="row.aiSuggested" size="small" type="success">🤖 AI建议</el-tag>
                    <el-tag v-else size="small">✅ 已有值</el-tag>
                  </template>
                </el-table-column>
              </el-table>
              <el-button v-if="editColumns.length && hasEditChanges" @click="rebuildEditSQL" size="small" style="margin-top:8px" type="primary">🔄 根据修改重建SQL</el-button>
            </div>
            <div v-else-if="editSearchedRows.length" class="edit-no-row-selected">
              <span>👆 请点击上方一行以查看/编辑其数据</span>
            </div>
          </template>
        </div>
      </template>
    </div>

    <!-- RIGHT PANEL: Chat + Table Selector -->
    <div class="right-panel">
      <div class="chat-header">
        <el-select v-model="currentConvId" placeholder="对话" style="flex:1" clearable filterable @change="onConvSelect" @clear="newConversation">
          <el-option v-for="c in conversations" :key="c.conversationId" :label="c.title" :value="c.conversationId" />
        </el-select>
        <el-button @click="newConversation" size="small" type="primary" style="margin-left:6px">+</el-button>
        <el-button @click="showSettings = true" size="small" :icon="Setting" circle style="margin-left:4px" />
        <el-button v-if="currentConvId" @click="handleDeleteConv(currentConvId)" size="small" type="danger" :icon="Delete" circle style="margin-left:4px" />
      </div>
      <div v-if="chatMessages.length >= 45" class="context-warning">
        ⚠️ 对话上下文已接近上限（{{ chatMessages.length }}/50条），超出部分将不被保留
      </div>
      <div class="table-selector">
        <el-select v-model="selectedTable" placeholder="不选=全库操作" clearable filterable size="small" style="width:100%">
          <el-option v-for="t in tables" :key="t.name" :label="t.name + ' ('+ (t.columns?.length||0) +'列)'" :value="t.name" />
        </el-select>
      </div>
      <div class="chat-messages" ref="chatRef">
        <div v-for="msg in chatMessages" :key="msg.id" :class="['msg', msg.type]">
          <div class="msg-head"><span class="msg-icon">{{ msgIcon(msg.type) }}</span><span class="msg-role">{{ msgRole(msg.type) }}</span><span class="msg-time">{{ msg.time }}</span></div>
          <div class="msg-body">
            <code v-if="msg.type === 'ai'" class="sql-code">{{ msg.content }}</code>
            <span v-else>{{ msg.content }}</span>
          </div>
          <div class="msg-actions">
            <el-button v-if="msg.type === 'ai'" link size="small" @click="applyToEditor(msg.content)">→ 填入编辑器</el-button>
            <el-button v-if="msg.type === 'ai'" link size="small" @click="execFromChat(msg.content)">▶ 直接执行</el-button>
            <el-button v-if="msg.type === 'ai' && isFillSQL(msg.content)" link size="small" type="success" @click="previewFill(msg.content)">→ 填表预览</el-button>
            <el-button v-if="msg.type === 'ai'" link size="small" type="primary" @click="openSuggestion(msg)">✋ 人工建议</el-button>
            <el-button v-if="msg.type === 'db-error' && msg.fixable" link size="small" type="warning" @click="handleFixSQL(msg.content)">🤖 AI修复</el-button>
          </div>
        </div>
      </div>

      <!-- Thinking block pinned above input -->
      <div v-if="thinkingBlock.visible" class="thinking-block">
        <div class="thinking-header" @click="thinkingBlock.collapsed = !thinkingBlock.collapsed">
          <span>{{ thinkingBlock.collapsed ? '▶' : '▼' }} 🧠 AI思考与自检</span>
          <div style="margin-left:auto;display:flex;gap:6px;align-items:center">
            <el-tag v-if="thinkingBlock.loopStatus" size="small" :type="thinkingBlock.loopOk ? 'success' : 'warning'">{{ thinkingBlock.loopStatus }}</el-tag>
            <span v-if="thinkingBlock.progress" style="font-size:11px;color:#909399">{{ thinkingBlock.progress }}</span>
          </div>
        </div>
        <div v-if="!thinkingBlock.collapsed" class="thinking-body" ref="thinkingBodyRef">
          <div v-for="(seg, i) in thinkingBlock.segments" :key="i" :class="['think-seg', seg.type]">
            <span class="seg-icon">{{ seg.type === 'loop' ? '🔄' : seg.type === 'pass' ? '✅' : seg.type === 'fail' ? '❌' : seg.type === 'fixed' ? '🔧' : '' }}</span>
            <span class="seg-text">{{ seg.text }}</span>
          </div>
        </div>
      </div>

      <!-- Human suggestion panel (pinned above input) -->
      <div v-if="suggestionTarget" class="suggestion-panel">
        <div class="suggestion-header">
          <span>✋ 人工建议 — 针对以下SQL提出修改意见</span>
          <el-button link @click="cancelSuggestion" size="small">✕</el-button>
        </div>
        <code class="suggestion-sql">{{ suggestionTarget.content.substring(0, 300) }}{{ suggestionTarget.content.length > 300 ? '...' : '' }}</code>
        <div class="suggestion-input-row">
          <el-input v-model="suggestionInput" placeholder="描述你需要如何修改这段代码...&#10;例如：把LIMIT改成50，加上WHERE条件过滤type=3的数据" type="textarea" :rows="3" size="small" @keyup.enter.ctrl="submitSuggestion" />
          <div class="suggestion-actions">
            <el-button size="small" @click="cancelSuggestion">取消</el-button>
            <el-button size="small" type="primary" @click="submitSuggestion" :loading="suggestionBusy" :disabled="!suggestionInput.trim()">发送建议</el-button>
          </div>
        </div>
      </div>

      <div class="chat-input">
        <el-input v-model="chatInput" :placeholder="chatPlaceholder" @keyup.enter="sendChat" :disabled="chatBusy" size="small" />
        <el-button type="primary" @click="sendChat" :loading="chatBusy" size="small" style="margin-left:6px">发送</el-button>
      </div>
    </div>

    <el-dialog v-model="showWarning" title="⚠️ 危险操作" width="420px">
      <p style="color:#e6a23c;margin-bottom:12px">{{ warningMessage }}</p>
      <template #footer>
        <el-button @click="showWarning = false">取消</el-button>
        <el-button type="danger" @click="confirmExecute">确认执行</el-button>
      </template>
    </el-dialog>

    <!-- Settings dialog -->
    <el-dialog v-model="showSettings" title="⚙️ 对话设置" width="520px">
      <el-form label-width="100px">
        <el-form-item label="项目背景（长期记忆）">
          <el-input v-model="projectBackground" type="textarea" :rows="6"
            placeholder="描述你的项目背景，AI会在每次对话中记住这些信息。例如：&#10;这是一个机床设计数据库，包含machine_case（机床案例）、axis_operating_parameters（轴参数）等表。machine_type表存储机床类型分类。所有表通过case_id关联到machine_case。&#10;&#10;常用查询场景：按机床类型统计、查找特定规格的轴参数、分析不同案例的配置差异。"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleSaveBackground" :loading="savingBg" type="primary">保存</el-button>
        <el-button @click="showSettings = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- Save Snippet dialog -->
    <el-dialog v-model="saveSnippetVisible" title="💾 保存SQL片段" width="520px" :close-on-click-modal="false">
      <div style="margin-bottom:12px;font-size:13px;color:#909399">保存当前SQL为可复用片段，之后在「📑 片段」面板中一键填入编辑器</div>
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="saveSnippetForm.title" placeholder="片段名称，如：用户订单查询" maxlength="200" />
        </el-form-item>
        <el-form-item label="SQL">
          <el-input v-model="saveSnippetForm.sqlContent" type="textarea" :rows="6" placeholder="SQL语句" />
        </el-form-item>
        <el-form-item label="描述（可选）">
          <el-input v-model="saveSnippetForm.description" placeholder="简短说明此SQL的用途" maxlength="500" />
        </el-form-item>
        <el-form-item label="标签（可选）">
          <el-input v-model="saveSnippetForm.tags" placeholder="逗号分隔，如：查询,报表,用户" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="saveSnippetVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveSnippet" :loading="savingSnippet">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CaretRight, Delete, Setting } from '@element-plus/icons-vue'
import SqlEditor from '../components/SqlEditor.vue'
import ResultTable from '../components/ResultTable.vue'
import SqlHistoryPanel from '../components/SqlHistoryPanel.vue'
import SqlSnippetPanel from '../components/SqlSnippetPanel.vue'
import ComplexTypePopover from '../components/ComplexTypePopover.vue'
import { executeSQL as execSQL, getColumnValues, searchRows, createSnippet, explainSQL } from '../api/sql'
import { aiConfigAPI } from '../api/ai-config'
import { conversationAPI } from '../api/ai-conversation'
import * as XLSX from 'xlsx'
import { useConnectionStore } from '../stores/connection'
import type { ConversationInfo } from '../api/ai-conversation'

const store = useConnectionStore()
const editorRef = ref<InstanceType<typeof SqlEditor>>()
const historyPanelRef = ref<InstanceType<typeof SqlHistoryPanel>>()
const snippetPanelRef = ref<InstanceType<typeof SqlSnippetPanel>>()
const snippetPopoverRef = ref<InstanceType<typeof import('element-plus').Popover>>()

// Save snippet standalone dialog
const saveSnippetVisible = ref(false)
const savingSnippet = ref(false)
const saveSnippetForm = ref({ title: '', sqlContent: '', description: '', tags: '' })
const thinkingBodyRef = ref<HTMLDivElement>()

// ── Streaming thinking block ──
const thinkingBlock = reactive<{
  visible: boolean; collapsed: boolean; loopStatus: string; loopOk: boolean; progress: string;
  segments: { type: string; text: string }[]
}>({ visible: false, collapsed: false, loopStatus: '', loopOk: false, progress: '', segments: [] })

function resetThinking() { thinkingBlock.visible = true; thinkingBlock.collapsed = false; thinkingBlock.loopStatus = '生成中...'; thinkingBlock.loopOk = false; thinkingBlock.progress = ''; thinkingBlock.segments = [] }
function addSegment(type: string, text: string) { thinkingBlock.segments.push({ type, text }); nextTick(() => { if (thinkingBodyRef.value) thinkingBodyRef.value.scrollTop = thinkingBodyRef.value.scrollHeight }) }
function finishThinking(resultOk: boolean) { thinkingBlock.loopStatus = resultOk ? '通过' : '需关注'; thinkingBlock.loopOk = resultOk; thinkingBlock.progress = ''; setTimeout(() => { thinkingBlock.visible = false; thinkingBlock.segments = [] }, resultOk ? 3000 : 0) }

// ── Mode ──
type Mode = 'sql' | 'fill'
const mode = ref<Mode>('sql')

// ── Tab state ──
interface Tab { id: number; title: string; sqlContent: string; queryResult: any; sqlSource: string; originalAiSQL: string }
let nextTabId = 1
const tabs = ref<Tab[]>([{ id: nextTabId++, title: 'Query 1', sqlContent: '', queryResult: null, sqlSource: 'manual', originalAiSQL: '' }])
const activeTabId = ref(tabs.value[0].id)
const activeTab = computed(() => tabs.value.find(t => t.id === activeTabId.value) ?? tabs.value[0])

// Computed proxies — all existing code uses these unchanged
const sqlContent = computed({
  get: () => activeTab.value.sqlContent,
  set: (v) => { activeTab.value.sqlContent = v }
})
const sqlSource = computed({
  get: () => activeTab.value.sqlSource as 'manual' | 'ai_generated' | 'human_ai_collab',
  set: (v) => { activeTab.value.sqlSource = v }
})
const originalAiSQL = computed({
  get: () => activeTab.value.originalAiSQL,
  set: (v) => { activeTab.value.originalAiSQL = v }
})
const queryResult = computed({
  get: () => activeTab.value.queryResult,
  set: (v) => { activeTab.value.queryResult = v }
})

const schema = ref<any>(null)
const executing = ref(false)
const fixing = ref(false)
const enablePagination = ref(true)
const currentPage = ref(1)
const currentPageSize = ref(100)
const exportingAll = ref(false)
const showWarning = ref(false)
const warningMessage = ref('')
const showSettings = ref(false)
const projectBackground = ref('')
const savingBg = ref(false)

// ── Explain state ──
const explainVisible = ref(false)
const explainTab = ref<'ai' | 'plan'>('ai')
const explainLoading = ref(false)
const outputEffect = ref('')
const codeStructure = ref('')
const explainPlanLoading = ref(false)
const explainPlanResult = ref<any>(null)
const explainPlanJson = ref('')
const explainPlanRows = ref<Record<string,any>[]>([])
const explainPlanCols = ref<string[]>([])
const explainPlanError = ref('')

// ── Fill preview state ──
const fillSubMode = ref<'insert' | 'edit'>('insert')
const fillSQL = ref('')
const fillExecuting = ref(false)
interface ColRow { name: string; type: string; value: string; aiFilled: boolean; pk?: boolean; wasNull?: boolean; modified?: boolean; aiSuggested?: boolean; originalValue?: string }
const fillColumns = ref<ColRow[]>([])

// ── Edit mode state ──
const editFilterCount = ref(1)
const editFilters = ref<{ column: string; value: string }[]>(Array.from({length:5}, () => ({column:'',value:''})))
const editColumnValues = ref<Record<string,string[]>>({})
const editColValuesLoading = ref<boolean[]>(Array.from({length:5}, () => false))
const editSearchedRows = ref<Record<string,any>[]>([])
const editRowIndex = ref(0)
const editPkColumn = ref('')
const editColumns = ref<ColRow[]>([])
const editSQL = ref('')
const editSearching = ref(false)
const editAIWorking = ref(false)
const editTableColumns = computed(() => {
  const t = tables.value.find(x => x.name === selectedTable.value)
  return t?.columns || []
})

// ── Tables ──
const tables = ref<any[]>([])
const selectedTable = ref('')

// ── Chat state ──
interface ChatMsg { id: number; type: 'user' | 'ai' | 'db-status' | 'db-error'; content: string; time: string; fixable?: boolean }
const chatMessages = ref<ChatMsg[]>([])
const chatInput = ref('')
const chatBusy = ref(false)
const chatRef = ref<HTMLDivElement>()
const conversations = ref<ConversationInfo[]>([])
const currentConvId = ref<string | null>(null)

// ── Human Suggestion state ──
const suggestionTarget = ref<ChatMsg | null>(null)
const suggestionInput = ref('')
const suggestionBusy = ref(false)

function openSuggestion(msg: ChatMsg) {
  suggestionTarget.value = msg
  suggestionInput.value = ''
  // Scroll suggestion panel into view
  nextTick(() => { if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight })
}
function cancelSuggestion() { suggestionTarget.value = null; suggestionInput.value = '' }
async function submitSuggestion() {
  if (!suggestionInput.value.trim() || !suggestionTarget.value || !store.activeConnection) return
  if (!currentConvId.value) newConversation()

  const targetSQL = suggestionTarget.value.content
  const humanAdvice = suggestionInput.value.trim()
  const intent = detectIntent(humanAdvice)
  const prompt = buildChatPrompt(
    `人工建议修改SQL:\n原始SQL:\n${targetSQL}\n\n修改需求:\n${humanAdvice}\n\n请根据以上人工建议和对话上下文，重新生成修改后的SQL。`,
    intent === 'general' ? 'modify' : intent,
    'human_suggestion'
  )

  suggestionBusy.value = true
  addChatMsg('user', `✋ 人工建议: ${humanAdvice}`)
  saveChatMessage('user', prompt)
  cancelSuggestion()

  resetThinking()
  thinkingBlock.progress = 'Agent 处理人工建议'

  aiConfigAPI.agentChat(
    store.activeConnection.id, prompt,
    selectedTable.value || undefined,
    currentConvId.value || undefined,
    'human_suggestion', intent,
    (type, data) => handleAgentEvent(type, data),
    (err) => {
      if (err.name !== 'AbortError') { addSegment('fail', '人工建议处理失败: ' + err.message); addChatMsg('db-error', 'Agent: ' + err.message) }
      finishThinking(false); suggestionBusy.value = false; chatBusy.value = false
    },
    (sql) => {
      addSegment('pass', '人工建议已处理')
      finishThinking(true); suggestionBusy.value = false; chatBusy.value = false
    }
  )
}

const chatPlaceholder = computed(() => selectedTable.value
  ? `对表 "${selectedTable.value}" 操作...`
  : '描述你想做什么...')

const now = () => new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
const msgIcon = (t: string) => ({ user: '👤', ai: '🤖', 'db-status': '🗄️', 'db-error': '⚠️' }[t] || '')
const msgRole = (t: string) => ({ user: '你', ai: 'AI', 'db-status': '数据库', 'db-error': '报错' }[t] || '')

function addChatMsg(type: ChatMsg['type'], content: string, fixable = false) {
  chatMessages.value.push({ id: Date.now(), type, content, time: now(), fixable })
  nextTick(() => { if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight })
}

function isFillSQL(sql: string): boolean {
  const s = sql.trim().toUpperCase()
  return s.startsWith('INSERT') || s.startsWith('UPDATE')
}

// ── Conversations ──
async function loadConversations() { try { const r = await conversationAPI.listConversations() as any; conversations.value = r.conversations || [] } catch {} }
async function restoreChatHistory(convId: string) {
  try {
    const r = await conversationAPI.getMessages(convId) as any
    chatMessages.value = (r.messages || []).map((m: any) => ({ id: m.id, time: new Date(m.createdAt).toLocaleTimeString('zh-CN', { hour:'2-digit', minute:'2-digit', second:'2-digit' }), type: m.messageType === 'ai' ? 'ai' : 'user' as ChatMsg['type'], content: m.messageContent }))
  } catch { chatMessages.value = [] }
}
function onConvSelect(id: string) { if (id) restoreChatHistory(id) }
function newConversation() { const id = 'conv_' + Date.now() + '_' + Math.random().toString(36).slice(2,8); currentConvId.value = id; chatMessages.value = []; conversations.value.unshift({ conversationId: id, title: '新对话', messageCount: 0, createdAt: new Date().toISOString() }) }
async function handleDeleteConv(id: string) { try { await ElMessageBox.confirm('删除？', '提示'); await conversationAPI.deleteConversation(id); if (currentConvId.value === id) { currentConvId.value = null; chatMessages.value = [] }; conversations.value = conversations.value.filter(c => c.conversationId !== id) } catch {} }
async function saveChatMessage(type: string, content: string) {
  if (!currentConvId.value || !store.activeConnection) return
  try { await conversationAPI.saveMessage(currentConvId.value, store.activeConnection.id, type, content) } catch {}
  const conv = conversations.value.find(c => c.conversationId === currentConvId.value)
  if (conv) { conv.messageCount = chatMessages.value.length; if (type === 'user') conv.title = content.slice(0, 30) }
}

// ── SQL Execution ──
async function handleExecute() {
  if (!store.activeConnection) { ElMessage.warning('请先选择数据库连接'); return }
  if (!sqlContent.value.trim()) { ElMessage.warning('请输入SQL'); return }

  // 检测是否为人机协作（AI生成后用户修改）
  if (sqlSource.value === 'ai_generated' && originalAiSQL.value && sqlContent.value.trim() !== originalAiSQL.value.trim()) {
    sqlSource.value = 'human_ai_collab'
  }

  executing.value = true
  try {
    const params: any = { connectionId: store.activeConnection.id, sql: sqlContent.value.trim(), confirmed: false, source: sqlSource.value }
    if (enablePagination.value) { params.page = currentPage.value; params.pageSize = currentPageSize.value }
    const result = await execSQL(params)
    if (result.isDangerous) { warningMessage.value = result.warningMessage; showWarning.value = true; executing.value = false; return }
    queryResult.value = result
    const info = result.columns ? `✅ 查询成功 · ${result.total ?? result.rows?.length} 行 · ${result.executionTime}ms` : `✅ 执行成功 · 影响 ${result.affectedRows ?? 0} 行 · ${result.executionTime}ms`
    addChatMsg('db-status', info); saveChatMessage('db-status', info)

    // 执行成功后重置为 manual（下次输入视为手动）
    sqlSource.value = 'manual'
    originalAiSQL.value = ''
  } catch (error: any) { addChatMsg('db-error', error.response?.data?.error || error.message || '执行失败', true); saveChatMessage('db-error', error.response?.data?.error || '执行失败') }
  finally { executing.value = false }
}
async function confirmExecute() {
  showWarning.value = false; executing.value = true
  try {
    const r = await execSQL({ connectionId: store.activeConnection!.id, sql: sqlContent.value.trim(), confirmed: true, source: sqlSource.value });
    queryResult.value = r;
    addChatMsg('db-status', `✅ 已执行 · ${r.affectedRows ?? 0} 行`)
    sqlSource.value = 'manual'
    originalAiSQL.value = ''
  }
  catch (e: any) { addChatMsg('db-error', e.response?.data?.error || e.message || '失败') }
  finally { executing.value = false }
}
function handlePageChange(p: number, s: number) { currentPage.value = p; currentPageSize.value = s; handleExecute() }
function fmtCell(v: any): string {
  if (v === null || v === undefined) return '(NULL)'
  if (typeof v === 'object') return JSON.stringify(v)
  return String(v)
}

function handleClear() { sqlContent.value = ''; queryResult.value = null; sqlSource.value = 'manual'; originalAiSQL.value = '' }

// ── Tab Management ──
function addTab() {
  const id = nextTabId++
  const n = tabs.value.length + 1
  tabs.value.push({ id, title: `Query ${n}`, sqlContent: '', queryResult: null, sqlSource: 'manual', originalAiSQL: '' })
  activeTabId.value = id
}
function switchTab(id: number) {
  activeTabId.value = id
  // Reset pagination for the new tab
  currentPage.value = 1
}
function closeTab(id: number) {
  if (tabs.value.length <= 1) return
  const idx = tabs.value.findIndex(t => t.id === id)
  tabs.value.splice(idx, 1)
  if (activeTabId.value === id) {
    activeTabId.value = tabs.value[Math.min(idx, tabs.value.length - 1)].id
  }
}
function renameTab(tab: Tab) {
  // Simple inline rename via prompt
  const name = window.prompt('重命名标签：', tab.title)
  if (name && name.trim()) {
    tab.title = name.trim()
  }
}

async function handleExportAll() {
  if (!store.activeConnection || !sqlContent.value.trim()) return
  exportingAll.value = true
  try {
    const params: any = { connectionId: store.activeConnection.id, sql: sqlContent.value.trim(), confirmed: true, source: sqlSource.value }
    const result = await execSQL(params)
    if (!result?.columns || !result?.rows?.length) { ElMessage.warning('无数据可导出'); return }

    const columns = result.columns as string[]
    const rows = result.rows as Record<string, any>[]
    const data: any[][] = [columns]
    for (const row of rows) {
      data.push(columns.map(c => {
        const v = row[c]
        if (v === null || v === undefined) return ''
        if (typeof v === 'object') return JSON.stringify(v)
        return String(v)
      }))
    }
    const ws = XLSX.utils.aoa_to_sheet(data)
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, 'Sheet1')
    XLSX.writeFile(wb, `query_export_all_${Date.now()}.xlsx`)
    ElMessage.success(`导出 ${rows.length} 行`)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error || e.message || '导出失败')
  } finally { exportingAll.value = false }
}

// ── AI Explain ──
async function handleExplainCode() {
  if (!store.activeConnection || !sqlContent.value.trim()) { ElMessage.warning('请输入SQL'); return }
  const selected = editorRef.value?.getSelectedText(); const code = (selected && selected.trim()) || sqlContent.value.trim()
  explainVisible.value = true; explainTab.value = 'ai'; explainLoading.value = true; outputEffect.value = ''; codeStructure.value = ''
  try { const res = await aiConfigAPI.explainSQL(store.activeConnection.id, code) as any; outputEffect.value = res.outputEffect || ''; codeStructure.value = res.codeStructure || '' } catch (e: any) { ElMessage.error(e.response?.data?.message || '失败') } finally { explainLoading.value = false }
}

async function handleExplainPlan() {
  if (!store.activeConnection || !sqlContent.value.trim()) { ElMessage.warning('请输入SQL'); return }
  explainVisible.value = true; explainTab.value = 'plan'; explainPlanLoading.value = true
  explainPlanResult.value = null; explainPlanJson.value = ''; explainPlanRows.value = []; explainPlanCols.value = []; explainPlanError.value = ''
  try {
    const res = await explainSQL(store.activeConnection.id, sqlContent.value.trim()) as any
    explainPlanResult.value = res
    if (res?.columns && res?.rows) {
      // PostgreSQL FORMAT JSON: single row with JSON plan
      if (res.columns.length === 1 && res.rows.length === 1) {
        const val = res.rows[0][res.columns[0]]
        if (typeof val === 'string' && (val.startsWith('[') || val.startsWith('{'))) {
          try {
            explainPlanJson.value = JSON.stringify(JSON.parse(val), null, 2)
          } catch { explainPlanJson.value = val }
        } else if (typeof val === 'object') {
          explainPlanJson.value = JSON.stringify(val, null, 2)
        } else {
          explainPlanRows.value = res.rows; explainPlanCols.value = res.columns
        }
      } else {
        explainPlanRows.value = res.rows; explainPlanCols.value = res.columns
      }
    }
  } catch (e: any) {
    explainPlanError.value = e.response?.data?.error || e.message || '执行计划获取失败'
  } finally { explainPlanLoading.value = false }
}

// ── Agent Event Handler (shared) ──
function handleAgentEvent(type: string, data: any) {
  switch (type) {
    case 'agent_phase':
      addSegment('loop', `📋 ${data.phase==='plan'?'Phase1-规划':data.phase==='execute'?'Phase2-并行执行':data.phase==='validate'?'Phase3-代码校验':data.phase==='review'?'Phase4-AI校核':data.phase}: ${data.msg}`)
      thinkingBlock.progress = data.msg; break
    case 'plan_result':
      addSegment('loop', '规划完成: ' + (data.plan || '')); break
    case 'wave_start':
      addSegment('loop', `⚡ 并行执行 ${data.count} 个无依赖任务 (wave ${data.wave}/${data.total})`); break
    case 'task_start':
      addSegment('loop', `🔧 Step${data.step}/${data.total} [${data.type}]: ${data.desc}`);
      if (data.table) addSegment('loop', `  目标表: ${data.table}`); break
    case 'tool_call':
      addSegment('loop', `  ⚡ ${data.tool}(${JSON.stringify(data.params)})`); break
    case 'tool_result':
      addSegment('loop', `  ✅ ${data.tool} (${data.elapsed}ms)`); break
    case 'task_sql': {
      const issuesNote = data.validated === false ? ` ⚠️${data.issues||''}` : ''
      addChatMsg('ai', `${data.desc?.substring(0,60)}${issuesNote}`)
      addChatMsg('ai', data.sql); saveChatMessage('ai', data.sql)
      addSegment(data.validated!==false?'pass':'fail', `✅ Step${data.step}: ${data.desc?.substring(0,40)}...${issuesNote}`); break
    }
    case 'task_error':
      addSegment('fail', `❌ Step${data.step}: ${data.error}`); break
    case 'validate_result':
      if (data.pass !== false) addSegment('pass', '✅ 代码校验通过');
      else addSegment('fail', `⚠️ 代码校验: ${data.issues}`); break
    case 'review_result': {
      const r = data; const ok = r.overall !== false
      addSegment(ok?'pass':'fail', ok?'🔍 AI校核通过':'🔍 AI校核需关注')
      if (r.format) addSegment(r.format.pass!==false?'pass':'fail',`  格式: ${r.format.reason||''}`)
      if (r.logic) addSegment(r.logic.pass!==false?'pass':'fail',`  逻辑: ${r.logic.reason||''}`)
      if (r.safety) addSegment(r.safety.pass!==false?'pass':'fail',`  安全: ${r.safety.reason||''}`); break
    }
    case 'agent_done':
      addSegment('pass', `🎯 完成: ${data.steps}步骤, ${data.tool_calls}次工具, 校验${data.code_ok!==false?'✅':'⚠️'}, 校核${data.review_passed!==false?'✅':'⚠️'}`); break
  }
}

// ── Intent & Operation Types ──
type OperationType = 'normal' | 'error_fix' | 'human_suggestion' | 'table_targeted'
type IntentType = 'query' | 'insert' | 'update' | 'delete' | 'modify' | 'general'

function detectIntent(msg: string): IntentType {
  const m = msg.toLowerCase()
  if (/insert|into\s+values|插入|添加|新增|写入|填入|补充/.test(m)) return 'insert'
  if (/update|set\s|更新|修改|改[成为]|填充|null|设为/.test(m)) return 'update'
  if (/delete|drop|truncate|删除|移除|清[空除]/.test(m)) return 'delete'
  if (/explain|解释|explain|优化|修复|fix|重构|rewrite|改写|调整/.test(m)) return 'modify'
  if (/select|查询|查看|显示|列出|统计|计数|count|找出|搜索/.test(m)) return 'query'
  return 'general'
}

function getOperationType(): OperationType {
  return selectedTable.value ? 'table_targeted' : 'normal'
}

function buildTableSnapshot(): string {
  const table = tables.value.find(t => t.name === selectedTable.value)
  if (!table) return ''
  const cols = (table.columns || []).map((c: any) => {
    const name = typeof c === 'string' ? c : c.name
    const type = typeof c === 'string' ? 'TEXT' : (c.type || 'TEXT')
    const pk = c.pk ? ' PK' : ''
    const nullable = c.nullable === false ? ' NOT NULL' : ''
    return `  ${name}: ${type}${pk}${nullable}`
  }).join('\n')
  return `## 目标表 Schema: ${selectedTable.value}\n${cols || '  (no columns)'}\n## 目标表: ${selectedTable.value}\n`
}

function buildChatPrompt(msg: string, intent: IntentType, opType: OperationType): string {
  const parts: string[] = []

  // ── Layer 1: Operation context header ──
  const opLabels: Record<OperationType, string> = {
    normal: '', table_targeted: `[指定表操作: ${selectedTable.value}]`, error_fix: '[报错修复]', human_suggestion: '[人工建议]'
  }
  if (opLabels[opType]) parts.push(opLabels[opType])

  // ── Layer 1.5: Table snapshot for table_targeted (skip agent explore step) ──
  if (opType === 'table_targeted') {
    const snap = buildTableSnapshot()
    if (snap) parts.push(snap)
  }

  // ── Layer 2: Intent-specific guidance ──
  switch (intent) {
    case 'insert':
      parts.push('## 意图: 插入数据 (INSERT)')
      parts.push('请先探索表结构和现有数据，再生成INSERT。跳过自增主键列。仅生成纯SQL。')
      break
    case 'update':
      parts.push('## 意图: 更新数据 (UPDATE)')
      parts.push('请先探索表结构和现有数据（确定WHERE条件），再生成UPDATE。必须有WHERE子句。仅生成纯SQL。')
      break
    case 'delete':
      parts.push('## 意图: 删除数据 (DELETE)')
      parts.push('请先探索表结构和现有数据（必须先看到数据再决定删除范围），再生成DELETE。必须有WHERE子句。仅生成纯SQL。')
      break
    case 'query':
      parts.push('## 意图: 查询数据 (SELECT)')
      parts.push('可跳过探索，直接生成SELECT。仅生成纯SQL。')
      break
    case 'modify':
      parts.push('## 意图: 修改/优化现有SQL')
      parts.push('请分析原有SQL的问题，基于表结构生成修正版。仅生成纯SQL。')
      break
    default:
      parts.push('## 意图: 通用操作')
      parts.push('请先识别用户具体需求（查询/插入/更新/删除），再执行对应策略。')
  }

  // ── Layer 3: Conversation history context ──
  const historySize = intent === 'query' || intent === 'general' ? 10 : 20
  const relevantMsgs = chatMessages.value.slice(-historySize)
  if (relevantMsgs.length > 0) {
    parts.push('## 对话历史 (最近' + historySize + '条)')
    relevantMsgs.forEach(m => {
      const roleTag = m.type === 'user' ? 'User' : m.type === 'ai' ? 'AI' : m.type === 'db-error' ? 'Error' : 'DB'
      const snippet = m.content.length > 300 ? m.content.substring(0, 300) + '...' : m.content
      parts.push(`[${roleTag}] ${snippet}`)
    })
  }

  // ── Layer 4: Current request ──
  parts.push('## 当前请求')
  parts.push(msg)

  return parts.join('\n\n')
}

// ── AI Chat ──
async function sendChat() {
  if (!chatInput.value.trim()) return
  if (!store.activeConnection) { ElMessage.warning('请先选择数据库连接'); return }
  if (!currentConvId.value) newConversation()

  const msg = chatInput.value.trim(); chatInput.value = ''; chatBusy.value = true
  const intent = detectIntent(msg)
  const opType = getOperationType()

  addChatMsg('user', msg); saveChatMessage('user', msg)
  if (chatMessages.value.length > 50) addChatMsg('db-status', '⚠️ 超过50条，仅保留最近50条')

  const prompt = buildChatPrompt(msg, intent, opType)
  console.log('[Chat] intent=' + intent + ' opType=' + opType + ' promptLen=' + prompt.length)

  // Start Agent
  resetThinking()
  thinkingBlock.progress = 'Agent 启动 (' + intent + ')'

  aiConfigAPI.agentChat(
    store.activeConnection.id, prompt,
    selectedTable.value || undefined,
    currentConvId.value || undefined,
    intent, opType,
    (type, data) => handleAgentEvent(type, data), (err) => {
      if (err.name !== 'AbortError') { addSegment('fail', 'Agent失败: ' + err.message); addChatMsg('db-error', 'Agent: ' + err.message) }
      finishThinking(false); chatBusy.value = false
    },
    (sql) => {
      addSegment('pass', `Agent输出完成`)
      finishThinking(true); chatBusy.value = false
    }
  )
}

function applyToEditor(sql: string) {
  mode.value = 'sql'
  sqlContent.value = sql
  sqlSource.value = 'ai_generated'
  originalAiSQL.value = sql
  ElMessage.success('已填入编辑器')
}

// ── History panel ──
function loadHistoryPanel() {
  // trigger refresh when popover opens
  if (historyPanelRef.value) {
    historyPanelRef.value.loadHistory()
  }
}
function onHistoryFill(sql: string) {
  mode.value = 'sql'
  sqlContent.value = sql
  sqlSource.value = 'manual'
  originalAiSQL.value = ''
  ElMessage.success('已回填历史SQL到编辑器')
}

function loadSnippetPanel() {
  if (snippetPanelRef.value) {
    snippetPanelRef.value.loadSnippets()
  }
}
function onSnippetFill(sql: string) {
  mode.value = 'sql'
  sqlContent.value = sql
  sqlSource.value = 'manual'
  originalAiSQL.value = ''
}
function saveCurrentSnippet() {
  saveSnippetForm.value = { title: '', sqlContent: sqlContent.value, description: '', tags: '' }
  saveSnippetVisible.value = true
}

async function handleSaveSnippet() {
  if (!saveSnippetForm.value.title.trim()) { ElMessage.warning('请输入标题'); return }
  if (!saveSnippetForm.value.sqlContent.trim()) { ElMessage.warning('请输入SQL'); return }
  savingSnippet.value = true
  try {
    await createSnippet(saveSnippetForm.value)
    ElMessage.success('片段已保存，可在「📑 片段」面板查看')
    saveSnippetVisible.value = false
  } catch (e: any) {
    const msg = e.response?.data?.error || e.response?.data?.message || e.message || '保存失败'
    ElMessage.error('保存失败：' + msg)
  } finally { savingSnippet.value = false }
}

async function execFromChat(sql: string) {
  mode.value = 'sql'
  sqlContent.value = sql
  sqlSource.value = 'ai_generated'
  originalAiSQL.value = sql
  await nextTick()
  handleExecute()
}

// ── Fill preview ──
function previewFill(sql: string) {
  mode.value = 'fill'
  fillSubMode.value = 'insert'  // chat-generated SQL always goes to insert (new-row) mode
  fillSQL.value = sql
  fillColumns.value = []

  if (sql.trim().toUpperCase().startsWith('INSERT')) {
    const tableMatch = sql.match(/INSERT\s+INTO\s+"?(\w+)"?\s*\(([^)]+)\)/i)
    if (tableMatch) {
      const colNames = tableMatch[2].split(',').map(s => s.trim().replace(/"/g, ''))
      const tableSchema = tables.value.find(t => t.name === tableMatch[1])
      fillColumns.value = colNames.map(name => {
        const colDef = tableSchema?.columns?.find((c: any) => (typeof c === 'string' ? c : c.name) === name)
        return { name, type: colDef?.type || '', value: '', aiFilled: false }
      })
      parseInsertValues(sql)
    }
  } else if (sql.trim().toUpperCase().startsWith('UPDATE')) {
    // Parse UPDATE "table" SET col1='val1', col2=NULL WHERE ...
    const updateMatch = sql.match(/UPDATE\s+"?(\w+)"?\s+SET\s+(.+?)(\s+WHERE\s+.+)?$/is)
    if (updateMatch) {
      const tableName = updateMatch[1]
      const setClause = updateMatch[2]
      const tableSchema = tables.value.find(t => t.name === tableName)

      // Parse SET pairs: col='val', col2=NULL, col3=123
      const pairs = splitCSV(setClause)
      fillColumns.value = pairs.map(pair => {
        const eqIdx = pair.indexOf('=')
        if (eqIdx < 0) return null
        const name = pair.substring(0, eqIdx).trim().replace(/"/g, '')
        const rawVal = pair.substring(eqIdx + 1).trim()
        const colDef = tableSchema?.columns?.find((c: any) => (typeof c === 'string' ? c : c.name) === name)

        let value = rawVal
        if (value.toUpperCase() === 'NULL') value = ''
        else { value = value.replace(/^'|'$/g, '').replace(/''/g, "'") }

        return { name, type: colDef?.type || '', value, aiFilled: true }
      }).filter(Boolean) as ColRow[]
    }
  }
  ElMessage.success('已切换到填表预览')
}
function clearFill() { fillSQL.value = ''; fillColumns.value = [] }
async function handleExecFillSQL() { if (!store.activeConnection || !fillSQL.value) return; fillExecuting.value = true
  try { const r = await execSQL({ connectionId: store.activeConnection.id, sql: fillSQL.value, confirmed: true }); addChatMsg('db-status', `✅ 已执行 · 影响 ${r.affectedRows ?? 0} 行 · ${r.executionTime}ms`) }
  catch (e: any) { addChatMsg('db-error', e.response?.data?.error || e.message || '执行失败') } finally { fillExecuting.value = false } }

async function handleFixFillSQL() {
  if (!store.activeConnection || !fillSQL.value) return
  if (!currentConvId.value) newConversation()
  fixing.value = true; chatBusy.value = true
  const fixPrompt = buildFixPrompt(fillSQL.value)
  addChatMsg('user', '🤖 修复填表SQL')
  resetThinking()
  aiConfigAPI.agentChat(store.activeConnection.id, fixPrompt, selectedTable.value||undefined, currentConvId.value||undefined,
    'error_fix', 'modify',
    (t,d) => handleAgentEvent(t,d),
    (e) => { if (e.name!=='AbortError') addChatMsg('db-error','Agent: '+e.message); finishThinking(false); fixing.value=false; chatBusy.value=false },
    (sql) => {
      const lastSql = sql.split('\n\n').filter((s: string) => s.trim().toUpperCase().startsWith('INSERT')||s.trim().toUpperCase().startsWith('UPDATE')).pop() || ''
      if (lastSql) previewFill(lastSql)
      finishThinking(true); fixing.value=false; chatBusy.value=false
    }
  )
}

function parseInsertValues(sql: string) { const m = sql.match(/VALUES\s*\((.+)\)/is); if (!m) return; const parts = splitCSV(m[1]); parts.forEach((v,i) => { if (i<fillColumns.value.length && v.trim().toUpperCase()!=='NULL' && v.trim().toUpperCase()!=='DEFAULT') { fillColumns.value[i].value = v.trim().replace(/^'|'$/g,''); fillColumns.value[i].aiFilled = true } }) }
function splitCSV(s: string): string[] { const r: string[] = []; let c='',q=false; for(let i=0;i<s.length;i++){if(s[i]==="'"&&(i===0||s[i-1]!=='\\'))q=!q,c+=s[i];else if(s[i]===','&&!q)r.push(c),c='';else c+=s[i];}r.push(c);return r }
function rebuildFillSQL() {
  if (fillSQL.value.trim().toUpperCase().startsWith('INSERT')) {
    const ns = fillColumns.value.map(c => c.name)
    const vs = fillColumns.value.map(c => { const v = c.value.trim(); if (!v || v.toUpperCase()==='NULL') return 'NULL'; if (/^\d+(\.\d+)?$/.test(v)) return v; if (v.toUpperCase()==='TRUE'||v.toUpperCase()==='FALSE') return v.toUpperCase(); return "'"+v.replace(/'/g,"''")+"'" })
    fillSQL.value = `INSERT INTO "${fillSQL.value.match(/INTO\s+"?(\w+)"?/i)?.[1]||'table'}" (${ns.map(n=>`"${n}"`).join(', ')}) VALUES (${vs.join(', ')})`
  } else if (fillSQL.value.trim().toUpperCase().startsWith('UPDATE')) {
    const tableName = fillSQL.value.match(/UPDATE\s+"?(\w+)"?/i)?.[1] || 'table'
    const whereClause = fillSQL.value.match(/(WHERE\s+.+)$/is)?.[1] || ''
    const sets = fillColumns.value.map(c => {
      const v = c.value.trim()
      if (!v || v.toUpperCase()==='NULL') return `"${c.name}" = NULL`
      if (/^\d+(\.\d+)?$/.test(v)) return `"${c.name}" = ${v}`
      if (v.toUpperCase()==='TRUE'||v.toUpperCase()==='FALSE') return `"${c.name}" = ${v.toUpperCase()}`
      return `"${c.name}" = '${v.replace(/'/g,"''")}'`
    })
    fillSQL.value = `UPDATE "${tableName}" SET ${sets.join(', ')} ${whereClause}`.trim()
  }
  ElMessage.success('SQL已重建')
}

// ═══════════════════════════════════════════════════════
//  EDIT MODE (行编辑) functions
// ═══════════════════════════════════════════════════════

const editSelectedRow = computed(() => {
  if (editSearchedRows.value.length === 0 || editRowIndex.value < 0) return null
  return editSearchedRows.value[editRowIndex.value] || null
})

const hasEditChanges = computed(() => editColumns.value.some(c => c.modified && !c.pk))

function onFillSubModeChange(mode: string) {
  if (mode === 'edit') {
    fillSQL.value = ''
    fillColumns.value = []
  } else {
    editSearchedRows.value = []
    editColumns.value = []
    editSQL.value = ''
  }
}

function onFilterCountChange(n: number) {
  editFilterCount.value = n
  editFilters.value = Array.from({length:5}, (_, i) =>
    i < n ? editFilters.value[i] || {column:'',value:''} : {column:'',value:''}
  )
}

async function onFilterColumnChange(filterIdx: number, colName: string) {
  if (!colName || !store.activeConnection || !selectedTable.value) return
  editFilters.value[filterIdx].value = ''
  editColValuesLoading.value[filterIdx] = true
  try {
    const r = await getColumnValues(selectedTable.value, colName, store.activeConnection.id) as any
    editColumnValues.value[colName] = r.values || []
    editColValuesLoading.value[filterIdx] = false
  } catch {
    editColValuesLoading.value[filterIdx] = false
  }
}

async function searchEditRows() {
  if (!store.activeConnection || !selectedTable.value) return
  editSearching.value = true
  try {
    const activeFilters = editFilters.value
      .slice(0, editFilterCount.value)
      .filter(f => f.column && f.value)
    const r = await searchRows(selectedTable.value, store.activeConnection.id, activeFilters) as any
    editSearchedRows.value = r.rows || []
    editPkColumn.value = (r.pkColumns && r.pkColumns.length > 0) ? r.pkColumns[0] : ''
    editRowIndex.value = 0
    if (editSearchedRows.value.length > 0) selectEditRow(0)
    else { editColumns.value = []; editSQL.value = '' }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error || '搜索失败')
  } finally { editSearching.value = false }
}

function selectEditRow(idx: number) {
  editRowIndex.value = idx
  const row = editSearchedRows.value[idx]
  if (!row) return

  // Build column rows with four-color status
  const table = tables.value.find(t => t.name === selectedTable.value)
  const cols: any[] = table?.columns || []
  editColumns.value = cols.map((col: any) => {
    const colName = typeof col === 'string' ? col : col.name
    const colType = typeof col === 'string' ? 'TEXT' : (col.type || 'TEXT')
    const isPk = col.pk === true || editPkColumn.value === colName
    const rawVal = row[colName]
    const isNull = rawVal === null || rawVal === undefined

    return {
      name: colName,
      type: colType,
      value: isNull ? '' : String(rawVal),
      aiFilled: false,
      pk: isPk,
      wasNull: isNull && !isPk,
      modified: false,
      aiSuggested: false,
      originalValue: isNull ? '' : String(rawVal)
    }
  })
  rebuildEditSQL()
}

function isComplexCell(v: string): boolean {
  if (!v) return false
  const s = v.trim()
  return s.startsWith('{') || s.startsWith('[') || s.startsWith('<') || (s.includes(':') && s.includes('\n'))
}

function rowPreview(r: Record<string, any>): string {
  const keys = Object.keys(r).filter(k => k !== editPkColumn.value).slice(0, 4)
  return keys.map(k => `${k}=${r[k] ?? 'NULL'}`).join(', ')
}

function editFieldClass(row: ColRow): string {
  if (row.pk) return 'field-readonly'
  if (row.modified) return 'field-modified'
  if (row.wasNull) return 'field-null'
  if (row.aiSuggested) return 'field-ai'
  return 'field-existing'
}

function onEditFieldChange(row: ColRow) {
  if (row.pk) return
  if (row.value !== row.originalValue) {
    row.modified = true; row.aiSuggested = false
  } else {
    row.modified = false
    if (row.wasNull) { row.modified = false }
  }
}

function rebuildEditSQL() {
  const tableName = selectedTable.value
  if (!tableName || !editPkColumn.value || !editSelectedRow.value) return

  const pkVal = editSelectedRow.value[editPkColumn.value]
  const pkCol = editPkColumn.value

  // Build SET clause from modified or non-null values
  const changedCols = editColumns.value.filter(c => c.modified || (!c.wasNull && !c.pk && c.value !== '' && c.value !== c.originalValue))
  const allFillCols = editColumns.value.filter(c => c.modified || (c.wasNull && c.value))

  // Use all columns that have values for a complete SET
  const setCols = editColumns.value.filter(c => {
    if (c.pk) return false
    if (c.modified) return true // user changed
    if (c.wasNull && c.value) return true // user filled null
    if (!c.wasNull && c.value === c.originalValue) return true // keep existing value in SET too
    return false
  })

  if (setCols.length === 0) { editSQL.value = ''; return }

  const sets = setCols.map(c => {
    const v = c.value.trim()
    if (!v || v.toUpperCase() === 'NULL') return `"${c.name}" = NULL`
    if (/^\d+(\.\d+)?$/.test(v)) return `"${c.name}" = ${v}`
    if (v.toUpperCase() === 'TRUE' || v.toUpperCase() === 'FALSE') return `"${c.name}" = ${v.toUpperCase()}`
    return `"${c.name}" = '${v.replace(/'/g, "''")}'`
  })

  const pkWhere = /^\d+$/.test(String(pkVal))
    ? `"${pkCol}" = ${pkVal}`
    : `"${pkCol}" = '${String(pkVal).replace(/'/g, "''")}'`

  editSQL.value = `UPDATE "${tableName}" SET ${sets.join(', ')} WHERE ${pkWhere}`
}

async function handleExecEditSQL() {
  if (!store.activeConnection || !editSQL.value) return
  fillExecuting.value = true
  try {
    const r = await execSQL({ connectionId: store.activeConnection.id, sql: editSQL.value, confirmed: true })
    addChatMsg('db-status', `✅ 已执行 · 影响 ${r.affectedRows ?? 0} 行 · ${r.executionTime}ms`)
  } catch (e: any) {
    addChatMsg('db-error', e.response?.data?.error || e.message || '执行失败')
  } finally { fillExecuting.value = false }
}

function resetEditForm() {
  if (editRowIndex.value >= 0 && editSearchedRows.value.length > 0) {
    selectEditRow(editRowIndex.value)
  }
}

async function handleAICompleteEdit() {
  if (!store.activeConnection || !selectedTable.value || !editSelectedRow.value || !editPkColumn.value) return
  if (!currentConvId.value) newConversation()

  editAIWorking.value = true

  const row = editSelectedRow.value
  const pkCol = editPkColumn.value
  const pkVal = row[pkCol]
  const nullFields = editColumns.value.filter(c => c.wasNull).map(c => c.name)

  const userIntent = `填写以下NULL字段：${nullFields.join(', ')}。根据已有数据和常识推断合适的值。`
  addChatMsg('user', `🤖 AI补全空值: ${selectedTable.value} · ${pkCol}=${pkVal} · 空字段: ${nullFields.join(',')}`)
  saveChatMessage('user', `AI补全空值: ${selectedTable.value} · ${pkCol}=${pkVal} · 空字段: ${nullFields.join(',')}`)

  try {
    const r = await aiConfigAPI.generateUpdateRow(
      store.activeConnection.id,
      selectedTable.value,
      editSelectedRow.value,
      pkCol,
      pkVal,
      userIntent
    ) as any
    if (r.sql) {
      addChatMsg('ai', r.sql)
      saveChatMessage('ai', r.sql)
      parseEditAgentSQL(r.sql)
    }
  } catch (e: any) {
    addChatMsg('db-error', 'AI补全: ' + (e.response?.data?.message || e.message))
  } finally { editAIWorking.value = false }
}

/** Parse agent-returned UPDATE SQL → apply AI-suggested values to edit form */
function parseEditAgentSQL(sql: string) {
  editSQL.value = sql
  // Extract SET clause
  const setMatch = sql.match(/SET\s+(.+?)(\s+WHERE\s+.+)?$/is)
  if (!setMatch) { ElMessage.warning('AI返回的SQL未能解析SET子句'); return }

  const setClause = setMatch[1]
  const pairs = setClause.split(/,(?=(?:[^']*'[^']*')*[^']*$)/).map((s: string) => s.trim()).filter(Boolean)

  let applied = 0
  editColumns.value.forEach(col => {
    if (col.pk) return
    const matched = pairs.find((p: string) => {
      const m = p.match(/^"?(\w+)"?\s*=/)
      return m && m[1] === col.name
    })
    if (matched && col.wasNull) {
      const eqIdx = matched.indexOf('=')
      if (eqIdx >= 0) {
        let val = matched.substring(eqIdx + 1).trim()
        if (val.toUpperCase() === 'NULL') val = ''
        else { val = val.replace(/^'|'$/g, '').replace(/''/g, "'") }
        if (val) {
          col.value = val
          col.aiSuggested = true
          col.modified = false
          applied++
        }
      }
    }
  })

  if (applied > 0) {
    ElMessage.success(`AI已补全 ${applied} 个空值字段 — 查看右侧对话了解推理过程`)
  } else {
    ElMessage.info('AI未建议新的填充值，可能所有字段已有数据')
  }
}

function clearEditMode() {
  editSearchedRows.value = []
  editColumns.value = []
  editSQL.value = ''
  editRowIndex.value = 0
}

// ── Build context-aware fix prompt (includes conversation history + user intent) ──
function buildFixPrompt(sql: string, errMsg?: string): string {
  const parts: string[] = []

  // 1. Extract user's original intent from recent conversation
  const recentUserMsgs = chatMessages.value.filter(m => m.type === 'user').slice(-3)
  if (recentUserMsgs.length > 0) {
    parts.push('## 用户原始意图')
    parts.push(recentUserMsgs.map(m => `- ${m.content}`).join('\n'))
  }

  // 2. Extract recent AI outputs (context of what was generated)
  const recentAiMsgs = chatMessages.value.filter(m => m.type === 'ai').slice(-3)
  if (recentAiMsgs.length > 0) {
    parts.push('## 最近AI输出（上下文）')
    recentAiMsgs.forEach(m => {
      const snippet = m.content.length > 500 ? m.content.substring(0, 500) + '...' : m.content
      parts.push(snippet)
    })
  }

  // 3. Current target table + schema
  if (selectedTable.value) {
    parts.push('## 目标表: ' + selectedTable.value)
    const snap = buildTableSnapshot()
    if (snap) parts.push(snap)
  }

  // 4. Conversation snapshot for intent recovery
  const recentMsgs = chatMessages.value.slice(-15)
  if (recentMsgs.length > 0) {
    parts.push('## 对话历史（最近15条）')
    recentMsgs.forEach(m => {
      const roleTag = m.type === 'user' ? 'User' : m.type === 'ai' ? 'AI' : m.type === 'db-error' ? 'Error' : 'DB'
      const snippet = m.content.length > 200 ? m.content.substring(0, 200) + '...' : m.content
      parts.push(`[${roleTag}] ${snippet}`)
    })
  }

  // 5. The SQL to fix
  parts.push('## 待修复SQL')
  parts.push(sql)

  // 6. Error message
  if (errMsg) {
    parts.push('## 错误信息')
    parts.push(errMsg)
  }

  // 7. Instruction
  parts.push('## 要求')
  parts.push('请根据用户原始意图和上下文，审查并修复以上SQL。修复后的SQL必须：1) 符合用户原始需求 2) 语法正确无分号 3) 针对目标表操作 4) 不要添加用户没要求的额外条件')

  return parts.join('\n\n')
}

// ── AI Fix ──
async function handleFixCurrentSQL() {
  if (!store.activeConnection || !sqlContent.value.trim()) return
  if (!currentConvId.value) newConversation()
  fixing.value = true; chatBusy.value = true
  const fixPrompt = buildFixPrompt(sqlContent.value.trim())
  addChatMsg('user', '🤖 请修复SQL')
  saveChatMessage('user', fixPrompt)
  resetThinking()
  thinkingBlock.progress = 'Agent 修复'
  aiConfigAPI.agentChat(store.activeConnection.id, fixPrompt, selectedTable.value||undefined, currentConvId.value||undefined,
    'error_fix', 'modify',
    (t,d) => handleAgentEvent(t,d),
    (e) => { if (e.name!=='AbortError') { addSegment('fail','失败: '+e.message); addChatMsg('db-error','Agent: '+e.message) }; finishThinking(false); fixing.value=false; chatBusy.value=false },
    () => { addSegment('pass','修复完成'); finishThinking(true); fixing.value=false; chatBusy.value=false }
  )
}
async function handleFixSQL(errMsg: string) {
  if (!store.activeConnection) return; if (!currentConvId.value) newConversation()
  fixing.value = true; chatBusy.value = true
  const fixPrompt = buildFixPrompt(sqlContent.value.trim() || 'SELECT 修复报错的SQL', errMsg)
  addChatMsg('user', '🤖 AI修复报错')
  saveChatMessage('user', fixPrompt)

  resetThinking()
  thinkingBlock.progress = 'Agent 修复中'

  aiConfigAPI.agentChat(
    store.activeConnection.id, fixPrompt,
    selectedTable.value || undefined, currentConvId.value || undefined,
    'error_fix', 'modify',
    (type, data) => handleAgentEvent(type, data),
    (err) => { if (err.name !== 'AbortError') { addSegment('fail', 'Agent修复失败: ' + err.message); addChatMsg('db-error', 'Agent: ' + err.message, true) }; finishThinking(false); fixing.value = false; chatBusy.value = false },
    (sql) => { addSegment('pass', 'Agent修复完成'); finishThinking(true); fixing.value = false; chatBusy.value = false }
  )
}

// ── Settings ──
async function handleSaveBackground() {
  savingBg.value = true
  try { await aiConfigAPI.saveProjectBackground(projectBackground.value); ElMessage.success('项目背景已保存') }
  catch (e: any) { ElMessage.error('保存失败') }
  finally { savingBg.value = false }
}

// ── Init ──
onMounted(async () => {
  if (!store.activeConnection) { await store.fetchConnections(); const a = store.connections.find(c => c.isActive); if (a) await store.switchConnection(a.id) }
  if (store.activeConnection) schema.value = store.schema
  if (store.schema?.tables) tables.value = store.schema.tables
  loadConversations()
  // Load project background
  try { const r = await aiConfigAPI.getProjectBackground() as any; projectBackground.value = r.background || '' } catch {}
  const pending = store.takePendingSql(); if (pending) { sqlContent.value = pending; ElMessage.success('已加载') }
})

// Watch connection changes from header dropdown
watch(() => store.activeConnection, (newConn) => {
  if (newConn) {
    schema.value = store.schema
    if (store.schema?.tables) tables.value = store.schema.tables
    selectedTable.value = ''
    console.log('SqlWorkspace: connection changed, schema updated', tables.value.length, 'tables')
  }
})
</script>

<style scoped>
.workspace { display: flex; height: 100%; }

/* ── Left Panel ── */
.left-panel { flex: 1; display: flex; flex-direction: column; min-width: 0; padding: 12px; gap: 8px; }
.toolbar { display: flex; align-items: center; gap: 6px; flex-shrink: 0; flex-wrap: wrap; }
.mode-switch { margin-right: 8px; }

/* ── Tab Bar ── */
.tab-bar { display: flex; align-items: center; background: #f5f7fa; border-radius: 4px 4px 0 0; padding: 0 4px; min-height: 32px; flex-shrink: 0; }
.tab-list { display: flex; align-items: center; gap: 2px; flex: 1; overflow-x: auto; }
.tab-item { display: inline-flex; align-items: center; gap: 4px; padding: 4px 10px; font-size: 12px; color: #606266; cursor: pointer; border-radius: 4px 4px 0 0; white-space: nowrap; user-select: none; border: 1px solid transparent; border-bottom: none; }
.tab-item:hover { background: #e8eaed; color: #303133; }
.tab-item.active { background: #fff; color: #409eff; border-color: #e4e7ed; font-weight: 600; }
.tab-title { max-width: 120px; overflow: hidden; text-overflow: ellipsis; }
.tab-close { font-size: 14px; color: #c0c4cc; padding: 0; min-height: auto; }
.tab-close:hover { color: #f56c6c; }
.tab-add { font-size: 16px; padding: 0 8px; color: #909399; flex-shrink: 0; }
.tab-add:hover { color: #409eff; }
.mode-hint { font-size: 12px; color: #909399; }

.editor-row { flex: 1; display: flex; gap: 0; min-height: 250px; }
.editor-area { flex: 1; min-width: 0; }

.explain-panel { width: 280px; min-width: 220px; display: flex; flex-direction: column; border-left: 1px solid #e4e7ed; margin-left: 8px; background: #fafbfc; border-radius: 0 4px 4px 0; }
.explain-header { display: flex; justify-content: space-between; align-items: center; padding: 8px 12px; font-size: 13px; font-weight: 600; color: #303133; border-bottom: 1px solid #e4e7ed; background: #fff; }
.explain-body { flex: 1; overflow-y: auto; padding: 12px; }
.explain-section { margin-bottom: 14px; }
.explain-label { font-size: 12px; font-weight: 600; color: #606266; margin-bottom: 6px; }
.explain-text { font-size: 13px; line-height: 1.7; color: #303133; white-space: pre-wrap; word-break: break-word; }
.explain-empty { font-size: 13px; color: #c0c4cc; text-align: center; padding: 24px 0; }
.explain-actions { padding: 8px 12px; border-top: 1px solid #e4e7ed; background: #fff; }
.explain-tabs { flex: 1; }
.explain-tabs :deep(.el-tabs__header) { margin: 0; }
.explain-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
.explain-tabs :deep(.el-tabs__item) { font-size: 12px; padding: 0 12px; height: 32px; line-height: 32px; }
.explain-plan-json { font-family: 'JetBrains Mono', Consolas, monospace; font-size: 11px; line-height: 1.4; white-space: pre-wrap; word-break: break-all; background: #f0f2f5; padding: 8px; border-radius: 4px; max-height: 400px; overflow: auto; }
.explain-error { color: #f56c6c; font-size: 13px; padding: 12px 0; }
.slide-enter-active, .slide-leave-active { transition: width .25s ease, opacity .25s ease; }
.slide-enter-from, .slide-leave-to { width: 0 !important; opacity: 0; overflow: hidden; }
.result-area { flex: 1; overflow: auto; min-height: 150px; }

/* ── Fill Preview ── */
.fill-area { flex: 1; overflow-y: auto; }
.fill-tabs { margin-bottom: 12px; }
.fill-empty { text-align: center; color: #909399; padding: 60px 16px; }
.fill-empty p { font-size: 16px; margin-bottom: 8px; }
.fill-empty span { font-size: 13px; }
.fill-result { background: #f5f7fa; padding: 16px; border-radius: 8px; }
.sql-preview { margin-bottom: 8px; }
.section-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 8px; }
.insert-sql, .update-sql { display: block; font-family: 'JetBrains Mono', Consolas, monospace; font-size: 13px; white-space: pre-wrap; word-break: break-all; padding: 12px; border-radius: 6px; }
.insert-sql { color: #409eff; background: #ecf5ff; }
.update-sql { color: #e6a23c; background: #fef5e7; }
.fill-actions { display: flex; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
.ai-filled :deep(.el-input__inner) { background: #fdf6ec !important; border-color: #e6a23c !important; }

/* ── Edit Mode (行编辑) ── */
.edit-row-selector { background: #f5f7fa; padding: 12px; border-radius: 8px; margin-bottom: 8px; }
.selector-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.selector-title { font-size: 14px; font-weight: 600; color: #303133; }
.selector-hint { font-size: 12px; color: #909399; }
.edit-no-table { text-align: center; padding: 30px; color: #909399; }
.edit-filters { display: flex; flex-direction: column; gap: 6px; }
.filter-info { display: flex; align-items: center; font-size: 12px; color: #606266; }
.filter-row { display: flex; align-items: center; gap: 6px; }
.filter-label { font-size: 12px; color: #606266; min-width: 42px; }
.row-count { font-size: 12px; color: #67c23a; margin-left: 10px; }
.edit-row-list { margin-top: 10px; }
.row-list-header { display: flex; justify-content: space-between; align-items: center; font-size: 12px; color: #606266; margin-bottom: 6px; }
.edit-row-item { display: flex; align-items: center; gap: 8px; padding: 6px 10px; border: 1px solid #e4e7ed; border-radius: 6px; margin-bottom: 4px; cursor: pointer; font-size: 12px; transition: all 0.15s; background: #fff; }
.edit-row-item:hover { border-color: #409eff; background: #ecf5ff; }
.edit-row-item.active { border-color: #409eff; background: #d9ecff; font-weight: 600; }
.row-item-num { color: #909399; min-width: 28px; }
.row-item-preview { flex: 1; color: #303133; font-family: 'JetBrains Mono', Consolas, monospace; font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.row-item-pk { color: #409eff; font-size: 11px; }
.edit-row-form { background: #f5f7fa; padding: 16px; border-radius: 8px; }
.form-navigator { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.edit-no-row-selected { text-align: center; padding: 30px; color: #909399; }

/* Four-color field states */
.field-readonly :deep(.el-input__inner) { background: #f0f0f0 !important; color: #909399 !important; cursor: not-allowed; }
.field-modified :deep(.el-input__inner) { background: #fef3e6 !important; border-color: #e6a23c !important; }
.field-null :deep(.el-input__inner) { background: #fef0f0 !important; border-color: #f89898 !important; }
.field-ai :deep(.el-input__inner) { background: #ecf8f0 !important; border-color: #67c23a !important; }
.field-existing :deep(.el-input__inner) { background: #fff !important; }

/* ── Right Panel ── */
.right-panel { width: 380px; min-width: 320px; display: flex; flex-direction: column; border-left: 1px solid #e4e7ed; background: #fafbfc; }
.chat-header { display: flex; align-items: center; padding: 10px 12px; border-bottom: 1px solid #e4e7ed; background: #fff; }
.table-selector { padding: 8px 12px; border-bottom: 1px solid #e4e7ed; background: #fff; }
.chat-messages { flex: 1; overflow-y: auto; padding: 12px; }

.msg { margin-bottom: 10px; padding: 10px 12px; border-radius: 8px; border-left: 3px solid #c0c4cc; background: #fff; }
.msg.user  { border-left-color: #409eff; background: #ecf5ff; }
.msg.ai    { border-left-color: #67c23a; background: #f0f9eb; }
.msg.db-status { border-left-color: #909399; background: #f5f7fa; }
.msg.db-error  { border-left-color: #f56c6c; background: #fef0f0; }
.msg-head { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.msg-icon { font-size: 14px; }
.msg-role { font-size: 12px; font-weight: 600; color: #606266; }
.msg-time { font-size: 11px; color: #c0c4cc; margin-left: auto; }
.msg-body { font-size: 13px; line-height: 1.5; color: #303133; word-break: break-word; }
.sql-code { font-family: 'JetBrains Mono', Consolas, monospace; font-size: 12px; color: #409eff; white-space: pre-wrap; background: #f0f5ff; padding: 6px 8px; border-radius: 4px; display: block; }
.msg-actions { margin-top: 6px; display: flex; gap: 8px; flex-wrap: wrap; }
/* ── Suggestion Panel ── */
.suggestion-panel { border-top: 1px solid #e4e7ed; border-bottom: 1px solid #e4e7ed; background: #fef9e7; padding: 10px 12px; }
.suggestion-header { display: flex; justify-content: space-between; align-items: center; font-size: 12px; font-weight: 600; color: #e6a23c; margin-bottom: 6px; }
.suggestion-sql { font-family: 'JetBrains Mono', Consolas, monospace; font-size: 11px; color: #909399; background: #fff; padding: 6px 8px; border-radius: 4px; display: block; white-space: pre-wrap; word-break: break-all; margin-bottom: 8px; max-height: 60px; overflow-y: auto; }
.suggestion-input-row { display: flex; flex-direction: column; gap: 6px; }
.suggestion-actions { display: flex; justify-content: flex-end; gap: 6px; }

.chat-input { display: flex; padding: 10px 12px; border-top: 1px solid #e4e7ed; background: #fff; }

.context-warning { padding: 6px 12px; background: #fef0f0; border-bottom: 1px solid #fde2e2; font-size: 11px; color: #e6a23c; text-align: center; }

/* ── Thinking Block ── */
.thinking-block { margin-bottom: 10px; border: 1px solid #e4e7ed; border-radius: 8px; overflow: hidden; }
.thinking-header { display: flex; align-items: center; padding: 8px 12px; background: #f5f3ff; border-bottom: 1px solid #e4e7ed; cursor: pointer; font-size: 13px; font-weight: 600; color: #7c3aed; user-select: none; }
.thinking-header:hover { background: #ede9fe; }
.thinking-body { max-height: 200px; overflow-y: auto; padding: 10px 12px; background: #faf9ff; font-size: 12px; }
.think-seg { padding: 2px 0; line-height: 1.5; }
.think-seg.thinking { color: #606266; white-space: pre-wrap; word-break: break-all; }
.think-seg.loop { color: #e6a23c; font-weight: 500; padding: 3px 0; }
.loop-info { display: flex; align-items: center; gap: 4px; }

/* Keep popover open when inner selects are used */
:global(.history-popover) {
  pointer-events: auto !important;
}
:global(.history-inner-select) {
  z-index: 2100 !important;
}
</style>
