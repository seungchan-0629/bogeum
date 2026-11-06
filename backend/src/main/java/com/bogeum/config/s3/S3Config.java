package com.bogeum.config.s3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

// spring.cloud.aws.s3.enabled=true일 때만 만든다. (test 프로필은 S3 설정이 없어 생성하지 않는다)
@Configuration
@ConditionalOnProperty(name = "spring.cloud.aws.s3.enabled", havingValue = "true")
public class S3Config {
	// .env 값은 OS 환경 변수가 아니라서 SDK가 자동으로 찾지 못한다. yml 값을 직접 넘긴다.
	@Value("${spring.cloud.aws.credentials.access-key}")
	private String accessKey;

	@Value("${spring.cloud.aws.credentials.secret-key}")
	private String secretKey;

	@Value("${spring.cloud.aws.region.static}")
	private String region;

	// S3 접근용 인증 정보
	@Bean
	public AwsBasicCredentials awsCredentials() {
		return AwsBasicCredentials.create(accessKey, secretKey);
	}

	// 파일 업로드·삭제
	@Bean
	public S3Client s3Client() {
		return S3Client.builder()
			.region(Region.of(region))
			.credentialsProvider(StaticCredentialsProvider.create(awsCredentials()))
			.build();
	}

	// Presigned URL(유효시간이 있는 임시 주소) 발급
	@Bean
	public S3Presigner s3Presigner() {
		return S3Presigner.builder()
			.region(Region.of(region))
			.credentialsProvider(StaticCredentialsProvider.create(awsCredentials()))
			.build();
	}
}
