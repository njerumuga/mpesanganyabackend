package com.nganyaexperience.backend.service;

import com.cloudinary.Cloudinary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    // ---------- EVENT POSTER ----------
    public String uploadEventPoster(MultipartFile file) {
        return upload(file, "events");
    }

    // ---------- NGANYA IMAGE ----------
    public String uploadNganyaImage(MultipartFile file) {
        return upload(file, "nganyas");
    }

    private String upload(MultipartFile file, String folder) {
        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    Map.of(
                            "folder", folder,
                            "resource_type", "image"
                    )
            );
            return result.get("secure_url").toString();
        } catch (Exception e) {
            throw new RuntimeException("Image upload failed", e);
        }
    }

    public void deleteImage(String imageUrl) {
        if (imageUrl == null) return;
        try {
            String publicId = imageUrl
                    .substring(imageUrl.indexOf("/upload/") + 8)
                    .replaceAll("^v\\d+/", "")
                    .replaceAll("\\.[a-zA-Z]+$", "");
            cloudinary.uploader().destroy(publicId, Map.of());
        } catch (Exception ignored) {}
    }
}
