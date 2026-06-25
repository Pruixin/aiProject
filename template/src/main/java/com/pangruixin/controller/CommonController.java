package com.pangruixin.controller;

import com.pangruixin.common.R;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/common")
public class CommonController {

    // 公共上传接口目前只允许图片类型，供注册、用户头像、动态图片等场景复用。
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @PostMapping("/upload")
    public R<Map<String, String>> upload(MultipartFile file) throws IOException {
        // MultipartFile 为空或内容为空，通常表示前端未真正选中文件。
        if (file == null || file.isEmpty()) {
            return R.error("请先选择图片");
        }

        // 同时校验扩展名和 MIME 类型，降低伪装文件上传的风险。
        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);
        String normalizedExtension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        String contentType = file.getContentType();
        boolean imageContentType = StringUtils.hasText(contentType) && contentType.toLowerCase(Locale.ROOT).startsWith("image/");
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(normalizedExtension) || !imageContentType) {
            return R.error("仅支持上传 jpg、png、webp、gif 图片");
        }
        // 使用 UUID 重命名，避免同名文件覆盖和目录遍历风险。
        String fileName = UUID.randomUUID().toString().replace("-", "");
        if (StringUtils.hasText(normalizedExtension)) {
            fileName = fileName + "." + normalizedExtension;
        }

        // 上传目录不存在时自动创建，并返回统一的 `/uploads/xxx` 访问路径。
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);

        Path targetFile = uploadPath.resolve(fileName);
        // 统一覆盖式写入目标路径；由于文件名是 UUID，正常情况下不会冲突。
        Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);

        Map<String, String> result = new HashMap<>();
        // name 用于前端展示原始文件名，url 用于后续头像/动态图片直接回填。
        result.put("name", originalFilename);
        result.put("url", "/uploads/" + fileName);
        return R.success(result);
    }
}
