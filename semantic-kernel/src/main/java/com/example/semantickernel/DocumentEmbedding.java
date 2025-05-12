package com.example.semantickernel;

import com.microsoft.semantickernel.data.textsearch.TextSearchResultValue;
import com.microsoft.semantickernel.data.vectorstorage.annotations.VectorStoreRecordData;
import com.microsoft.semantickernel.data.vectorstorage.annotations.VectorStoreRecordKey;
import com.microsoft.semantickernel.data.vectorstorage.annotations.VectorStoreRecordVector;
import com.microsoft.semantickernel.data.vectorstorage.definition.DistanceFunction;
import com.microsoft.semantickernel.data.vectorstorage.definition.IndexKind;

import java.util.ArrayList;
import java.util.List;

public class DocumentEmbedding {

	public final static int DIMENSIONS = 1536;

	@VectorStoreRecordKey
	private String id;
	@VectorStoreRecordVector(dimensions = DIMENSIONS, indexKind = IndexKind.HNSW, distanceFunction = DistanceFunction.COSINE_DISTANCE)
	private List<Float> embedding;

	@TextSearchResultValue
	@VectorStoreRecordData
	private String content;

	public DocumentEmbedding() {
		embedding = new ArrayList<>();
	}

	public DocumentEmbedding(String id, List<Float> embedding, String content) {
		this.id = id;
		this.embedding = embedding;
		this.content = content;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public List<Float> getEmbedding() {
		return embedding;
	}

	public void setEmbedding(List<Float> embedding) {
		this.embedding = embedding;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
}
