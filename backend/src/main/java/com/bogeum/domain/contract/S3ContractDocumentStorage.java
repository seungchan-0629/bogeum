package com.bogeum.domain.contract;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** 계약서 이미지를 계약서 전용 S3 버킷에서 다룬다. 실패 로그와 예외 변환은 ContractService가 맡는다. */
@Component
@ConditionalOnProperty(name = "spring.cloud.aws.s3.enabled", havingValue = "true")
public class S3ContractDocumentStorage implements ContractDocumentStorage {

	private final S3Client s3Client;
	private final String bucket;

	public S3ContractDocumentStorage(S3Client s3Client,
			@Value("${spring.cloud.aws.s3.contract-bucket}") String bucket) {
		this.s3Client = s3Client;
		this.bucket = bucket;
	}

	@Override
	public void write(String fileKey, byte[] jpeg) {
		s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(fileKey).contentType("image/jpeg").build(),
			RequestBody.fromBytes(jpeg));
	}

	@Override
	public void delete(String fileKey) {
		s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(fileKey).build());
	}
}
