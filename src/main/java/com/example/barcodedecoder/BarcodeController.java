package com.example.barcodedecoder;

import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@CrossOrigin(origins = "*") // Allows frontend to call this API
public class BarcodeController {
     public BarcodeController() {
        System.out.println("✅ BarcodeController Loaded");
    }
    @PostMapping("/decode")
    public ResponseEntity<String> decodeBarcode(@RequestParam("image") MultipartFile file) {
        System.out.println("Received file: " + file.getOriginalFilename());
        System.out.println("Content type: " + file.getContentType());
        System.out.println("Size: " + file.getSize());

        try {
            BufferedImage img = ImageIO.read(file.getInputStream());
            if (img == null) {
                System.out.println("⚠️ ImageIO.read returned null");
                return ResponseEntity.badRequest().body("Invalid image file");
            }

            String result = Code39Decoder.interpretBarcode(img);
            return ResponseEntity.ok(result.isEmpty() ? "Could not decode" : result);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error processing image");
        }
    }
}
