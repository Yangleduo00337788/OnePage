/// <reference types="vite/client" />
/** 告诉 TypeScript：import Xxx.vue 是合法模块。没有这文件会报红。 */

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}
