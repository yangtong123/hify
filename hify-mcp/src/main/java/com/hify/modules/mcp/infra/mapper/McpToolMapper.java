package com.hify.modules.mcp.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hify.modules.mcp.infra.po.McpToolPo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface McpToolMapper extends BaseMapper<McpToolPo> {

    @Delete("DELETE FROM t_mcp_tool WHERE mcp_server_id = #{mcpServerId}")
    int deletePhysicallyByServerId(@Param("mcpServerId") Long mcpServerId);
}
