package com.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return Result.fail("图片不能为空");
        }
        try {
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            // 生成新文件名
            String fileName = createNewFileName(originalFilename);
            if (fileName == null) {
                return Result.fail("不支持的图片格式");
            }
            // 保存文件
            Path target = resolveBlogImagePath(fileName);
            Files.createDirectories(target.getParent());
            image.transferTo(target.toFile());
            // 返回结果
            log.debug("文件上传成功，{}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        Path file = resolveBlogImagePath(filename);
        if (file == null || Files.isDirectory(file)) {
            return Result.fail("错误的文件名称");
        }
        try {
            return Files.deleteIfExists(file) ? Result.ok() : Result.fail("文件不存在");
        } catch (IOException e) {
            throw new RuntimeException("文件删除失败", e);
        }
    }

    private String createNewFileName(String originalFilename) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        if (StrUtil.isBlank(suffix) || !suffix.matches("(?i)jpg|jpeg|png|gif|webp")) {
            return null;
        }
        // 生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 生成文件名
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }

    private Path resolveBlogImagePath(String filename) {
        if (StrUtil.isBlank(filename)) {
            return null;
        }
        Path uploadRoot = Paths.get(SystemConstants.IMAGE_UPLOAD_DIR).toAbsolutePath().normalize();
        Path blogRoot = uploadRoot.resolve("blogs").normalize();
        String relativeName = filename.replace('\\', '/');
        while (relativeName.startsWith("/")) {
            relativeName = relativeName.substring(1);
        }
        Path target = uploadRoot.resolve(relativeName).normalize();
        if (target.equals(blogRoot) || !target.startsWith(blogRoot)) {
            return null;
        }
        return target;
    }
}
