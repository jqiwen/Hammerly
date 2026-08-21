package com.hammerly.backend.controller;

import com.hammerly.backend.repository.DatabaseDebugRepository;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = "hammerly.debug-endpoint.enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseDebugController {
    private final DatabaseDebugRepository database;

    public DatabaseDebugController(DatabaseDebugRepository database) {
        this.database = database;
    }

    @GetMapping({"", "/"})
    Map<String, Object> debugDatabase() {
        return database.describeDatabase();
    }
}
