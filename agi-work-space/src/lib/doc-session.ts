import { ref } from 'vue'

/** 侧栏点「编辑」后，文档页据此进入编辑态 */
export const pendingEditSlug = ref<string | null>(null)

/** 侧栏点「移动」后，文档页打开重命名 / 移动对话框 */
export const pendingMoveSlug = ref<string | null>(null)
