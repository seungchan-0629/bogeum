package com.bogeum.domain.aftersales;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/** AS 사진을 S3 버킷에 저장한다. 실패 로그와 예외 변환은 AfterSalesService가 맡는다. */
@Component
@ConditionalOnProperty(name = "spring.cloud.aws.s3.enabled", havingValue = "true")
public class S3AfterSalesImageStorage implements AfterSalesImageStorage {
	// 비공개 버킷이라 상세 조회마다 짧은 조회 주소를 새로 발급한다.
	private static final Duration READ_URL_DURATION = Duration.ofMinutes(10);

	private final S3Client s3Client;
	private final S3Presigner s3Presigner;
	private final String bucket;

	public S3AfterSalesImageStorage(S3Client s3Client, S3Presigner s3Presigner,
			@Value("${spring.cloud.aws.s3.bucket}") String bucket) {
		this.s3Client = s3Client;
		this.s3Presigner = s3Presigner;
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

	@Override
	public String createReadUrl(String fileKey) {
		var request = GetObjectPresignRequest.builder().signatureDuration(READ_URL_DURATION)
			.getObjectRequest(object -> object.bucket(bucket).key(fileKey)).build();
		return s3Presigner.presignGetObject(request).url().toString();
	}
}
