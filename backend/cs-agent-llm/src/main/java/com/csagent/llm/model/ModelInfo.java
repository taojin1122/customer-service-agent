package com.csagent.llm.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型注册信息，用于前端展示与路由
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfo {

    /** 模型 id */
    private String id;

    /** 展示名称 */
    private String name;

    /** 实际模型名 */
    private String model;

    /** 是否为当前默认模型 */
    private boolean defaultModel;
}
