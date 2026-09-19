package com.example.ratingsystem.batchimport;

import com.example.ratingsystem.batchimport.BatchImportDtos.BatchImportExecutionView;
import com.example.ratingsystem.batchimport.BatchImportDtos.BatchImportView;
import com.example.ratingsystem.batchimport.BatchImportDtos.BatchStudentView;
import com.example.ratingsystem.batchimport.BatchImportDtos.ConfirmBatchStudentRequest;
import com.example.ratingsystem.batchimport.BatchImportDtos.UpdateBatchStudentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class BatchAnswerImportController {

    private final BatchAnswerImportService service;

    public BatchAnswerImportController(BatchAnswerImportService service) {
        this.service = service;
    }

    @PostMapping(value = "/exams/{examId}/answer-import/batches/preview",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    BatchImportView preview(@PathVariable Long examId, @RequestPart("file") MultipartFile file) {
        return service.preview(examId, file);
    }

    @GetMapping("/answer-import/batches/{batchId}")
    BatchImportView getBatch(@PathVariable Long batchId) {
        return service.getBatch(batchId);
    }

    @GetMapping("/exams/{examId}/answer-import/batches/latest")
    BatchImportView getLatestBatch(@PathVariable Long examId) {
        return service.getLatestBatch(examId);
    }

    @PutMapping("/answer-import/batches/{batchId}/students/{studentImportId}")
    BatchStudentView updateStudent(@PathVariable Long batchId,
                                   @PathVariable Long studentImportId,
                                   @Valid @RequestBody UpdateBatchStudentRequest request) {
        return service.updateStudent(batchId, studentImportId, request);
    }

    @PostMapping("/answer-import/batches/{batchId}/students/{studentImportId}/confirm")
    BatchStudentView confirmStudent(@PathVariable Long batchId,
                                    @PathVariable Long studentImportId,
                                    @Valid @RequestBody ConfirmBatchStudentRequest request) {
        return service.confirmStudent(batchId, studentImportId, request);
    }

    @PostMapping("/answer-import/batches/{batchId}/import")
    BatchImportExecutionView importConfirmed(@PathVariable Long batchId) {
        return service.importConfirmed(batchId);
    }
}
