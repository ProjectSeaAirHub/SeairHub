package net.dima.project.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import lombok.RequiredArgsConstructor;
import net.dima.project.dto.BlDto;
import net.dima.project.service.RequestService;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.core.Authentication;
import java.nio.charset.StandardCharsets;

@Controller
@RequiredArgsConstructor
public class FileDownloadController {
	private final RequestService requestService; // RequestService 주입

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
    
    /**
     * B/L(선하증권) 텍스트 파일을 생성하고 다운로드하는 API
     */
    @GetMapping("/download/bl/{requestId}")
    public ResponseEntity<Resource> downloadBl(@PathVariable Long requestId, Authentication authentication) {
        try {
            // 1. 현재 로그인한 사용자 정보를 가져옵니다.
            String currentUserId = authentication.getName();
            
            // 2. RequestService를 통해 B/L 정보를 조회합니다. (이때 권한 검사도 함께 수행됨)
            BlDto blInfo = requestService.getBlInfo(requestId, currentUserId);

            // 3. 전달받은 B/L DTO를 사용하여 텍스트 파일 내용을 구성합니다.
            String content = buildBlContent(blInfo);
            
            // 4. 생성된 텍스트 내용을 byte 배열로 변환합니다.
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            ByteArrayResource resource = new ByteArrayResource(contentBytes);

            // 5. 다운로드할 파일 이름을 설정합니다.
            String fileName = "BL_" + blInfo.getBlNo() + ".txt";
            
            // 6. HTTP 응답 헤더를 설정하여 파일 다운로드를 처리합니다.
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.TEXT_PLAIN)
                    .contentLength(contentBytes.length)
                    .body(resource);

        } catch (Exception e) {
            // B/L 정보 조회 실패 또는 파일 생성 실패 시 오류 응답
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * BlDto 객체를 기반으로 B/L 텍스트 파일의 전체 내용을 생성하는 헬퍼 메서드
     */
    private String buildBlContent(BlDto dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("==============================================\n");
        sb.append("              BILL OF LADING (DRAFT)            \n");
        sb.append("==============================================\n\n");
        sb.append("B/L No: ").append(dto.getBlNo()).append("\n");
        sb.append("Issue Date: ").append(dto.getIssueDate()).append("\n\n");
        sb.append("----------------------------------------------\n");
        sb.append(" SHIPPER / EXPORTER\n");
        sb.append("----------------------------------------------\n");
        sb.append("Name: ").append(dto.getShipperName()).append("\n");
        sb.append("Address: ").append(dto.getShipperAddress()).append("\n\n");
        sb.append("----------------------------------------------\n");
        sb.append(" CONSIGNEE\n");
        sb.append("----------------------------------------------\n");
        sb.append("Name: ").append(dto.getConsigneeName()).append("\n");
        sb.append("Address: ").append(dto.getConsigneeAddress()).append("\n\n");
        sb.append("----------------------------------------------\n");
        sb.append(" FORWARDING AGENT\n");
        sb.append("----------------------------------------------\n");
        sb.append("Name: ").append(dto.getForwarderName()).append("\n\n");
        sb.append("----------------------------------------------\n");
        sb.append(" VESSEL & VOYAGE INFORMATION\n");
        sb.append("----------------------------------------------\n");
        sb.append("Vessel Name: ").append(dto.getVesselName()).append("\n");
        sb.append("IMO Number: ").append(dto.getImoNumber()).append("\n");
        sb.append("Port of Loading: ").append(dto.getPortOfLoading()).append("\n");
        sb.append("Port of Discharge: ").append(dto.getPortOfDischarge()).append("\n\n");
        sb.append("----------------------------------------------\n");
        sb.append(" CARGO & CONTAINER DETAILS\n");
        sb.append("----------------------------------------------\n");
        sb.append("Container No: ").append(dto.getContainerNo()).append("\n");
        sb.append("Description of Goods: ").append(dto.getDescriptionOfGoods()).append("\n");
        sb.append("CBM (Cubic Meter): ").append(String.format("%.2f", dto.getCbm())).append("\n\n");
        sb.append("==============================================\n");
        sb.append("              END OF DOCUMENT                 \n");
        sb.append("==============================================\n");
        return sb.toString();
    }
}