/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_DEV_PROXY_TARGET?: string
  readonly VITE_COMMUNITY_ORIGIN?: string
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

declare module '@onepage/image-crop' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}
