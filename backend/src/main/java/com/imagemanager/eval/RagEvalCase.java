package com.imagemanager.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条 RAG 评测题。example=true 且题干含 EXAMPLE 的是随仓库提交的示例，不是业务数据。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RagEvalCase {
    public String question = "";
    public List<String> expectedFacts = new ArrayList<>();
    public List<String> keywords = new ArrayList<>();
    public List<String> expectedSourceIds = new ArrayList<>();
    public String huohao;
    public boolean shouldRefuse;
    public String company;
    public boolean example;
    public String comment;
}
