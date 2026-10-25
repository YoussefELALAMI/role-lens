package com.rolelens.api.job.controller;

import com.rolelens.api.job.dto.CvUploadResponse;
import com.rolelens.api.job.service.CvService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

@RestController
@RequestMapping("/api/cv")
public class CvController {

    private final CvService service;

    public CvController(CvService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CvUploadResponse> uploadCv(
            @RequestParam("file") MultipartFile file
            ) {
        CvUploadResponse response = service.uploadCV(file);

        return ResponseEntity
                .created(URI.create("/api/cv/" + response.id()))
                .body(response);
    }
}
