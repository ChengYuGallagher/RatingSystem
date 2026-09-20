package com.example.ratingsystem.examimport;

import com.example.ratingsystem.examimport.ExamPaperImportDtos.ExamDraftView;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/exam-import")
public class ExamPaperImportController {

    private final ExamPaperImportService service;

    public ExamPaperImportController(ExamPaperImportService service) {
        this.service = service;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ExamDraftView preview(@RequestPart("examFile") MultipartFile examFile,
                          @RequestPart(value = "referenceFile", required = false) MultipartFile referenceFile) {
        return service.preview(examFile, referenceFile);
    }
}
