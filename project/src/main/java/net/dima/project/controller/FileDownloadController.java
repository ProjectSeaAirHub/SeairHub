package net.dima.project.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Controller
public class FileDownloadController {

    // application.properties에 설정된 파일 저장 경로를 가져옵니다.
    @Value("${file.upload-dir}")
    private String uploadDir;

    @GetMapping("/download/license/{fileName:.+}")
    public ResponseEntity<Resource> downloadLicense(@PathVariable String fileName) {
        try {
            // 파일 경로를 설정합니다.
            Path filePath = Paths.get(uploadDir).resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists()) {
                // 파일이 존재하면, 다운로드할 수 있도록 사용자에게 보냅니다.
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                // 파일이 없으면 404 Not Found 응답을 보냅니다.
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}