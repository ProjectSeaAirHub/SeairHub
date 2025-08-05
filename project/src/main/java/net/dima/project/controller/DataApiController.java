package net.dima.project.controller;

import lombok.RequiredArgsConstructor;
import net.dima.project.entity.ScfiData;
import net.dima.project.repository.ScfiDataRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DataApiController {

    private final ScfiDataRepository scfiDataRepository;

    @GetMapping("/api/scfi-data")
    public ResponseEntity<List<ScfiData>> getScfiData() {
        List<ScfiData> data = scfiDataRepository.findByOrderByRecordDateAsc();
        return ResponseEntity.ok(data);
    }
}