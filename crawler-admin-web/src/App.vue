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
body {
  font-family: Avenir Next, Avenir, 'PingFang SC', 'Microsoft YaHei', sans-serif;
  color: #102a43;
  background: #f7f8fa;
  -webkit-font-smoothing: antialiased;
}
:root {
  --ink-950: #172b4d;
  --ink-700: #5e6c84;
  --ink-500: #8993a4;
  --line: #e6e9ef;
  --canvas: #f7f8fa;
  --teal: #0f9f9a;
  --teal-dark: #087b78;
  --coral: #ed765f;
  --shadow: 0 4px 16px rgba(23, 43, 77, 0.055);
}
html, body, #app { min-height: 100%; }
button, input, textarea, select { font: inherit; }
.el-card {
  border: 1px solid var(--line);
  border-radius: 8px;
  box-shadow: var(--shadow);
  transition: border-color .2s ease, box-shadow .2s ease, transform .2s ease;
}
.el-card:hover { border-color: #d6dde8; box-shadow: 0 8px 24px rgba(23, 43, 77, .08); }
.el-card__header { padding: 17px 20px; border-bottom-color: #edf0f4; color: var(--ink-950); font-weight: 700; }
.el-card__body { padding: 20px; }
.el-button { border-radius: 7px; font-weight: 600; }
.el-button--small { border-radius: 6px; }
.el-button.is-link { font-weight: 600; }
.el-button--primary {
  --el-button-bg-color: var(--teal);
  --el-button-border-color: var(--teal);
  --el-button-hover-bg-color: var(--teal-dark);
  --el-button-hover-border-color: var(--teal-dark);
}
.el-button--primary:not(.is-text):not(.is-link) { box-shadow: 0 4px 10px rgba(15, 159, 154, .16); }
.el-input__wrapper, .el-select__wrapper { border-radius: 7px; box-shadow: 0 0 0 1px var(--line) inset; }
.el-input__wrapper.is-focus, .el-select__wrapper.is-focused { box-shadow: 0 0 0 1px var(--teal) inset, 0 0 0 3px rgba(22, 166, 163, .12); }
.el-table { --el-table-header-bg-color: #fafbfc; --el-table-row-hover-bg-color: #f5fbfa; color: #243b53; }
.el-table th.el-table__cell { color: var(--ink-700); font-weight: 700; }
.el-table td.el-table__cell, .el-table th.el-table__cell { border-bottom-color: #edf0f4; }
.el-table .cell { line-height: 22px; }
.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell { background: #fbfcfd; }
.el-tag { border-radius: 5px; }
.el-table__inner-wrapper::before { display: none; }

/* ===== 移动端适配 ===== */
@media (max-width: 767px) {
  /* 弹窗：近全屏 */
  .el-dialog {
    width: calc(100vw - 24px) !important;
    max-width: calc(100vw - 24px);
    margin: 12vh auto !important;
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
}
.el-menu--popup {
  min-width: 190px;
  padding: 8px;
  border: 1px solid rgba(114, 224, 200, .18);
  border-radius: 9px;
  background: #163b59 !important;
  box-shadow: 0 14px 30px rgba(16, 42, 67, .24);
}
.el-menu--popup .el-menu-item,
.el-menu--popup .el-sub-menu__title {
  height: 42px;
  line-height: 42px;
  margin: 3px 0;
  border-radius: 7px;
  color: #d9e2ec !important;
}
.el-menu--popup .el-menu-item:hover,
.el-menu--popup .el-sub-menu__title:hover {
  color: #fff !important;
  background: rgba(114, 224, 200, .16) !important;
}
.el-menu--popup .el-menu-item.is-active {
  color: #fff !important;
  background: #167d7b !important;
}
.el-menu--popup .el-icon { color: #72e0c8; }
.el-dialog { border-radius: 12px; overflow: hidden; }
.el-dialog__header { margin-right: 0; padding: 22px 24px 16px; border-bottom: 1px solid #edf2f6; }
.el-dialog__title { color: var(--ink-950); font-weight: 700; }
.el-dialog__body { padding: 24px; }
.el-form-item__label { color: #5e6c84; font-weight: 600; }
.el-form-item { margin-bottom: 18px; }
.el-form--inline .el-form-item { margin-right: 14px; margin-bottom: 10px; }
.card-header { min-height: 24px; display: flex; align-items: center; justify-content: space-between; }
.card-header > span:first-child { color: #172b4d; font-size: 15px; font-weight: 800; }
.el-pagination { --el-pagination-button-bg-color: #fff; }
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  margin-bottom: 18px;
  border: 1px solid #e8eef3;
  border-radius: 8px;
  background: #f8fafc;
}
.filter-label { color: var(--ink-700); font-size: 13px; font-weight: 700; }
@media (max-width: 720px) {
  .el-card__body { padding: 16px; }
  .filter-bar { align-items: stretch; flex-wrap: wrap; }
  .el-dialog { width: calc(100vw - 20px) !important; }
  .el-form--inline {
    display: block;
  }
  .el-form--inline .el-form-item {
    display: block;
    margin-right: 0;
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
