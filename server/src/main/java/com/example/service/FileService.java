package com.example.service;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 文件服务 — 上传/下载 Base64 编码的文件。
 *
 * 安全措施：
 * - 上传大小限制 5MB
 * - 仅允许图片类型 (JPEG/PNG/GIF)，通过魔数校验
 * - 文件扩展名从魔数推导，不信任客户端传入的 fileName
 * - 路径标准化 + 前缀检查防止路径穿越
 */
public class FileService {

    private static final int MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/gif");

    // 魔数签名
    private static final byte[] JPEG_MAGIC = {(byte)0xFF, (byte)0xD8, (byte)0xFF};
    private static final byte[] PNG_MAGIC = {(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] GIF_MAGIC = {0x47, 0x49, 0x46, 0x38}; // "GIF8"

    private final Path uploadDir;

    public FileService() {
        uploadDir = Paths.get("uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建上传目录: " + uploadDir, e);
        }
    }

    /** 上传 Base64 编码的图片 */
    public Map<String, String> upload(String base64Data, String fileName) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Data);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("无效的 Base64 编码");
        }

        // 大小限制
        if (bytes.length > MAX_FILE_SIZE) {
            throw new RuntimeException("文件过大，最大允许 " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
        }
        if (bytes.length == 0) {
            throw new RuntimeException("文件内容为空");
        }

        // 通过魔数确定文件类型
        String ext = detectExtension(bytes);
        if (ext == null) {
            throw new RuntimeException("不支持的文件类型，仅允许 JPEG/PNG/GIF 图片");
        }

        String uuidName = UUID.randomUUID().toString() + ext;
        Path target = uploadDir.resolve(uuidName).normalize();

        // 路径穿越检查
        if (!target.startsWith(uploadDir)) {
            throw new RuntimeException("非法文件路径");
        }

        try {
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new RuntimeException("写入文件失败: " + e.getMessage(), e);
        }
        Map<String, String> result = new HashMap<>();
        result.put("url", "/uploads/" + uuidName);
        return result;
    }

    /** 下载图片，返回 Base64 字符串 */
    public Map<String, String> download(String path) {
        // 从路径中提取文件名
        String fileName = Paths.get(path).getFileName().toString();
        Path file = uploadDir.resolve(fileName).normalize();

        // 路径穿越检查
        if (!file.startsWith(uploadDir)) {
            throw new RuntimeException("非法路径");
        }
        if (!Files.exists(file)) {
            throw new RuntimeException("文件不存在: " + fileName);
        }

        try {
            byte[] bytes = Files.readAllBytes(file);
            String contentType = Files.probeContentType(file);
            if (contentType == null) contentType = "image/jpeg";
            Map<String, String> result = new HashMap<>();
            result.put("base64", Base64.getEncoder().encodeToString(bytes));
            result.put("contentType", contentType);
            return result;
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败: " + e.getMessage(), e);
        }
    }

    /** 通过魔数检测文件类型，返回对应的扩展名 */
    private static String detectExtension(byte[] bytes) {
        if (startsWith(bytes, JPEG_MAGIC)) return ".jpg";
        if (startsWith(bytes, PNG_MAGIC)) return ".png";
        if (startsWith(bytes, GIF_MAGIC)) return ".gif";
        return null;
    }

    private static boolean startsWith(byte[] bytes, byte[] magic) {
        if (bytes.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (bytes[i] != magic[i]) return false;
        }
        return true;
    }
}
