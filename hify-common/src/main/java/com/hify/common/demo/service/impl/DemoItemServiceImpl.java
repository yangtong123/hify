package com.hify.common.demo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.demo.dto.DemoItemCreateRequest;
import com.hify.common.demo.dto.DemoItemResponse;
import com.hify.common.demo.dto.DemoItemUpdateRequest;
import com.hify.common.demo.mapper.DemoItemMapper;
import com.hify.common.demo.po.DemoItemPo;
import com.hify.common.demo.service.DemoItemService;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DemoItemServiceImpl implements DemoItemService {

    private final DemoItemMapper mapper;

    @Override
    public DemoItemResponse create(DemoItemCreateRequest request) {
        DemoItemPo po = new DemoItemPo();
        po.setName(request.getName());
        po.setStatus(request.getStatus());
        mapper.insert(po);
        return toResponse(po);
    }

    @Override
    public DemoItemResponse update(DemoItemUpdateRequest request) {
        DemoItemPo po = mapper.selectById(request.getId());
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "DemoItem 不存在: " + request.getId());
        }
        po.setName(request.getName());
        po.setStatus(request.getStatus());
        mapper.updateById(po);
        return toResponse(po);
    }

    @Override
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    @Override
    public DemoItemResponse getById(Long id) {
        DemoItemPo po = mapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "DemoItem 不存在: " + id);
        }
        return toResponse(po);
    }

    @Override
    public PageResult<List<DemoItemResponse>> list(Integer page, Integer pageSize) {
        Page<DemoItemPo> mpPage = PageHelper.toPage(page, pageSize);
        IPage<DemoItemPo> result = mapper.selectPage(mpPage,
                new LambdaQueryWrapper<DemoItemPo>().orderByDesc(DemoItemPo::getCreatedAt));
        List<DemoItemResponse> dtos = result.getRecords().stream()
                .map(this::toResponse).toList();
        return PageResult.ok(dtos, result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    private DemoItemResponse toResponse(DemoItemPo po) {
        DemoItemResponse resp = new DemoItemResponse();
        resp.setId(po.getId());
        resp.setName(po.getName());
        resp.setStatus(po.getStatus());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setUpdatedAt(po.getUpdatedAt());
        return resp;
    }
}
