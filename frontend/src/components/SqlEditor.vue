<template>
  <div ref="editorContainer" class="sql-editor"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import * as monaco from 'monaco-editor'

const props = defineProps<{
  modelValue: string
  schema?: any
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  execute: []
}>()

function getSelectedText(): string {
  if (!editor) return ''
  const selection = editor.getSelection()
  if (!selection) return ''
  const model = editor.getModel()
  if (!model) return ''
  const selected = model.getValueInRange(selection)
  return selected || ''
}

defineExpose({ getSelectedText })

const editorContainer = ref<HTMLDivElement>()
let editor: monaco.editor.IStandaloneCodeEditor | null = null

onMounted(() => {
  if (!editorContainer.value) return

  editor = monaco.editor.create(editorContainer.value, {
    value: props.modelValue,
    language: 'sql',
    theme: 'vs',
    fontSize: 14,
    fontFamily: 'JetBrains Mono, Consolas, monospace',
    minimap: { enabled: false },
    automaticLayout: true,
    tabSize: 2
  })

  editor.onDidChangeModelContent(() => {
    emit('update:modelValue', editor?.getValue() || '')
  })

  editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
    emit('execute')
  })

  registerSQLCompletion()
})

// Watch for external modelValue changes (e.g., AI generated SQL)
watch(() => props.modelValue, (newValue) => {
  if (editor && newValue !== editor.getValue()) {
    editor.setValue(newValue)
  }
})

onBeforeUnmount(() => {
  editor?.dispose()
})

// SQL keywords for autocomplete
const SQL_KEYWORDS = [
  'SELECT', 'FROM', 'WHERE', 'INSERT', 'UPDATE', 'DELETE', 'CREATE', 'DROP', 'ALTER',
  'TABLE', 'INTO', 'VALUES', 'SET', 'AND', 'OR', 'NOT', 'IN', 'LIKE', 'BETWEEN',
  'JOIN', 'LEFT', 'RIGHT', 'INNER', 'OUTER', 'ON', 'AS', 'ORDER', 'BY', 'ASC',
  'DESC', 'GROUP', 'HAVING', 'LIMIT', 'OFFSET', 'UNION', 'ALL', 'DISTINCT',
  'COUNT', 'SUM', 'AVG', 'MAX', 'MIN', 'NULL', 'IS', 'EXISTS', 'CASE', 'WHEN',
  'THEN', 'ELSE', 'END', 'INDEX', 'PRIMARY', 'KEY', 'FOREIGN', 'REFERENCES',
  'CONSTRAINT', 'DEFAULT', 'CHECK', 'UNIQUE', 'CASCADE', 'TRUNCATE', 'BEGIN',
  'COMMIT', 'ROLLBACK', 'TRANSACTION', 'VACUUM', 'EXPLAIN', 'ANALYZE'
]

function registerSQLCompletion() {
  // Always register SQL keywords (once)
  monaco.languages.registerCompletionItemProvider('sql', {
    provideCompletionItems: (model, position) => {
      const word = model.getWordUntilPosition(position)
      const range: monaco.IRange = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn
      }

      const suggestions: monaco.languages.CompletionItem[] = []

      // SQL keywords
      SQL_KEYWORDS.forEach(keyword => {
        suggestions.push({
          label: keyword,
          kind: monaco.languages.CompletionItemKind.Keyword,
          insertText: keyword,
          detail: 'SQL Keyword',
          range,
          sortText: '1' + keyword
        })
      })

      // Tables and columns from schema
      if (props.schema?.tables) {
        props.schema.tables.forEach((table: any) => {
          suggestions.push({
            label: table.name,
            kind: monaco.languages.CompletionItemKind.Class,
            insertText: table.name,
            detail: `Table${table.type ? ' (' + table.type + ')' : ''}`,
            range,
            sortText: '2' + table.name
          })

          table.columns?.forEach((col: any) => {
            const colName = typeof col === 'string' ? col : col.name
            const colType = typeof col === 'string' ? '' : col.type
            const colNullable = typeof col === 'string' ? false : col.nullable
            const detailParts = [`Column of ${table.name}`]
            if (colType) detailParts.push(colType)
            if (colNullable) detailParts.push('NULLABLE')

            suggestions.push({
              label: colName,
              kind: monaco.languages.CompletionItemKind.Field,
              insertText: colName,
              detail: detailParts.join(' • '),
              documentation: typeof col === 'object' ?
                `Type: ${colType}\nNullable: ${colNullable}\n${col.comment ? 'Comment: ' + col.comment : ''}` :
                undefined,
              range,
              sortText: '3' + colName
            })
          })
        })
      }

      return { suggestions }
    }
  })
}
</script>

<style scoped>
.sql-editor {
  width: 100%;
  height: 100%;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}
</style>
