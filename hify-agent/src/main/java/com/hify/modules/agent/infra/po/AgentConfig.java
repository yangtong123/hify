package com.hify.modules.agent.infra.po;

import lombok.Data;

import java.util.List;

@Data
public class AgentConfig {

    /** 开场白 */
    private String openingMessage;

    /** 建议问题列表 */
    private List<String> suggestedQuestions;

    /** 最大迭代次数，默认 20 */
    private Integer maxIterations;
}
