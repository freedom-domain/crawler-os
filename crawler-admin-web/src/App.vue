<template>
  <router-view />
</template>

<script setup lang="ts">
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}
*::-webkit-scrollbar { width: 8px; height: 8px; }
*::-webkit-scrollbar-track { background: transparent; }
*::-webkit-scrollbar-thumb { border: 2px solid transparent; border-radius: 999px; background: var(--scroll-thumb); background-clip: padding-box; }
*::-webkit-scrollbar-thumb:hover { background: var(--scroll-thumb-hover); background-clip: padding-box; }
body {
  font-family: Avenir Next, Avenir, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: var(--ink-950);
  background: var(--canvas);
  -webkit-font-smoothing: antialiased;
}
html, body, #app {
  width: 100%;
  height: 100%;
  min-height: 0;
}
body { overflow: hidden; }
@supports (min-height: 100dvh) {
  html, body, #app { height: 100dvh; }
}
body {
  background:
    radial-gradient(circle at 92% 0%, var(--bg-glow), transparent 28rem),
    var(--canvas);
}
button, input, textarea, select { font: inherit; }
/* 纯黑主题：EP 默认空状态插图是白色，压暗以免刺眼 */
html[data-theme="black"] .el-empty__image {
  filter: brightness(.6) saturate(.75);
  opacity: .85;
}
.el-card {
  border: 1px solid var(--line);
  border-radius: 12px;
  box-shadow: var(--shadow);
  background: var(--surface);
  transition: border-color .2s ease, box-shadow .2s ease, transform .2s ease;
}
.el-card:hover { border-color: var(--line-strong); box-shadow: var(--shadow); }
.el-card__header { padding: 18px 22px; border-bottom-color: var(--line); color: var(--ink-950); font-weight: 700; }
.el-card__body { padding: 22px; }
.el-button { border-radius: 7px; font-weight: 600; }
.el-button--small { border-radius: 6px; }
.el-button.is-link { font-weight: 600; }
.table-row-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  white-space: nowrap;
}
.table-row-actions .el-button + .el-button { margin-left: 0; }
.table-action-popper .el-dropdown-menu__item.is-danger:not(.is-disabled) { color: var(--el-color-danger); }
.table-action-popper .el-dropdown-menu__item.is-danger:not(.is-disabled):hover { color: var(--el-color-danger); background: var(--el-color-danger-light-9); }
.el-message-box__wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
}
.el-message-box {
  width: min(420px, calc(100vw - 32px));
  max-height: calc(100vh - 32px);
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface);
  box-shadow: var(--shadow);
}
@supports (height: 100dvh) {
  .el-message-box { max-height: calc(100dvh - 32px); }
}
.el-message-box__header {
  margin-right: 0;
  padding: 0 28px 14px;
  border-bottom: 1px solid var(--line);
  display: flex;
  align-items: center;
  gap: 16px;
}
.el-message-box__title {
  color: var(--ink-950);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: .3px;
  flex: 1;
  line-height: 1.5;
}
.el-message-box__headerbtn {
  position: static;
  width: 30px;
  height: 30px;
  border-radius: 8px;
  transition: background-color .15s ease;
}
.el-message-box__headerbtn:hover { background: var(--surface-soft); }
.el-message-box__headerbtn:focus-visible { outline: 2px solid rgba(var(--el-color-primary-rgb), .35); outline-offset: -3px; }
.el-message-box__content {
  max-height: min(55vh, 420px);
  overflow-y: auto;
  padding: 20px 28px 24px;
  color: var(--ink-700);
}
.el-message-box__container {
  display: flex;
  align-items: center;
  gap: 16px;
}
.el-message-box__status {
  display: grid;
  place-items: center;
  margin: 0;
  background: transparent;
  color: red;
  flex-shrink: 0;
  width: 1em !important;
  height: 1em !important;
}

.el-message-box__status.el-message-box-icon--primary { color: var(--teal); }
.el-message-box__status.el-message-box-icon--success { color: var(--success-ink); }
.el-message-box__status.el-message-box-icon--info { color: var(--msg-info-ink); }
.el-message-box__status.el-message-box-icon--error { color: var(--msg-error-ink); }
.el-message-box__message {
  flex: 1;
  min-width: 0;
  color: var(--ink-700);
  font-size: 14px;
  line-height: 1.8;
}
.el-message-box__message p {
  margin: 0;
  line-height: 1.8;
  overflow-wrap: anywhere;
  word-break: break-word;
  letter-spacing: .1px;
}
.el-message-box__message p + p {
  margin-top: 10px;
}
.el-message-box:has(.el-message-box-icon--error) .el-message-box__message { color: var(--msg-error-ink); }
.el-message-box:has(.el-message-box-icon--warning) .el-message-box__message { color: var(--msg-warning-ink); }
.el-message-box:has(.el-message-box-icon--success) .el-message-box__message { color: var(--success-ink); }
.el-message-box:has(.el-message-box-icon--info) .el-message-box__message { color: var(--msg-info-ink); }
.el-message-box:has(.el-message-box-icon--primary) .el-message-box__message { color: var(--teal); }
.el-message-box__btns {
  justify-content: center;
  gap: 12px;
  padding: 5px;
  border-top: 1px solid var(--line);
}
.el-message-box__btns .el-button + .el-button { margin-left: 0; }
.el-message-box__btns .el-button {
  min-width: 88px;
  height: 36px;
  padding: 0 18px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
}
.el-message-box__btns .el-button:not(.el-button--primary):not(.el-button--danger) {
  --el-button-text-color: var(--ink-700);
  --el-button-border-color: var(--line);
  --el-button-bg-color: var(--surface);
  --el-button-hover-text-color: var(--ink-900);
  --el-button-hover-border-color: var(--line-strong);
  --el-button-hover-bg-color: var(--surface-soft);
}
.el-message-box__btns .el-button--danger {
  --el-button-bg-color: var(--msg-danger-bg);
  --el-button-border-color: var(--msg-danger-bg);
  --el-button-hover-bg-color: var(--msg-danger-bg-hover);
  --el-button-hover-border-color: var(--msg-danger-bg-hover);
}
.el-message-box .el-message-box__btns .el-button:not(.is-text):not(.is-link):hover { transform: none; }
.el-message-box .el-message-box__btns .el-button:focus-visible { outline: 2px solid rgba(var(--el-color-primary-rgb), .35); outline-offset: 2px; }
.el-button--primary {
  --el-button-bg-color: var(--teal);
  --el-button-border-color: var(--teal);
  --el-button-hover-bg-color: var(--teal-dark);
  --el-button-hover-border-color: var(--teal-dark);
}
.el-button--primary:not(.is-text):not(.is-link) { box-shadow: 0 4px 10px rgba(var(--el-color-primary-rgb), .16); }
.el-button:not(.is-text):not(.is-link):hover { transform: translateY(-1px); }
.el-input__wrapper, .el-select__wrapper { border-radius: 8px; background: var(--surface-soft); box-shadow: 0 0 0 1px var(--line) inset; }
.el-input__wrapper.is-focus, .el-select__wrapper.is-focused { box-shadow: 0 0 0 1px var(--teal) inset, 0 0 0 3px var(--focus-ring); }
.el-table { --el-table-header-bg-color: var(--surface-soft); --el-table-row-hover-bg-color: var(--tint); color: var(--ink-900); border-radius: 8px; overflow: hidden; }
.el-table th.el-table__cell { color: var(--ink-700); font-weight: 700; }
.el-table td.el-table__cell, .el-table th.el-table__cell { border-bottom-color: var(--line); }
.el-table .cell { line-height: 22px; padding-top: 2px; padding-bottom: 2px; }
.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell { background: var(--surface-soft); }
.el-tag { border-radius: 6px; font-weight: 600; }
/* 统一链接色：跟随主题青色，替代 Element 默认蓝 */
.el-link--primary, .el-link--primary:hover, a { color: var(--teal); }
.el-link--primary.is-hovered, a:hover { color: var(--teal-dark); }
.el-pagination { margin-top: 18px; padding: 4px 0; justify-content: center; flex-wrap: wrap; gap: 4px; }
.el-pagination button, .el-pagination li { border-radius: 6px !important; }
.el-empty { padding: 44px 0; }
.el-loading-mask { background: var(--loading-mask); backdrop-filter: blur(2px); }
.el-table__inner-wrapper::before { display: none; }

/* ===== 表格自适应：不溢出容器，单滚动条查看全部列 =====
   规则：
   1. 容器 { overflow-x: auto } — 唯一滚动条
   2. .el-table__body-wrapper { overflow-x: hidden !important } — 禁用表格内部滚动（避免双滚动条）
   3. .el-table { width: max-content; min-width: 100% } — 表格宽度由列的 min-width 撑开（不压缩），最小 100% 保证铺满
   通过 .table-scroll-wrapper 容器实现，所有页面统一生效 */
.table-scroll-wrapper {
  width: 100%;
  overflow-x: auto;
  min-width: 0;
  scrollbar-width: thin;
  scrollbar-color: var(--scroll-thumb-2) var(--scroll-track);
}
.table-scroll-wrapper::-webkit-scrollbar {
  height: 10px;
}
.table-scroll-wrapper::-webkit-scrollbar-track {
  border-radius: 999px;
  background: var(--scroll-track);
}
.table-scroll-wrapper::-webkit-scrollbar-thumb {
  border: 2px solid var(--scroll-track);
  border-radius: 999px;
  background: var(--scroll-thumb-2);
  background-clip: padding-box;
}
.table-scroll-wrapper::-webkit-scrollbar-thumb:hover {
  background: var(--scroll-thumb-2-hover);
  background-clip: padding-box;
}
.table-scroll-wrapper .el-table {
  width: max-content;
  min-width: 100%;
}
.table-scroll-wrapper .el-table__body-wrapper {
  overflow-x: hidden !important;
}

/* ===== 移动端适配 ===== */
@media (max-width: 767px) {
  /* 弹窗：近全屏 */
  .el-dialog {
    width: calc(100vw - 24px) !important;
    max-width: calc(100vw - 24px);
    margin: 12vh auto !important;
  }
  .el-dialog.content-preview-dialog {
    top: 4px !important;
    margin: 0 auto !important;
  }
  .el-dialog__body { padding: 16px !important; }
  /* 表单：标签置顶，避免 label-width 挤压输入框 */
  .el-form--label-left .el-form-item__label,
  .el-form--label-top .el-form-item__label {
    float: none;
    display: block;
    width: auto !important;
    text-align: left;
    padding: 0 0 6px;
    line-height: 1.4;
  }
  .el-form--label-left .el-form-item__content {
    margin-left: 0 !important;
    line-height: 1.4;
  }
  /* 行内筛选表单：纵向排列 */
  .el-form--inline .el-form-item {
    display: block;
    margin-right: 0;
    margin-bottom: 10px;
  }
  .el-form--inline .el-form-item .el-select,
  .el-form--inline .el-form-item .el-input {
    width: 100% !important;
  }
  /* 表格：缩小内边距，允许横向滚动 */
  .el-table .cell { padding-left: 8px; padding-right: 8px; }
  .el-table th.el-table__cell,
  .el-table td.el-table__cell { padding: 6px 0; }
  /* 分页：居中、可换行 */
  .el-pagination {
    justify-content: center;
    flex-wrap: wrap;
    gap: 4px;
  }
  .el-pagination .el-select__wrapper { min-width: 90px; }
  /* 下拉菜单：贴边显示，避免溢出 */
  .el-dropdown__popper.el-popper {
    max-width: calc(100vw - 24px);
  }
  /* 卡片 */
  .el-card__header { padding: 12px 14px; }
  .el-card__body { padding: 14px; }
  /* 消息提示 */
  .el-message { min-width: 0; margin-left: auto; margin-right: auto; width: max-content; max-width: calc(100vw - 32px); }
  .el-message-box__header { padding: 0 18px 14px; }
  .el-message-box__content { padding: 14px 18px 22px; }
  .el-message-box__container { gap: 12px; }
  .el-message-box__btns { padding: 5px; gap: 10px; }
  .el-message-box__btns .el-button { min-width: 0; flex: 1; margin: 0; }
}
.el-menu--popup {
  min-width: 190px;
  padding: 8px;
  border: 1px solid var(--chrome-line);
  border-radius: 9px;
  background: var(--chrome-popup-bg) !important;
  box-shadow: var(--shadow);
}
.el-menu--popup .el-menu-item,
.el-menu--popup .el-sub-menu__title {
  height: 42px;
  line-height: 42px;
  margin: 3px 0;
  border-radius: 7px;
  color: var(--chrome-text) !important;
}
.el-menu--popup .el-menu-item:hover,
.el-menu--popup .el-sub-menu__title:hover {
  color: #fff !important;
  background: rgba(var(--neon-rgb), .16) !important;
}
.el-menu--popup .el-menu-item.is-active {
  color: var(--on-teal) !important;
  background: var(--menu-popup-active-bg) !important;
}
.el-menu--popup .el-icon { color: var(--chrome-icon); }
.el-dialog { border-radius: 12px; overflow: hidden; }
.el-dialog__header { margin-right: 0; padding: 22px 24px 16px; border-bottom: 1px solid var(--line); }
.el-dialog__title { color: var(--ink-950); font-weight: 700; }
.el-dialog__body { padding: 24px; }
.el-form-item__label { color: var(--ink-700); font-weight: 600; }
.el-form-item { margin-bottom: 18px; }
.el-form--inline .el-form-item { margin-right: 14px; margin-bottom: 10px; }
.el-card > .el-card__body > .el-form--inline {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 16px;
}
.el-card > .el-card__body > .el-form--inline .el-form-item {
  margin: 0;
}
/* 表格空数据占位：统一空状态视觉 */
.el-table__empty-block { min-height: 160px; }
.el-table__empty-text { color: var(--ink-500); font-size: 13px; }
.card-header { min-height: 24px; display: flex; align-items: center; justify-content: space-between; }
.card-header > span:first-child { color: var(--ink-950); font-size: 15px; font-weight: 800; }
.el-pagination { --el-pagination-button-bg-color: var(--surface); }
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  margin-bottom: 18px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: var(--surface-soft);
  box-shadow: var(--shadow-soft);
}
.filter-label { color: var(--ink-700); font-size: 13px; font-weight: 700; }
@media (max-width: 720px) {
  .el-card__body { padding: 16px; }
  .filter-bar { align-items: stretch; flex-wrap: wrap; }
  .el-dialog { width: calc(100vw - 20px) !important; }
  .el-dialog.content-preview-dialog { top: 4px !important; margin: 0 auto !important; }
  .el-form--inline {
    display: flex;
    align-items: stretch;
    flex-wrap: wrap;
    gap: 8px;
  }
  .el-form--inline .el-form-item {
    display: block;
    margin-right: 0;
    margin-bottom: 0;
    width: 100%;
  }
  .el-form-item__content,
  .el-input,
  .el-select,
  .el-date-editor,
  .el-input-number,
  .el-cascader,
  .el-autocomplete {
    width: 100% !important;
  }
  .el-table {
    min-width: 700px;
  }
  .el-table__wrapper {
    overflow-x: auto;
  }
}
</style>
