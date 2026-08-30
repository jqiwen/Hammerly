package com.hammerly.backend.knowledge;

import com.hammerly.backend.knowledge.KnowledgeDtos.CreateDocumentRequest;
import com.hammerly.backend.knowledge.KnowledgeDtos.DocumentResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/knowledge/documents")
public class KnowledgeController {
    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) {
        this.service = service;
    }

    @PostMapping
    ResponseEntity<DocumentResponse> create(@Valid @RequestBody CreateDocumentRequest request) {
        DocumentResponse document = service.create(request);
        return ResponseEntity.accepted()
            .location(URI.create("/internal/knowledge/documents/" + document.id()))
            .body(document);
    }

    @GetMapping("/{id}")
    DocumentResponse get(@PathVariable UUID id) {
        return service.get(id);
    }
}
