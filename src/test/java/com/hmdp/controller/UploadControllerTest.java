package com.hmdp.controller;

import com.hmdp.dto.Result;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertFalse;

class UploadControllerTest {

    private final UploadController controller = new UploadController();

    @Test
    void rejectsPathTraversalWhenDeletingImage() {
        Result result = controller.deleteBlogImg("/blogs/../../pom.xml");

        assertFalse(result.getSuccess());
    }

    @Test
    void rejectsUnsupportedUploadExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "payload.exe", "application/octet-stream", "payload".getBytes());

        Result result = controller.uploadImage(file);

        assertFalse(result.getSuccess());
    }
}
