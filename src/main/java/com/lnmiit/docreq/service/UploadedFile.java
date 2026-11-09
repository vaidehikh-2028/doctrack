package com.lnmiit.docreq.service;

import com.lnmiit.docreq.domain.RequestValidator;

import java.util.Arrays;

/** A file received from the web layer, decoupled from Spring's MultipartFile. */
public record UploadedFile(String fileName, byte[] bytes) {

    public RequestValidator.AttachmentCandidate toCandidate() {
        return new RequestValidator.AttachmentCandidate(fileName, bytes.length,
                Arrays.copyOf(bytes, Math.min(bytes.length, 8)));
    }

    /** Content type derived from the (already validated) extension, never trusted from the client. */
    public String contentType() {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    public String safeFileName() {
        String base = fileName == null ? "file" : fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        return base.length() > 100 ? base.substring(base.length() - 100) : base;
    }
}
