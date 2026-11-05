package com.csagent.server.controller;

import com.csagent.common.constant.RoleConstants;
import com.csagent.common.context.RequireRole;
import com.csagent.common.context.TenantContext;
import com.csagent.common.exception.BusinessException;
import com.csagent.common.result.Result;
import com.csagent.rag.service.KnowledgeDoc;
import com.csagent.rag.service.KnowledgeService;
import com.csagent.rag.vector.VectorEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库 API：文档上传、列表、删除、检索测试。
 *
 * <p><b>整个类限定 ADMIN</b>：知识库影响的是该租户<b>所有</b>对话的回答质量。
 * 坐席误删一段文档，全租户的机器人都会开始答错，而且很难定位到是谁改的。
 * 这属于"配置权"而非"操作权"，按 AGENTS.md §9.3 的权限矩阵归管理员。</p>
 *
 * <p>注意：前端菜单同样对 AGENT 隐藏了知识库入口，但那只是体验层。
 * 真正的边界是这里的注解——隐藏菜单挡不住直接调接口的人。</p>
 *
 * <p><b>租户范围一律取自 {@link TenantContext}</b>：四个接口都只作用于当前登录账号所属租户，
 * 不接受任何租户参数。管理员权限是"本租户的管理员"，不是"所有租户的管理员"，
 * 这两者在只做 {@code @RequireRole(ADMIN)} 时看起来一样，但少了 tenantId 就是跨租户越权。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge")
@RequireRole(RoleConstants.ADMIN)
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    /**
     * 上传文档并摄入（解析→分块→向量化→入库）
     */
    @PostMapping("/upload")
    public Result<KnowledgeDoc> upload(@RequestParam("file") MultipartFile file) {
        try {
            return Result.success(knowledgeService.upload(file, TenantContext.requireTenantId()));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
    }

    /**
     * 文档列表（仅本租户）
     */
    @GetMapping("/docs")
    public Result<List<KnowledgeDoc>> list() {
        return Result.success(knowledgeService.list(TenantContext.requireTenantId()));
    }

    /**
     * 删除文档（仅限本租户的文档）
     */
    @PostMapping("/delete")
    public Result<Void> delete(@RequestParam("docId") String docId) {
        knowledgeService.delete(docId, TenantContext.requireTenantId());
        return Result.success();
    }

    /**
     * 检索测试：返回本租户 Top-K 命中知识块
     */
    @GetMapping("/search")
    public Result<List<VectorEntry>> search(@RequestParam("query") String query,
                                            @RequestParam(value = "k", defaultValue = "3") int k) {
        return Result.success(knowledgeService.search(query, k, TenantContext.requireTenantId()));
    }
}
