package com.bogeum.domain.property;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/** 매물 사진을 매물 전용 S3 버킷에서 다룬다. */
@Component
@ConditionalOnProperty(name = "spring.cloud.aws.s3.enabled", havingValue = "true")
public class S3PropertyImageStorage implements PropertyImageStorage {
	// 비공개 버킷이라 조회마다 짧은 조회 주소를 새로 발급한다.
	private static final Duration READ_URL_DURATION = Duration.ofMinutes(10);

	private final S3Presigner s3Presigner;
	private final String bucket;

	public S3PropertyImageStorage(S3Presigner s3Presigner,
			@Value("${spring.cloud.aws.s3.property-bucket}") String bucket) {
		this.s3Presigner = s3Presigner;
		this.bucket = bucket;
	}

	@Override
	public String createReadUrl(String fileKey) {
		var request = GetObjectPresignRequest.builder().signatureDuration(READ_URL_DURATION)
			.getObjectRequest(object -> object.bucket(bucket).key(fileKey)).build();
		return s3Presigner.presignGetObject(request).url().toString();
	}
}
