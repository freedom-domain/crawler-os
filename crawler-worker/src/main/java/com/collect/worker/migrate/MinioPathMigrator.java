package com.collect.worker.migrate;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.collect.worker.entity.FileMetadata;
import com.collect.worker.mapper.FileMetadataMapper;
import com.collect.worker.minio.MinioHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 一次性迁移工具：将 MinIO 对象名中的 "crawler/" 前缀去掉，
 * 即 crawler/html/... -> html/...，crawler/images/... -> images/...，crawler/js/... -> js/...，
 * 并同步更新 file_metadata 表中的 object_name。
 *
 * 迁移后完整路径变为 {bucket=crawler}/{html|images|js}/...，不再出现 crawler/crawler 的视觉重复。
 *
 * 运行方式（在 crawler-worker 模块下）：
 *   mvn spring-boot:run -Dspring-boot.run.arguments="--migrate.minio-path=true"
 * 诊断模式（只统计不迁移）：
 *   mvn spring-boot:run -Dspring-boot.run.arguments="--migrate.minio-path=true --migrate.list-only=true"
 *
 * 迁移完成后该 Runner 自动退出（System.exit），不影响正常启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioPathMigrator implements CommandLineRunner {

    private static final String BUCKET = "crawler";
    /** 需要去掉前缀的目录列表 */
    private static final String[] DIRS = {"html", "images", "js", "file"};

    private final MinioHelper minioHelper;
    private final FileMetadataMapper fileMetadataMapper;

    @org.springframework.beans.factory.annotation.Value("${migrate.minio-path:false}")
    private boolean enabled;

    @org.springframework.beans.factory.annotation.Value("${migrate.list-only:false}")
    private boolean listOnly;

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        if (listOnly) {
            runDiagnose();
            return;
        }
        runMigrate();
    }

    private void runDiagnose() {
        log.info("========== 诊断模式：统计各前缀对象/记录数 ==========");
        try {
            for (String dir : DIRS) {
                int oldCount = minioHelper.listObjectNames(BUCKET, "crawler/" + dir + "/").size();
                int newCount = minioHelper.listObjectNames(BUCKET, dir + "/").size();
                log.info("MinIO [crawler/{}/] 对象数: {}, [/{}/] 对象数: {}", dir, oldCount, dir, newCount);
            }
            log.info("---------- file_metadata object_name 前缀分布 ----------");
            for (String dir : DIRS) {
                long oldCount = fileMetadataMapper.selectCount(
                        new QueryWrapper<FileMetadata>().likeRight("object_name", "crawler/" + dir + "/"));
                long newCount = fileMetadataMapper.selectCount(
                        new QueryWrapper<FileMetadata>().likeRight("object_name", dir + "/"));
                log.info("DB [crawler/{}/] 记录数: {}, [/{}/] 记录数: {}", dir, oldCount, dir, newCount);
            }
        } catch (Exception e) {
            log.error("诊断失败: {}", e.getMessage(), e);
        }
        System.exit(0);
    }

    private void runMigrate() {
        log.info("========== MinIO 路径迁移开始（去掉对象名 crawler/ 前缀）==========");
        int migrated = 0;
        int failed = 0;
        int metaUpdated = 0;

        // 1. 迁移 MinIO 对象：crawler/{dir}/xxx -> {dir}/xxx
        for (String dir : DIRS) {
            String oldPrefix = "crawler/" + dir + "/";
            String newPrefix = dir + "/";
            try {
                List<String> oldObjects = minioHelper.listObjectNames(BUCKET, oldPrefix);
                log.info("目录 [{}]: 发现旧前缀对象 {} 个", dir, oldObjects.size());
                for (String oldName : oldObjects) {
                    String newName = newPrefix + oldName.substring(oldPrefix.length());
                    try {
                        minioHelper.copyObject(BUCKET, oldName, newName);
                        minioHelper.removeObject(BUCKET, oldName);
                        migrated++;
                        if (migrated % 500 == 0) {
                            log.info("已迁移 {} 个对象...", migrated);
                        }
                    } catch (Exception e) {
                        failed++;
                        log.warn("迁移对象失败: {} -> {}: {}", oldName, newName, e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("列出目录 [{}] 旧前缀对象失败: {}", dir, e.getMessage(), e);
            }
        }

        // 2. 更新 file_metadata 表中的 object_name
        for (String dir : DIRS) {
            String oldPrefix = "crawler/" + dir + "/";
            String newPrefix = dir + "/";
            try {
                List<FileMetadata> metas = fileMetadataMapper.selectList(
                        new QueryWrapper<FileMetadata>().likeRight("object_name", oldPrefix));
                log.info("目录 [{}]: 发现旧前缀元数据 {} 条", dir, metas.size());
                for (FileMetadata meta : metas) {
                    String newName = newPrefix + meta.getObjectName().substring(oldPrefix.length());
                    meta.setObjectName(newName);
                    fileMetadataMapper.updateById(meta);
                    metaUpdated++;
                }
            } catch (Exception e) {
                log.error("更新目录 [{}] 元数据失败: {}", dir, e.getMessage(), e);
            }
        }

        log.info("========== MinIO 路径迁移完成: 对象迁移 {} 个, 失败 {} 个, 元数据更新 {} 条 ==========",
                migrated, failed, metaUpdated);
        System.exit(failed > 0 ? 1 : 0);
    }
}
