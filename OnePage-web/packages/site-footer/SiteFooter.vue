<script setup lang="ts">
import { PROJECT_COPYRIGHT_LINE, PROJECT_GITEE_URL, PROJECT_GITHUB_URL } from '@onepage/site-urls'

withDefaults(
  defineProps<{
    /** 侧栏收起时只显示仓库图标（仅 sidebar 布局） */
    compact?: boolean
    /** sidebar：社区侧栏；bar：创作者 / 运营主区底栏 */
    layout?: 'sidebar' | 'bar'
  }>(),
  { layout: 'sidebar' },
)
</script>

<template>
  <footer
    class="site-footer"
    :class="{
      'is-compact': compact,
      'is-bar': layout === 'bar',
    }"
  >
    <p v-if="!compact || layout === 'bar'" class="copy">{{ PROJECT_COPYRIGHT_LINE }}</p>
    <div class="links" :class="{ 'links-bar': layout === 'bar' }">
      <a
        class="repo"
        :href="PROJECT_GITHUB_URL"
        target="_blank"
        rel="noopener noreferrer"
        aria-label="GitHub 仓库"
        :title="PROJECT_GITHUB_URL"
      >
        <svg class="icon icon-github" width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
          <path
            fill="currentColor"
            d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 10.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"
          />
        </svg>
      </a>
      <a
        class="repo"
        :href="PROJECT_GITEE_URL"
        target="_blank"
        rel="noopener noreferrer"
        aria-label="Gitee 仓库"
        :title="PROJECT_GITEE_URL"
      >
        <svg class="icon icon-gitee" width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
          <path
            fill="currentColor"
            d="M11.984 0A12 12 0 0 0 0 12a12 12 0 0 0 12 12a12 12 0 0 0 12-12A12 12 0 0 0 12 0zm6.09 5.333c.328 0 .593.266.592.593v1.482a.594.594 0 0 1-.593.592H9.777c-.982 0-1.778.796-1.778 1.778v5.63c0 .327.266.592.593.592h5.63c.982 0 1.778-.796 1.778-1.778v-.296a.593.593 0 0 0-.592-.593h-4.15a.592.592 0 0 1-.592-.592v-1.482a.593.593 0 0 1 .593-.592h6.815c.327 0 .593.265.593.592v3.408a4 4 0 0 1-4 4H5.926a.593.593 0 0 1-.593-.593V9.778a4.444 4.444 0 0 1 4.445-4.444h8.296Z"
          />
        </svg>
      </a>
    </div>
  </footer>
</template>

<style scoped>
.site-footer {
  width: 100%;
  padding: 8px 4px 4px;
  border-top: 1px solid #e8e8e8;
}

.site-footer.is-compact {
  padding: 8px 0 4px;
  border-top: none;
}

.site-footer.is-compact .copy {
  display: none;
}

.site-footer.is-bar {
  flex-shrink: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px 16px;
  padding: 10px 24px 14px;
  border-top: 1px solid #e8e8e8;
  background: rgb(248, 248, 248);
}

.copy {
  margin: 0 0 6px;
  padding: 0 4px;
  font-size: 11px;
  line-height: 1.35;
  color: #8a8a8a;
  text-align: center;
}

.is-bar .copy {
  margin: 0;
  padding: 0;
  font-size: 12px;
}

.links {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.links-bar {
  gap: 12px;
}

.repo {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: #666;
  text-decoration: none;
}

.repo:hover {
  background: #eee;
  color: #1f1f1f;
}

.icon {
  display: block;
  flex-shrink: 0;
}

.icon-github {
  color: #24292f;
}

.icon-gitee {
  color: #c71d23;
}
</style>
