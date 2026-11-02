package com.csagent.server.controller;

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
 */
@Slf4j
@RestController
@RequestMapping("/api/knowledge")
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
            return Result.success(knowledgeService.upload(file));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
    }

    /**
     * 文档列表
     */
    @GetMapping("/docs")
    public Result<List<KnowledgeDoc>> list() {
        return Result.success(knowledgeService.list());
    }

    /**
     * 删除文档
     */
    @PostMapping("/delete")
    public Result<Void> delete(@RequestParam("docId") String docId) {
        knowledgeService.delete(docId);
        return Result.success();
    }

    /**
     * 检索测试：返回 Top-K 命中知识块
     */
    @GetMapping("/search")
    public Result<List<VectorEntry>> search(@RequestParam("query") String query,
                                            @RequestParam(value = "k", defaultValue = "3") int k) {
        return Result.success(knowledgeService.search(query, k));
    }
}
