package semicolon.africa.waylchub.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import semicolon.africa.waylchub.dto.campaignDto.*;
import semicolon.africa.waylchub.service.campaign.CampaignService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/campaigns")
@RequiredArgsConstructor
 @PreAuthorize("hasRole('ROLE_ADMIN')")
public class CampaignController {

    private final CampaignService campaignService;

    @PostMapping
    public ResponseEntity<CampaignResponse> create(@Valid @RequestBody CampaignRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(campaignService.createCampaign(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CampaignResponse> update(
            @PathVariable String id, @Valid @RequestBody CampaignRequest req) {
        return ResponseEntity.ok(campaignService.updateCampaign(id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        campaignService.deleteCampaign(id);
        return ResponseEntity.noContent().build();
    }

    /** Admin can start a campaign immediately without waiting for startDate. */
    @PostMapping("/{id}/activate")
    public ResponseEntity<CampaignResponse> activate(@PathVariable String id) {
        return ResponseEntity.ok(campaignService.manualActivate(id));
    }

    /** Admin can end a campaign early. */
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<CampaignResponse> deactivate(@PathVariable String id) {
        return ResponseEntity.ok(campaignService.manualDeactivate(id));
    }

    @GetMapping
    public ResponseEntity<List<CampaignResponse>> getAll() {
        return ResponseEntity.ok(campaignService.getAllCampaigns());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampaignResponse> getOne(@PathVariable String id) {
        return ResponseEntity.ok(campaignService.getCampaign(id));
    }
}