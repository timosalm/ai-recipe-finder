package com.example.semantickernel;

import com.microsoft.semantickernel.data.vectorstorage.annotations.VectorStoreRecordKey;
import com.microsoft.semantickernel.data.vectorstorage.annotations.VectorStoreRecordVector;
import com.microsoft.semantickernel.data.vectorstorage.definition.DistanceFunction;
import com.microsoft.semantickernel.data.vectorstorage.definition.IndexKind;

import java.util.ArrayList;
import java.util.List;

public class DocumentEmbedding {

	@VectorStoreRecordKey
	private String id;
	@VectorStoreRecordVector(dimensions = 1536, indexKind = IndexKind.HNSW, distanceFunction = DistanceFunction.COSINE_DISTANCE)
	private List<Float> embedding;

	public DocumentEmbedding() {
		embedding = new ArrayList<>();
	}

	public DocumentEmbedding(String id, List<Float> embedding) {
		this.id = id;
		this.embedding = embedding;
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
}
