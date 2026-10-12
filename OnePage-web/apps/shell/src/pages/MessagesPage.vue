<!--
  私信：左会话列表，点开右侧对话。message 服务未建，不再用假会话演示。
-->
<script setup lang="ts">
import Icon from '../components/Icon.vue'
</script>

<template>
  <section class="im-page">
    <aside class="im-list">
      <div class="im-list-head">消息</div>
      <p class="px-5 py-16 text-center text-[13px] text-[#8a8a8a]">还没有私信</p>
    </aside>
    <div class="im-thread">
      <div class="im-empty">
        <Icon name="message-circle-more" :size="36" :stroke="1.5" />
        <p>私信尚未接入</p>
      </div>
    </div>
  </section>
</template>
