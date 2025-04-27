package io.github.pheonixhkbxoic.a2a4j.examples.ragagent.controller;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.github.pheonixhkbxoic.a2a4j.core.util.Util;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * @author PheonixHkbxoic
 * @date 2025/4/18 23:33
 * @desc
 */
@Controller
public class IndexController {
    @Resource
    EmbeddingStoreIngestor embeddingStoreIngestor;

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
    
}
