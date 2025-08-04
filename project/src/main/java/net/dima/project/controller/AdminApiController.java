package net.dima.project.controller;

import lombok.RequiredArgsConstructor;
import net.dima.project.dto.DashboardMetricsDto;
import net.dima.project.dto.VolumeDto;
import net.dima.project.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map; // import 추가

@RestController
@RequestMapping("/api/adm")
@RequiredArgsConstructor
public class AdminApiController {

    private final AdminService adminService;

    @GetMapping("/volumes")
    public ResponseEntity<VolumeDto> getVolumes() {
        VolumeDto volumeData = adminService.getSystemVolume();
        return ResponseEntity.ok(volumeData);
    }
    
    // [추가] 대시보드 전체 데이터 API
    @GetMapping("/dashboard-metrics")
    public ResponseEntity<DashboardMetricsDto> getDashboardMetrics() {
        DashboardMetricsDto metrics = adminService.getDashboardMetrics();
        return ResponseEntity.ok(metrics);
    }
    
    // [추가] 포워더 상태 변경 API
    @PostMapping("/forwarders/{userSeq}/status")
    public ResponseEntity<String> updateUserStatus(@PathVariable("userSeq") Integer userSeq, @RequestBody Map<String, String> payload) {
        try {
            String status = payload.get("status");
            adminService.updateUserStatus(userSeq, status);
            return ResponseEntity.ok("상태가 성공적으로 변경되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}