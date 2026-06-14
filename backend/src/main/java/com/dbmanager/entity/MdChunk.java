package com.dbmanager.entity;

import java.time.LocalDateTime;

public class MdChunk {
    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String chunkType;      // structured_data / semantic_content
    private String chunkTitle;
    private Integer contentStartPos;
    private Integer contentEndPos;
    private String chunkContent;
    private String classificationReason;
    private Boolean humanModified;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getChunkType() { return chunkType; }
    public void setChunkType(String chunkType) { this.chunkType = chunkType; }
    public String getChunkTitle() { return chunkTitle; }
    public void setChunkTitle(String chunkTitle) { this.chunkTitle = chunkTitle; }
    public Integer getContentStartPos() { return contentStartPos; }
    public void setContentStartPos(Integer contentStartPos) { this.contentStartPos = contentStartPos; }
    public Integer getContentEndPos() { return contentEndPos; }
    public void setContentEndPos(Integer contentEndPos) { this.contentEndPos = contentEndPos; }
    public String getChunkContent() { return chunkContent; }
    public void setChunkContent(String chunkContent) { this.chunkContent = chunkContent; }
    public String getClassificationReason() { return classificationReason; }
    public void setClassificationReason(String classificationReason) { this.classificationReason = classificationReason; }
    public Boolean getHumanModified() { return humanModified; }
    public void setHumanModified(Boolean humanModified) { this.humanModified = humanModified; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
