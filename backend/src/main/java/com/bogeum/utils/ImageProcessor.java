package com.bogeum.utils;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.bogeum.common.exception.ImageException;

/**
 * 업로드 사진의 실제 형식·크기를 검사하고 크기를 제한한 JPEG로 변환한다. 파일 저장은 맡지 않는다.
 * AS 사진(AS_001)과 매물 사진(PROPERTY_010)이 같은 검사 기준을 쓴다. 설정 키는 처음 만든 AS 기준 이름(as-image)을 그대로 쓴다.
 */
@Component
public class ImageProcessor {
	private final long maxBytes;
	private final long maxPixels;
	private final int maxDimension;
	private final float quality;
	private final Set<String> formats;

	public ImageProcessor(@Value("${as-image.max-bytes:20971520}") long maxBytes,
			@Value("${as-image.max-pixels:40000000}") long maxPixels,
			@Value("${as-image.max-dimension:2048}") int maxDimension,
			@Value("${as-image.jpeg-quality:0.85}") float quality,
			@Value("${as-image.formats:JPEG,PNG}") Set<String> formats) {
		if (maxBytes <= 0 || maxPixels <= 0 || maxDimension <= 0 || quality <= 0 || quality > 1) {
			throw new IllegalArgumentException("이미지 설정값을 확인해 주세요.");
		}
		this.maxBytes = maxBytes;
		this.maxPixels = maxPixels;
		this.maxDimension = maxDimension;
		this.quality = quality;
		this.formats = formats;
	}

	/**
	 * 확장자나 요청 MIME 대신 실제 이미지 내용을 읽어 변환한다.
	 *
	 * @throws ImageException 빈 파일이거나 형식·용량·픽셀 제한을 벗어나거나 읽을 수 없는 경우
	 */
	public byte[] process(MultipartFile file) {
		if (file.isEmpty() || file.getSize() > maxBytes) {
			throw ImageException.invalidImage();
		}
		try (var source = file.getInputStream(); var input = ImageIO.createImageInputStream(source)) {
			var readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw ImageException.invalidImage();
			}
			var reader = readers.next();
			try {
				if (!formats.contains(reader.getFormatName().toUpperCase(Locale.ROOT))) {
					throw ImageException.invalidImage();
				}
				reader.setInput(input, true, true);
				// 전체 이미지를 디코딩하기 전에 픽셀 수를 제한해 메모리 사용을 억제한다.
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width <= 0 || height <= 0 || (long) width * height > maxPixels) {
					throw ImageException.invalidImage();
				}
				BufferedImage original = reader.read(0);
				double scale = Math.min(1.0, (double) maxDimension / Math.max(width, height));
				var resized = new BufferedImage(Math.max(1, (int) (width * scale)),
					Math.max(1, (int) (height * scale)), BufferedImage.TYPE_INT_RGB);
				var graphics = resized.createGraphics();
				try {
					// JPEG에는 투명 영역이 없어 PNG의 투명 부분을 흰색 배경에 합성한다.
					graphics.setColor(Color.WHITE);
					graphics.fillRect(0, 0, resized.getWidth(), resized.getHeight());
					graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
					graphics.drawImage(original, 0, 0, resized.getWidth(), resized.getHeight(), null);
				} finally {
					graphics.dispose();
					original.flush();
				}
				try (var bytes = new ByteArrayOutputStream(); var output = ImageIO.createImageOutputStream(bytes)) {
					var writer = ImageIO.getImageWritersByFormatName("JPEG").next();
					try {
						writer.setOutput(output);
						var params = writer.getDefaultWriteParam();
						params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
						params.setCompressionQuality(quality);
						// 원본 메타데이터를 넘기지 않고 변환한 픽셀만 새 JPEG에 기록한다.
						writer.write(null, new javax.imageio.IIOImage(resized, null, null), params);
						output.flush();
						return bytes.toByteArray();
					} finally {
						writer.dispose();
						resized.flush();
					}
				}
			} finally {
				reader.dispose();
			}
		} catch (IOException | IllegalArgumentException exception) {
			// 파일 내용과 디코더의 내부 오류는 응답에 노출하지 않는다.
			throw ImageException.invalidImage();
		}
	}
}
