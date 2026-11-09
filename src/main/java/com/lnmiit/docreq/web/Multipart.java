package com.lnmiit.docreq.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnmiit.docreq.domain.DomainException;
import com.lnmiit.docreq.domain.ErrorCode;
import com.lnmiit.docreq.service.UploadedFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/** Helpers for multipart form submissions. */
final class Multipart {

    private Multipart() {}

    /** Browsers send an empty part when no file is chosen; treat that as "no file". */
    static UploadedFile file(MultipartFile part) {
        if (part == null || part.isEmpty()) {
            return null;
        }
        try {
            return new UploadedFile(part.getOriginalFilename(), part.getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Parses the {"field name": "value"} JSON sent in the "fields" part. */
    static Map<String, String> fields(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (IOException e) {
            throw new DomainException(ErrorCode.GEN_BAD_INPUT, "The form fields could not be read.");
        }
    }
}
