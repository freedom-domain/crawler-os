package com.collect.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.collect.common.exception.BizException;
import com.collect.user.entity.SysDict;
import com.collect.user.mapper.SysDictMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DictService {

    private final SysDictMapper dictMapper;

    /**
     * 返回树形结构：父项（parent_id=0）及其子项
     */
    @SuppressWarnings("null")
    public List<SysDict> tree() {
        List<SysDict> all = listAll();
        List<SysDict> parents = new ArrayList<>();
        for (SysDict d : all) {
            if (d.getParentId() == null || d.getParentId() == 0) {
                parents.add(d);
            }
        }
        for (SysDict parent : parents) {
            List<SysDict> children = new ArrayList<>();
            for (SysDict d : all) {
                if (parent.getId().equals(d.getParentId())) {
                    children.add(d);
                }
            }
            parent.setChildren(children);
        }
        return parents;
    }

    /**
     * 根据父级 value 获取其子项列表
     */
    @SuppressWarnings("null")
    public List<SysDict> childrenByParentValue(String parentValue) {
        if (parentValue == null || parentValue.isBlank()) {
            return new ArrayList<>();
        }
        // 先根据 value 找到父项
        SysDict parent = dictMapper.selectOne(
                new LambdaQueryWrapper<SysDict>()
                        .eq(SysDict::getValue, parentValue)
                        .eq(SysDict::getStatus, 1)
                        .last("LIMIT 1"));
        if (parent == null) {
            return new ArrayList<>();
        }
        return dictMapper.selectList(
                new LambdaQueryWrapper<SysDict>()
                        .eq(SysDict::getParentId, parent.getId())
                        .eq(SysDict::getStatus, 1)
                        .orderByAsc(SysDict::getSort)
                        .orderByDesc(SysDict::getId));
    }

    @SuppressWarnings("null")
    private List<SysDict> listAll() {
        LambdaQueryWrapper<SysDict> qw = new LambdaQueryWrapper<>();
        qw.eq(SysDict::getStatus, 1);
        qw.orderByAsc(SysDict::getSort).orderByDesc(SysDict::getId);
        return dictMapper.selectList(qw);
    }

    public void create(SysDict dict) {
        if (dict.getLabel() == null || dict.getLabel().isBlank()) {
            throw new BizException("名称不能为空");
        }
        if (dict.getParentId() == null) {
            dict.setParentId(0L);
        }
        if (dict.getSort() == null) {
            dict.setSort(0);
        }
        if (dict.getStatus() == null) {
            dict.setStatus(1);
        }
        if (dict.getParentId() != 0) {
            SysDict parent = dictMapper.selectById(dict.getParentId());
            if (parent == null) {
                throw new BizException("父项不存在");
            }
        }
        dictMapper.insert(dict);
    }

    public void update(Long id, SysDict dict) {
        SysDict exist = dictMapper.selectById(id);
        if (exist == null) {
            throw new BizException("字典项不存在");
        }
        if (dict.getParentId() != null) {
            if (dict.getParentId().equals(id)) {
                throw new BizException("父项不能是自身");
            }
            if (dict.getParentId() != 0) {
                SysDict parent = dictMapper.selectById(dict.getParentId());
                if (parent == null) {
                    throw new BizException("父项不存在");
                }
            }
            exist.setParentId(dict.getParentId());
        }
        if (dict.getLabel() != null && !dict.getLabel().isBlank()) {
            exist.setLabel(dict.getLabel());
        }
        if (dict.getValue() != null) {
            exist.setValue(dict.getValue());
        }
        if (dict.getSort() != null) {
            exist.setSort(dict.getSort());
        }
        if (dict.getStatus() != null) {
            exist.setStatus(dict.getStatus());
        }
        dictMapper.updateById(exist);
    }

    public void delete(Long id) {
        SysDict exist = dictMapper.selectById(id);
        if (exist == null) {
            throw new BizException("字典项不存在");
        }
        @SuppressWarnings("null")
        Long childCount = dictMapper.selectCount(
                new LambdaQueryWrapper<SysDict>().eq(SysDict::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BizException("存在子项，无法删除");
        }
        dictMapper.deleteById(id);
    }
}
