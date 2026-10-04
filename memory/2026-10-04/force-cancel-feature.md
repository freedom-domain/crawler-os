# 强制取消功能（2026-10-04）

## 背景

任务 #22（曲谱日记）卡在 CANCELING 状态，worker 8090 已挂，取消消息没人消费。
原有兜底逻辑 `cancelStuckCancelingTasks` 要等 30 分钟（STUCK_CANCELING_MINUTES）才触发。

## 改动

### 后端（crawler-spider-service）

**SpiderService.java** — 新增 `forceCancelTask(Long id)` 方法：
- 仅 CANCELING 或 RUNNING 状态可强制取消
- 调用 `taskMapper.markCancelingTaskCanceled(id, now)` 立即置为 CANCELED
- 异常用 `BizException`（不是 BusinessException，编译报错后已修）

**SpiderController.java** — 新增接口：
- `PUT /task/{id}/force-cancel` → `spiderService.forceCancelTask(id)`

### 前端（crawler-admin-web）

**api/index.ts** — 新增：
```ts
export const taskForceCancel = (id: number) => request.put(`/spider/task/${id}/force-cancel`)
```

**Task.vue**：
- `getRowActions`：CANCELING 状态显示「强制取消」按钮（danger 红色，icon=VideoPause）
- `handleRowAction`：新增 `force-cancel` 分支 → `handleForceCancel(row)`
- `handleForceCancel`：确认框「强制取消会立即终止任务，不再等待 worker 完成。已抓数据保留，但未完成的部分会丢失。」→ 调 API → 提示「任务已强制取消」

## 验证

- `vue-tsc --noEmit` 通过
- 后端首次编译失败：`BusinessException` 不存在，改为 `BizException` 后通过

## 部署

1. 重启 `crawler-spider-service`
2. `npm run build` 重新构建前端
3. CANCELING 任务操作菜单里点「强制取消」

或直接调 API（无需前端）：
```bash
curl -X PUT http://192.168.31.2:8082/api/spider/task/{id}/force-cancel
```
