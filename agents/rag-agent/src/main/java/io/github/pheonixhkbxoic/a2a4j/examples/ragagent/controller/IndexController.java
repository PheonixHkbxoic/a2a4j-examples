package io.github.pheonixhkbxoic.a2a4j.examples.ragagent.controller;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.github.pheonixhkbxoic.a2a4j.core.core.TaskStore;
import io.github.pheonixhkbxoic.a2a4j.core.spec.entity.*;
import io.github.pheonixhkbxoic.a2a4j.core.util.Util;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/18 23:33
 * @desc
 */
@Slf4j
@Controller
public class IndexController {
    @Resource
    EmbeddingStoreIngestor embeddingStoreIngestor;
    @Resource
    TaskStore taskStore;

    @ResponseBody
    @GetMapping("/ingest")
    public ResponseEntity<Object> ingest(@RequestParam("file") MultipartFile file) {
        String name = file.getOriginalFilename();
        if (Util.isEmpty(name) || !name.endsWith(".txt")) {
            return ResponseEntity.badRequest().body("only accept txt file");
        }
        DocumentParser documentParser = new TextDocumentParser();
        try {
            Document document = documentParser.parse(file.getInputStream());
            embeddingStoreIngestor.ingest(document);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    @ResponseBody
    @GetMapping("/redis/test")
    public ResponseEntity<Task> testRedis() {
        try {
            String sessionId = "test-001";
            List<Part> parts = List.of(new TextPart("hello world"));
            Artifact artifact = Artifact.builder().parts(parts).build();
            Task task = Task.builder()
                    .id("1")
                    .sessionId(sessionId)
                    .status(new TaskStatus(TaskState.WORKING))
                    .artifacts(List.of(artifact))
                    .metadata(Map.of("k", "v", "kk", 12345))
                    .build();
            taskStore.insert(task);

            TaskStatus status = new TaskStatus(TaskState.COMPLETED);
            task.setStatus(status);
            taskStore.update(task);

            Task taskCompleted = taskStore.query(task.getId());
            return ResponseEntity.ok(taskCompleted);
        } catch (Exception e) {
            log.error("test redis exception: {}", e.getMessage(), e);
        }
        return ResponseEntity.internalServerError().build();
    }

}
