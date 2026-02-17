package com.nganyaexperience.backend.controller;

import com.nganyaexperience.backend.entity.Nganya;
import com.nganyaexperience.backend.repository.NganyaRepository;
import com.nganyaexperience.backend.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin("*")
public class NganyaController {

    private final NganyaRepository nganyaRepository;
    private final CloudinaryService cloudinaryService;

    // ✅ GET ALL NGANYAS
    @GetMapping("/nganyas")
    public List<Nganya> getNganyas() {
        return nganyaRepository.findAll();
    }

    // ✅ CREATE NGANYA (WITH IMAGE UPLOAD)
    @PostMapping("/admin/nganyas")
    public Nganya createNganya(
            @RequestParam String name,
            @RequestParam String size,
            @RequestParam(required = false) MultipartFile image
    ) {
        String imageUrl = null;

        if (image != null && !image.isEmpty()) {
            imageUrl = cloudinaryService.uploadNganyaImage(image);
        }

        Nganya nganya = Nganya.builder()
                .name(name)
                .size(size)
                .imageUrl(imageUrl) // 🔥 FULL CLOUDINARY URL
                .build();

        return nganyaRepository.save(nganya);
    }

    // ✅ DELETE NGANYA
    @DeleteMapping("/admin/nganyas/{id}")
    public ResponseEntity<?> deleteNganya(@PathVariable Long id) {

        Nganya nganya = nganyaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Nganya not found"));

        cloudinaryService.deleteImage(nganya.getImageUrl());
        nganyaRepository.delete(nganya);

        return ResponseEntity.ok().build();
    }
}
