package com.example.ratingsystem.answerimport;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static com.example.ratingsystem.answerimport.AnswerImportDtos.AnswerImportPreview;
import static com.example.ratingsystem.answerimport.AnswerImportDtos.ImportStructureRequest;

@RestController
@RequestMapping("/api/answer-import")
public class AnswerImportController {

    private final DocxAnswerImportService importService;

    public AnswerImportController(DocxAnswerImportService importService) {
        this.importService = importService;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    AnswerImportPreview preview(@RequestPart("file") MultipartFile file,
                                @Valid @RequestPart("structure") ImportStructureRequest structure) {
        return importService.preview(file, structure);
    }
}
