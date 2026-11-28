package com.collect.ai.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.collect.ai.entity.AiAssistant;
import com.collect.ai.entity.AiAssistantRole;
import com.collect.ai.mapper.AiAssistantMapper;
import com.collect.ai.mapper.AiAssistantRoleMapper;
import com.collect.ai.rag.dto.AiAssistantRoleDtos;
import com.collect.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiAssistantRoleService {

    private final AiAssistantRoleMapper assistantRoleMapper;
    private final AiAssistantMapper assistantMapper;

    public List<AiAssistantRoleDtos.Response> list() {
        return assistantRoleMapper.selectList(new LambdaQueryWrapper<AiAssistantRole>()
                        .orderByAsc(AiAssistantRole::getSort)
                        .orderByDesc(AiAssistantRole::getId))
                .stream().map(AiAssistantRoleService::toResponse).toList();
    }

    public List<AiAssistantRoleDtos.Response> listEnabled() {
        return assistantRoleMapper.selectList(new LambdaQueryWrapper<AiAssistantRole>()
                        .eq(AiAssistantRole::getEnabled, true)
                        .orderByAsc(AiAssistantRole::getSort)
                        .orderByDesc(AiAssistantRole::getId))
                .stream().map(AiAssistantRoleService::toResponse).toList();
    }

    public IPage<AiAssistantRoleDtos.Response> page(long current, long size, String keyword, Boolean enabled) {
        LambdaQueryWrapper<AiAssistantRole> query = new LambdaQueryWrapper<AiAssistantRole>()
                .eq(enabled != null, AiAssistantRole::getEnabled, enabled)
                .orderByAsc(AiAssistantRole::getSort)
                .orderByDesc(AiAssistantRole::getId);
        if (StringUtils.hasText(keyword)) {
            String search = keyword.trim();
            query.and(wrapper -> wrapper
                    .like(AiAssistantRole::getName, search)
                    .or().like(AiAssistantRole::getDescription, search));
        }
        return assistantRoleMapper.selectPage(new Page<>(safeCurrent(current), safeSize(size)), query)
                .convert(AiAssistantRoleService::toResponse);
    }

    public AiAssistantRoleDtos.Response get(Long id) {
        AiAssistantRole role = assistantRoleMapper.selectById(id);
        if (role == null) throw new BizException("角色不存在");
        return toResponse(role);
    }

    @Transactional
    public AiAssistantRoleDtos.Response create(AiAssistantRoleDtos.Request request) {
        validate(request);
        ensureUniqueName(request.getName().trim(), null);
        AiAssistantRole role = new AiAssistantRole();
        apply(request, role);
        LocalDateTime now = LocalDateTime.now();
        role.setCreateTime(now);
        role.setUpdateTime(now);
        if (assistantRoleMapper.insert(role) != 1) {
            throw new IllegalStateException("创建角色失败");
        }
        return toResponse(role);
    }

    @Transactional
    public AiAssistantRoleDtos.Response update(Long id, AiAssistantRoleDtos.Request request) {
        validate(request);
        AiAssistantRole role = assistantRoleMapper.selectById(id);
        if (role == null) throw new BizException("角色不存在");
        ensureUniqueName(request.getName().trim(), id);
        apply(request, role);
        role.setUpdateTime(LocalDateTime.now());
        if (assistantRoleMapper.updateById(role) != 1) {
            throw new IllegalStateException("保存角色失败");
        }
        return toResponse(role);
    }

    @Transactional
    public void delete(Long id) {
        AiAssistantRole role = assistantRoleMapper.selectById(id);
        if (role == null) throw new BizException("角色不存在");
        Long referenced = assistantMapper.selectCount(
                new LambdaQueryWrapper<AiAssistant>().eq(AiAssistant::getRoleId, id));
        if (referenced != null && referenced > 0) {
            throw new BizException("该角色正被智能体引用，不能删除");
        }
        if (assistantRoleMapper.deleteById(id) != 1) {
            throw new IllegalStateException("删除角色失败");
        }
    }

    private void ensureUniqueName(String name, Long excludedId) {
        LambdaQueryWrapper<AiAssistantRole> query = new LambdaQueryWrapper<AiAssistantRole>()
                .eq(AiAssistantRole::getName, name);
        if (excludedId != null) query.ne(AiAssistantRole::getId, excludedId);
        if (assistantRoleMapper.selectCount(query) > 0) {
            throw new BizException("角色名称已存在");
        }
    }

    private static void apply(AiAssistantRoleDtos.Request request, AiAssistantRole role) {
        role.setName(request.getName().trim());
        role.setDescription(request.getDescription() == null ? "" : request.getDescription().trim());
        role.setSystemPrompt(request.getSystemPrompt() == null ? "" : request.getSystemPrompt().trim());
        role.setEnabled(request.getEnabled() == null ? Boolean.TRUE : request.getEnabled());
        role.setSort(request.getSort() == null ? 0 : request.getSort());
    }

    private static void validate(AiAssistantRoleDtos.Request request) {
        if (request == null || !StringUtils.hasText(request.getName()))
            throw new BizException("角色名称不能为空");
        if (request.getName().trim().length() > 64) throw new BizException("角色名称不能超过 64 个字符");
        if (request.getDescription() != null && request.getDescription().trim().length() > 512)
            throw new BizException("角色简介不能超过 512 个字符");
    }

    private static AiAssistantRoleDtos.Response toResponse(AiAssistantRole role) {
        AiAssistantRoleDtos.Response response = new AiAssistantRoleDtos.Response();
        response.setId(role.getId());
        response.setName(role.getName());
        response.setDescription(role.getDescription());
        response.setSystemPrompt(role.getSystemPrompt());
        response.setEnabled(role.getEnabled());
        response.setSort(role.getSort());
        response.setCreateTime(role.getCreateTime());
        response.setUpdateTime(role.getUpdateTime());
        return response;
    }

    private static long safeCurrent(long current) {
        return Math.max(1, current);
    }

    private static long safeSize(long size) {
        return Math.min(100, Math.max(1, size));
    }
}
